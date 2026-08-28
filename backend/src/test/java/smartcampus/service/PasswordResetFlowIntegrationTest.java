package smartcampus.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import smartcampus.TestcontainersConfiguration;
import smartcampus.entity.Role;
import smartcampus.entity.User;
import smartcampus.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check of the OTP password-reset flow against the real Mailpit dev SMTP
 * sink (localhost:1025 / its REST API on :8025) and a real (Testcontainers) MySQL.
 *
 * <p>Security filters are disabled for this test ({@code addFilters = false}) because
 * this agent does not own {@code SecurityConfig} - the integrator still needs to add a
 * {@code permitAll} rule for {@code /api/auth/password-reset/**} before these
 * endpoints are reachable through the real filter chain. That wiring is out of scope
 * here; this test verifies the controller/service/repository/email behaviour itself.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class PasswordResetFlowIntegrationTest {

    private static final String MAILPIT_API = "http://localhost:8025/api/v1/messages";
    private static final Pattern OTP_PATTERN = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private String testEmail;

    @BeforeEach
    void createUser() {
        testEmail = "otp-test-" + System.nanoTime() + "@example.com";
        User user = User.builder()
                .email(testEmail)
                .password(passwordEncoder.encode("OriginalPass123"))
                .fullName("OTP Test User")
                .role(Role.STUDENT)
                .build();
        userRepository.save(user);
    }

    @Test
    void requestReset_returnsIdenticalResponseForRealAndUnknownEmail() throws Exception {
        String realBody = mockMvc
                .perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + testEmail + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String unknownBody = mockMvc
                .perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"definitely-not-registered-" + System.nanoTime()
                                + "@example.com\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(realBody).isEqualTo(unknownBody);
    }

    @Test
    void fullFlow_requestThenVerifyThenReset_changesThePassword() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + testEmail + "\"}"))
                .andExpect(status().isOk());

        String otp = fetchOtpFromMailpit(testEmail);
        assertThat(otp).matches("\\d{6}");

        mockMvc.perform(post("/api/auth/password-reset/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + testEmail + "\",\"otp\":\"" + otp + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Verification code is valid."));

        mockMvc.perform(post("/api/auth/password-reset/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + testEmail + "\",\"otp\":\"" + otp
                                + "\",\"newPassword\":\"BrandNewPass456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password has been reset successfully."));

        User reloaded = userRepository.findByEmail(testEmail).orElseThrow();
        assertThat(passwordEncoder.matches("BrandNewPass456", reloaded.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("OriginalPass123", reloaded.getPassword())).isFalse();

        // Single-use: the same OTP cannot be used again.
        mockMvc.perform(post("/api/auth/password-reset/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + testEmail + "\",\"otp\":\"" + otp
                                + "\",\"newPassword\":\"AnotherPass789\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired verification code."));
    }

    @Test
    void wrongOtp_isRejected_andExceedingAttemptCapLocksOutTheCorrectOtpToo() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + testEmail + "\"}"))
                .andExpect(status().isOk());

        String correctOtp = fetchOtpFromMailpit(testEmail);
        String wrongOtp = correctOtp.equals("000000") ? "111111" : "000000";

        // Default cap is 5 (smartcampus.password-reset.max-attempts) unless the
        // integrator changes it; exhaust it with wrong guesses.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/password-reset/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + testEmail + "\",\"otp\":\"" + wrongOtp + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invalid or expired verification code."));
        }

        // Cap now reached - even the correct OTP must be rejected.
        mockMvc.perform(post("/api/auth/password-reset/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + testEmail + "\",\"otp\":\"" + correctOtp + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired verification code."));
    }

    /** Polls Mailpit's REST API for the newest message to {@code recipient} and extracts its OTP. */
    private String fetchOtpFromMailpit(String recipient) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(MAILPIT_API + "?limit=25"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            for (JsonNode msg : root.get("messages")) {
                String to = msg.at("/To/0/Address").asText("");
                if (recipient.equalsIgnoreCase(to)) {
                    String messageId = msg.get("ID").asText();
                    return fetchOtpFromMessage(messageId);
                }
            }
            Thread.sleep(300);
        }
        throw new AssertionError("No Mailpit message arrived for " + recipient + " within timeout");
    }

    private String fetchOtpFromMessage(String messageId) throws Exception {
        // Mailpit's single-message endpoint is the singular "/api/v1/message/{id}" -
        // NOT "/api/v1/messages/{id}" (that 404s with a plain-text "File not found"
        // body, which is not valid JSON). Confirmed against the real running Mailpit
        // container's REST API.
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:8025/api/v1/message/" + messageId))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(response.body());
        String text = root.path("Text").asText("");
        Matcher matcher = OTP_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new AssertionError("No 6-digit OTP found in Mailpit message body: " + text);
    }
}
