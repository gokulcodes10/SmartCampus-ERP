package smartcampus.security61;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import tools.jackson.databind.ObjectMapper;

/**
 * §61 item 8 — API key protection.
 *
 * <p>{@code GET /api/ai/models} must be ADMIN-only (checked here as an actual live
 * request, not read off {@code SecurityConfig}); and no response anywhere touched by
 * this class - the AI status endpoint, and the models endpoint itself for whichever
 * caller reaches it - may ever contain the raw {@code AI_API_KEY}/{@code
 * JUDGE0_API_KEY} environment values in its body. The comparison values are read from
 * the process environment (never printed, logged, or written into an assertion
 * message) and the sub-check is skipped with a stated reason when a key is not present
 * in this test process's environment, exactly as it is not by default here (see the
 * {@code AGENT_CONTEXT.md} Addendum 3 note that {@code .env} is not auto-loaded by
 * Spring Boot / Maven).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ApiKeyProtectionTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${smartcampus.ai.api-key:}")
    private String configuredAiApiKey;

    @Value("${smartcampus.judge0.api-key:}")
    private String configuredJudge0ApiKey;

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private int next() {
        return COUNTER.incrementAndGet();
    }

    private String uniqueEmail(String prefix) {
        return prefix + next() + "-" + System.nanoTime() + "@example.com";
    }

    private String tokenFor(Role role) throws Exception {
        String email = uniqueEmail("apikey-" + role.name().toLowerCase());
        userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode("Pass1234!"))
                .fullName("API Key Check " + role.name())
                .role(role)
                .build());
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Pass1234!\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    @Test
    void aiModels_isAdminOnly_studentAndFacultyDenied() throws Exception {
        String studentToken = tokenFor(Role.STUDENT);
        String facultyToken = tokenFor(Role.FACULTY);

        mockMvc.perform(get("/api/ai/models").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ai/models").header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void aiModels_adminReachesTheServiceLayer_neverDenied403() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);

        // Whatever the outcome (200 with a real list, or a clean "AI not configured"
        // failure when no AI_API_KEY is present in this test environment - see
        // GroqAIService.isConfigured()), the point of this assertion is that an ADMIN
        // is never blocked by the role gate the way STUDENT/FACULTY are above.
        MvcResult result = mockMvc.perform(get("/api/ai/models").header("Authorization", "Bearer " + adminToken))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(403);
        assertBodyNeverLeaksASecret(result.getResponse().getContentAsString());
    }

    @Test
    void aiStatus_anyAuthenticatedCaller_neverLeaksTheApiKeyInTheResponseBody() throws Exception {
        String studentToken = tokenFor(Role.STUDENT);

        MvcResult result = mockMvc.perform(get("/api/ai/status").header("Authorization", "Bearer " + studentToken))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertBodyNeverLeaksASecret(result.getResponse().getContentAsString());
    }

    private void assertBodyNeverLeaksASecret(String responseBody) {
        String lower = responseBody.toLowerCase();
        assertThat(lower).doesNotContain("\"apikey\"");
        assertThat(lower).doesNotContain("\"api_key\"");
        assertThat(lower).doesNotContain("\"api-key\"");

        if (configuredAiApiKey != null && !configuredAiApiKey.isBlank()) {
            assertThat(responseBody).doesNotContain(configuredAiApiKey);
        }
        if (configuredJudge0ApiKey != null && !configuredJudge0ApiKey.isBlank()) {
            assertThat(responseBody).doesNotContain(configuredJudge0ApiKey);
        }
    }
}
