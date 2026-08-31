package smartcampus.coding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
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
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.User;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 7 (Coding) verification checkpoint, driven entirely over real HTTP with real
 * JWTs against the real MySQL container (see {@link TestcontainersConfiguration}), not
 * against services directly. This is deliberately independent of {@code
 * CodingSubmissionServiceTest} (Mockito-level) and {@code CodingSchemaValidationTest}
 * (repository-level): it proves the same guarantees survive the full HTTP stack —
 * {@code SecurityConfig} route matchers, controller wiring, Jackson serialization — not
 * just the service method in isolation.
 *
 * <p>Judge0 has no reachable endpoint in this environment either (clarification G10),
 * so {@link #submit_withJudge0Unreachable_returnsHonestInternalErrorNeverAFabricatedVerdict}
 * is exercised against the real {@code Judge0Service} bean, not a stub — the same honest
 * failure this checkpoint is required to demonstrate on the live application.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CodingCheckpointTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static int next() {
        return COUNTER.incrementAndGet();
    }

    private String uniqueEmail(String prefix) {
        return prefix + next() + "-" + System.nanoTime() + "@example.com";
    }

    private String persistAndLogin(Role role) throws Exception {
        String email = uniqueEmail(role.name().toLowerCase());
        String password = "CheckpointPass1!";
        User user =
                userRepository.save(
                        User.builder()
                                .email(email)
                                .password(passwordEncoder.encode(password))
                                .fullName("Checkpoint " + role.name())
                                .role(role)
                                .build());
        if (role == Role.STUDENT) {
            // PENDING, not ACTIVE — no department/course/register-number fixture needed,
            // and CodingSubmissionService/CodingContestService explicitly allow PENDING
            // students to submit and register (only INACTIVE is refused).
            studentRepository.save(Student.builder().user(user).status(StudentStatus.PENDING).build());
        }
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        String responseBody =
                mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(responseBody).get("token").asText();
    }

    private JsonNode postJson(String url, String token, String body, org.hamcrest.Matcher<Integer> statusMatcher)
            throws Exception {
        String responseBody =
                mockMvc.perform(
                                post(url)
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().is(statusMatcher))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return responseBody == null || responseBody.isBlank() ? null : objectMapper.readTree(responseBody);
    }

    // ------------------------------------------------------------------
    // Hidden test cases must never reach a non-admin caller — problem detail, list, or
    // submission response.
    // ------------------------------------------------------------------

    @Test
    void hiddenTestCases_neverLeakToAStudent_viaProblemDetailListOrSubmission() throws Exception {
        String adminToken = persistAndLogin(Role.ADMIN);
        String studentToken = persistAndLogin(Role.STUDENT);

        String slug = "checkpoint-problem-" + next() + "-" + System.nanoTime();
        String createBody =
                "{\"slug\":\"" + slug + "\",\"title\":\"Checkpoint Sum\",\"description\":\"Add.\","
                        + "\"difficulty\":\"EASY\",\"timeLimitMs\":2000,\"memoryLimitKb\":262144,"
                        + "\"published\":false}";
        JsonNode created =
                postJson("/api/problems", adminToken, createBody, org.hamcrest.Matchers.is(201));
        long problemId = created.get("id").asLong();

        postJson(
                "/api/problems/" + problemId + "/test-cases",
                adminToken,
                "{\"ordinal\":1,\"input\":\"1 2\",\"expectedOutput\":\"3\",\"isSample\":true,\"weight\":1}",
                org.hamcrest.Matchers.is(201));
        postJson(
                "/api/problems/" + problemId + "/test-cases",
                adminToken,
                "{\"ordinal\":2,\"input\":\"9 9\",\"expectedOutput\":\"CHECKPOINT_SECRET_18\",\"isSample\":false,"
                        + "\"weight\":2}",
                org.hamcrest.Matchers.is(201));

        // Unpublished: invisible to the student — 404, never a 403 (R8).
        mockMvc.perform(get("/api/problems/" + problemId).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());

        // Publish it.
        String updateBody =
                "{\"slug\":\"" + slug + "\",\"title\":\"Checkpoint Sum\",\"description\":\"Add.\","
                        + "\"difficulty\":\"EASY\",\"timeLimitMs\":2000,\"memoryLimitKb\":262144,"
                        + "\"published\":true}";
        mockMvc.perform(
                        put("/api/problems/" + problemId)
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateBody))
                .andExpect(status().isOk());

        // Detail response, as a student: the hidden case's expected output must not appear
        // anywhere in the body.
        String detailBody =
                mockMvc.perform(get("/api/problems/" + problemId).header("Authorization", "Bearer " + studentToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(detailBody).doesNotContain("CHECKPOINT_SECRET_18");
        JsonNode detail = objectMapper.readTree(detailBody);
        assertThat(detail.get("sampleTestCases")).hasSize(1);
        assertThat(detail.get("hiddenTestCaseCount").asInt()).isEqualTo(1);

        // List response, as a student.
        String listBody =
                mockMvc.perform(
                                get("/api/problems?search=" + slug)
                                        .header("Authorization", "Bearer " + studentToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(listBody).doesNotContain("CHECKPOINT_SECRET_18");

        // Test-case admin route: forbidden for a student, even after publishing — G3, and
        // the matcher-ordering trap this phase specifically calls out.
        mockMvc.perform(
                        get("/api/problems/" + problemId + "/test-cases")
                                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        // Admin CAN see it via the dedicated route.
        String adminTestCases =
                mockMvc.perform(
                                get("/api/problems/" + problemId + "/test-cases")
                                        .header("Authorization", "Bearer " + adminToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(adminTestCases).contains("CHECKPOINT_SECRET_18");

        // A real graded submission, then re-read via GET /api/coding/submissions/{id}: even
        // though Judge0 is unreachable and no per-case rows exist yet, prove the shape that
        // would carry hidden output (testResults[].expectedOutput/actualOutput) never does.
        String submitBody =
                "{\"problemId\":" + problemId + ",\"language\":\"JAVA\",\"sourceCode\":\"x\",\"contestId\":null}";
        JsonNode submission =
                postJson("/api/coding/submissions", studentToken, submitBody, org.hamcrest.Matchers.is(201));
        long submissionId = submission.get("id").asLong();
        String submissionBody =
                mockMvc.perform(
                                get("/api/coding/submissions/" + submissionId)
                                        .header("Authorization", "Bearer " + studentToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(submissionBody).doesNotContain("CHECKPOINT_SECRET_18");
    }

    // ------------------------------------------------------------------
    // No fabricated verdict when Judge0 is unreachable.
    // ------------------------------------------------------------------

    @Test
    void submit_withJudge0Unreachable_returnsHonestInternalErrorNeverAFabricatedVerdict() throws Exception {
        String adminToken = persistAndLogin(Role.ADMIN);
        String studentToken = persistAndLogin(Role.STUDENT);

        String slug = "checkpoint-honest-" + next() + "-" + System.nanoTime();
        JsonNode created =
                postJson(
                        "/api/problems",
                        adminToken,
                        "{\"slug\":\"" + slug + "\",\"title\":\"Honesty Check\",\"description\":\"x\","
                                + "\"difficulty\":\"EASY\",\"timeLimitMs\":2000,\"memoryLimitKb\":262144,"
                                + "\"published\":true}",
                        org.hamcrest.Matchers.is(201));
        long problemId = created.get("id").asLong();
        postJson(
                "/api/problems/" + problemId + "/test-cases",
                adminToken,
                "{\"ordinal\":1,\"input\":\"\",\"expectedOutput\":\"ok\",\"isSample\":true,\"weight\":1}",
                org.hamcrest.Matchers.is(201));

        JsonNode submission =
                postJson(
                        "/api/coding/submissions",
                        studentToken,
                        "{\"problemId\":" + problemId + ",\"language\":\"JAVA\",\"sourceCode\":\"x\","
                                + "\"contestId\":null}",
                        org.hamcrest.Matchers.is(201));

        // The honest, non-fabricated outcome: real INTERNAL_ERROR with a real message, never
        // ACCEPTED — this is what chk_coding_submissions_accepted_is_earned exists to make
        // impossible even if the application layer had a bug.
        assertThat(submission.get("status").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(submission.get("errorMessage").asText()).isNotBlank();
        assertThat(submission.get("errorMessage").asText()).doesNotContain("ACCEPTED");
    }

    // ------------------------------------------------------------------
    // Contest registration: once, duplicate rejected, unregistered submission rejected.
    // ------------------------------------------------------------------

    @Test
    void contestRegistration_onceOnly_andSubmissionRequiresRegistration() throws Exception {
        String adminToken = persistAndLogin(Role.ADMIN);
        String studentToken = persistAndLogin(Role.STUDENT);

        long problemId =
                postJson(
                                "/api/problems",
                                adminToken,
                                "{\"slug\":\"checkpoint-contest-problem-" + next() + "-" + System.nanoTime()
                                        + "\",\"title\":\"Contest Problem\",\"description\":\"x\","
                                        + "\"difficulty\":\"EASY\",\"timeLimitMs\":2000,\"memoryLimitKb\":262144,"
                                        + "\"published\":true}",
                                org.hamcrest.Matchers.is(201))
                        .get("id")
                        .asLong();
        postJson(
                "/api/problems/" + problemId + "/test-cases",
                adminToken,
                "{\"ordinal\":1,\"input\":\"\",\"expectedOutput\":\"ok\",\"isSample\":true,\"weight\":1}",
                org.hamcrest.Matchers.is(201));

        LocalDateTime start = LocalDateTime.now().minusMinutes(1);
        LocalDateTime end = LocalDateTime.now().plusHours(2);
        JsonNode contest =
                postJson(
                        "/api/contests",
                        adminToken,
                        "{\"slug\":\"checkpoint-contest-" + next() + "-" + System.nanoTime()
                                + "\",\"title\":\"Checkpoint Contest\",\"description\":\"x\","
                                + "\"startTime\":\"" + start.format(TS) + "\",\"endTime\":\"" + end.format(TS)
                                + "\",\"status\":\"PUBLISHED\",\"penaltyMinutesPerWrongAttempt\":10}",
                        org.hamcrest.Matchers.is(201));
        long contestId = contest.get("id").asLong();

        postJson(
                "/api/contests/" + contestId + "/problems",
                adminToken,
                "{\"problemId\":" + problemId + ",\"ordinal\":1,\"points\":100}",
                org.hamcrest.Matchers.is(201));

        // Submitting to the contest before registering is rejected with a clear 400.
        mockMvc.perform(
                        post("/api/coding/submissions")
                                .header("Authorization", "Bearer " + studentToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"problemId\":" + problemId + ",\"language\":\"JAVA\",\"sourceCode\":\"x\","
                                                + "\"contestId\":" + contestId + "}"))
                .andExpect(status().isBadRequest());

        // Register once — succeeds.
        postJson("/api/contests/" + contestId + "/register", studentToken, "", org.hamcrest.Matchers.is(201));

        // Register again — rejected as a duplicate, not silently accepted.
        mockMvc.perform(
                        post("/api/contests/" + contestId + "/register")
                                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isConflict());

        // Now the contest submission is accepted (still an honest INTERNAL_ERROR verdict,
        // since Judge0 is unreachable — but the row is created and the registration gate
        // itself is proven).
        JsonNode contestSubmission =
                postJson(
                        "/api/coding/submissions",
                        studentToken,
                        "{\"problemId\":" + problemId + ",\"language\":\"JAVA\",\"sourceCode\":\"x\","
                                + "\"contestId\":" + contestId + "}",
                        org.hamcrest.Matchers.is(201));
        assertThat(contestSubmission.get("contestId").asLong()).isEqualTo(contestId);

        // A judge-outage submission must never move the ICPC penalty clock.
        String meBody =
                mockMvc.perform(
                                get("/api/contests/" + contestId + "/me")
                                        .header("Authorization", "Bearer " + studentToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode me = objectMapper.readTree(meBody);
        assertThat(me.get("penaltySeconds").asInt()).isZero();
        assertThat(me.get("problemsSolved").asInt()).isZero();
    }

    // ------------------------------------------------------------------
    // Route-level authorization sanity: a non-admin cannot author problems or contests.
    // ------------------------------------------------------------------

    @Test
    void nonAdminWrites_toProblemsAndContests_areRejected() throws Exception {
        String studentToken = persistAndLogin(Role.STUDENT);

        mockMvc.perform(
                        post("/api/problems")
                                .header("Authorization", "Bearer " + studentToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"slug\":\"x-" + next() + "\",\"title\":\"x\",\"description\":\"x\","
                                                + "\"difficulty\":\"EASY\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        post("/api/contests")
                                .header("Authorization", "Bearer " + studentToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"slug\":\"x-" + next() + "\",\"title\":\"x\","
                                                + "\"startTime\":\"2027-01-01T00:00:00\","
                                                + "\"endTime\":\"2027-01-02T00:00:00\",\"status\":\"DRAFT\","
                                                + "\"penaltyMinutesPerWrongAttempt\":10}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/problems/999999").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }
}
