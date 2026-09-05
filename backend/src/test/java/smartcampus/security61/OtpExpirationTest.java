package smartcampus.security61;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import smartcampus.TestcontainersConfiguration;
import smartcampus.entity.PasswordResetToken;
import smartcampus.entity.Role;
import smartcampus.entity.User;
import smartcampus.repository.PasswordResetTokenRepository;
import smartcampus.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * §61 item 10 — OTP expiration.
 *
 * <p>Three sub-requirements against the real OTP flow, driven over real Mailpit mail
 * (the same dev SMTP sink {@code PasswordResetFlowIntegrationTest} uses):
 *
 * <ol>
 *   <li>a token older than {@code smartcampus.password-reset.otp-expiration-minutes}
 *       is rejected. Rather than sleeping the real 15 minutes, the persisted token row
 *       is read back through {@link PasswordResetTokenRepository} and its {@code
 *       expiresAt} is pushed into the past directly - the same lookup the service uses
 *       ({@code findFirstBy..ExpiresAtAfter}) then genuinely cannot find it, which is
 *       the real mechanism, not a simulation of it.
 *   <li>a used OTP cannot be reused.
 *   <li>THE PHASE 2 REGRESSION, explicitly: five wrong guesses followed by the correct
 *       code must FAIL. PROJECT_PLAN.md documents that a brute-force cap that increments
 *       and saves {@code attemptCount} and then throws from the same {@code
 *       @Transactional} method was silently rolled back by Spring's default
 *       rollback-on-unchecked rule, so five wrong guesses never actually accumulated -
 *       the sixth (correct) guess still succeeded. This test proves that specific
 *       failure mode is closed, not just that a generic cap "exists".
 * </ol>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class OtpExpirationTest {

    private static final String MAILPIT_API = "http://localhost:8025/api/v1/messages";
    private static final Pattern OTP_PATTERN = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }

    private User persistUser(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode("OriginalPass123"))
                .fullName("OTP Expiry Check")
                .role(Role.STUDENT)
                .build());
    }

    private void requestReset(String email) throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // 1) A token older than the configured expiry is rejected
    // ------------------------------------------------------------------

    @Test
    void otpOlderThanTheConfiguredExpiryWindow_isRejected() throws Exception {
        String email = uniqueEmail("otp-expiry");
        User user = persistUser(email);
        requestReset(email);
        String otp = fetchOtpFromMailpit(email);

        PasswordResetToken token = passwordResetTokenRepository
                .findFirstByUser_IdAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        user.getId(), LocalDateTime.now())
                .orElseThrow(() -> new AssertionError("No active OTP token row found for " + email));

        // Push expiry into the past - the exact lookup PasswordResetService uses
        // (ExpiresAtAfter now) then genuinely cannot find this row anymore.
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        passwordResetTokenRepository.saveAndFlush(token);

        mockMvc.perform(post("/api/auth/password-reset/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"otp\":\"" + otp + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired verification code."));
    }

    // ------------------------------------------------------------------
    // 2) A used OTP cannot be reused
    // ------------------------------------------------------------------

    @Test
    void aUsedOtp_cannotBeUsedASecondTime() throws Exception {
        String email = uniqueEmail("otp-reuse");
        persistUser(email);
        requestReset(email);
        String otp = fetchOtpFromMailpit(email);

        mockMvc.perform(post("/api/auth/password-reset/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"otp\":\"" + otp
                                + "\",\"newPassword\":\"BrandNewPass456\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/password-reset/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"otp\":\"" + otp + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired verification code."));
    }

    // ------------------------------------------------------------------
    // 3) THE PHASE 2 REGRESSION: five wrong guesses + the correct code afterward = FAIL
    // ------------------------------------------------------------------

    @Test
    void fiveWrongGuesses_thenTheCorrectCode_stillFails_provingTheAttemptCapReallyEngages() throws Exception {
        String email = uniqueEmail("otp-cap-regression");
        User user = persistUser(email);
        requestReset(email);
        String correctOtp = fetchOtpFromMailpit(email);
        String wrongOtp = correctOtp.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/password-reset/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"otp\":\"" + wrongOtp + "\"}"))
                    .andExpect(status().isBadRequest());
        }

        // The regression this guards: if the attemptCount save were rolled back by the
        // same @Transactional-throw bug PROJECT_PLAN.md documents from Phase 2, the
        // cap would never have engaged and this next call would succeed (200) instead
        // of correctly failing (400).
        PasswordResetToken tokenAfterFiveWrongGuesses = passwordResetTokenRepository
                .findFirstByUser_IdAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        user.getId(), LocalDateTime.now())
                .orElse(null);
        if (tokenAfterFiveWrongGuesses != null) {
            org.assertj.core.api.Assertions.assertThat(tokenAfterFiveWrongGuesses.getAttemptCount())
                    .as("attemptCount must have actually persisted across 5 wrong guesses, not been rolled back")
                    .isGreaterThanOrEqualTo(5);
        }

        mockMvc.perform(post("/api/auth/password-reset/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"otp\":\"" + correctOtp + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired verification code."));
    }

    private String fetchOtpFromMailpit(String recipient) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(MAILPIT_API + "?limit=25"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:8025/api/v1/message/" + messageId))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(response.body());
        String text = root.path("Text").asText("");
        Matcher matcher = OTP_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new AssertionError("No 6-digit OTP found in Mailpit message body: " + text);
    }
}
