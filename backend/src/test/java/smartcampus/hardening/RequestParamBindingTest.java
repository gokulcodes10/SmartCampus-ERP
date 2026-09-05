package smartcampus.hardening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import smartcampus.TestcontainersConfiguration;
import smartcampus.entity.Role;
import smartcampus.entity.User;
import smartcampus.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * §61 input validation for {@code @RequestParam} binding: a missing required query
 * parameter ({@code MissingServletRequestParameterException}) or a value of the wrong
 * type ({@code MethodArgumentTypeMismatchException}) must produce the §47 400
 * envelope, never fall through to the catch-all 500. These exact requests returned
 * 500 in the Phase 12 §75 audit; {@code InputValidationTest} only exercises
 * {@code @RequestBody} validation, which is why nothing caught it.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RequestParamBindingTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String RAW_PASSWORD = "CheckpointPass1!";

    private String adminToken() throws Exception {
        String email = "rpb" + SEQUENCE.incrementAndGet() + "@example.com";
        userRepository.save(
                User.builder()
                        .email(email)
                        .password(passwordEncoder.encode(RAW_PASSWORD))
                        .fullName("RPB Admin")
                        .role(Role.ADMIN)
                        .build());
        String body =
                mockMvc.perform(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"email\":\"" + email + "\",\"password\":\"" + RAW_PASSWORD + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(body).get("token").asString();
    }

    private JsonNode assertEnvelope400(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode error = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(error.get("status").asInt()).isEqualTo(400);
        assertThat(error.get("error").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(error.get("message").asString()).isNotBlank();
        return error;
    }

    @Test
    void missingRequiredParameter_is400NotServerError() throws Exception {
        String token = adminToken();

        // /api/attendance/roster without its required `date` (and others).
        JsonNode rosterError =
                assertEnvelope400(
                        mockMvc.perform(
                                        get("/api/attendance/roster")
                                                .header("Authorization", "Bearer " + token))
                                .andReturn());
        assertThat(rosterError.get("message").asString()).contains("subjectId");

        // /api/attendance/class-summary without any of its required parameters.
        assertEnvelope400(
                mockMvc.perform(
                                get("/api/attendance/class-summary")
                                        .header("Authorization", "Bearer " + token))
                        .andReturn());

        // /api/marks/entry-sheet without its required `examId`.
        assertEnvelope400(
                mockMvc.perform(
                                get("/api/marks/entry-sheet")
                                        .header("Authorization", "Bearer " + token))
                        .andReturn());
    }

    @Test
    void typeMismatchedParameter_is400NotServerError() throws Exception {
        String token = adminToken();

        // `subjectId=abc` where a numeric id is expected.
        JsonNode error =
                assertEnvelope400(
                        mockMvc.perform(
                                        get("/api/attendance/roster")
                                                .header("Authorization", "Bearer " + token)
                                                .param("subjectId", "abc")
                                                .param("academicYear", "2025-2026")
                                                .param("semester", "1")
                                                .param("section", "A")
                                                .param("date", "2026-08-31")
                                                .param("period", "1"))
                                .andReturn());
        assertThat(error.get("message").asString()).contains("subjectId");

        // A malformed date is a type mismatch too, not a server fault.
        assertEnvelope400(
                mockMvc.perform(
                                get("/api/attendance/roster")
                                        .header("Authorization", "Bearer " + token)
                                        .param("subjectId", "1")
                                        .param("academicYear", "2025-2026")
                                        .param("semester", "1")
                                        .param("section", "A")
                                        .param("date", "not-a-date")
                                        .param("period", "1"))
                        .andReturn());
    }
}
