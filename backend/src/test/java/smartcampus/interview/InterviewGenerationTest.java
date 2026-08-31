package smartcampus.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import smartcampus.TestcontainersConfiguration;
import smartcampus.entity.AIRequestLog;
import smartcampus.entity.AIRequestOutcome;
import smartcampus.entity.Course;
import smartcampus.entity.Department;
import smartcampus.entity.InterviewQuestion;
import smartcampus.entity.InterviewQuestionSource;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.User;
import smartcampus.exception.AIUnavailableException;
import smartcampus.repository.AIRequestLogRepository;
import smartcampus.repository.CourseRepository;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.InterviewQuestionRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.UserRepository;
import smartcampus.service.AIChatMessage;
import smartcampus.service.AICompletion;
import smartcampus.service.AICompletionRequest;
import smartcampus.service.AIModelInfo;
import smartcampus.service.AIService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 10 (Interview) verification for AI-generated interview practice questions: real
 * HTTP, real JWTs, real MySQL (Testcontainers, real Flyway V10 migration) and the REAL
 * orchestration/controller layer this task built — only the external provider is
 * replaced, by a {@link StubAIService} bean substituted for {@code GroqAIService} via
 * {@code @Primary} in {@link StubAIServiceConfig}, exactly the pattern {@code
 * AIAssistantFlowTest} uses.
 *
 * <p>Every fixture code/email is derived from a per-JVM {@link AtomicInteger}, never
 * {@code System.nanoTime()} — PROJECT_PLAN.md documents a real duplicate-key flake
 * elsewhere caused by exactly that pattern.
 */
@Import({TestcontainersConfiguration.class, InterviewGenerationTest.StubAIServiceConfig.class})
@SpringBootTest
@AutoConfigureMockMvc
class InterviewGenerationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private InterviewQuestionRepository interviewQuestionRepository;
    @Autowired private AIRequestLogRepository aiRequestLogRepository;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String RAW_PASSWORD = "CheckpointPass1!";
    // "IGT" tags every fixture code/email this class creates, distinguishing them from
    // any sibling checkpoint test class sharing the cached Spring context / MySQL
    // instance.
    private static final String PREFIX = "IGT";

    private static String tag() {
        return PREFIX + SEQUENCE.incrementAndGet();
    }

    // ------------------------------------------------------------------
    // Stub AIService — replaces GroqAIService for this whole test class.
    // ------------------------------------------------------------------

    @TestConfiguration
    static class StubAIServiceConfig {
        @Bean
        @Primary
        AIService stubAIService() {
            return new StubAIService();
        }
    }

    /**
     * Records every request it was handed and returns a canned, real-looking
     * completion — never a network call. {@link #FAIL_NEXT_CALL} makes exactly the next
     * {@link #complete} call throw {@link AIUnavailableException}, proving the
     * PROVIDER_ERROR / no-partial-persistence path. {@link #CONFIGURED} lets a test
     * simulate an unconfigured provider (NOT_CONFIGURED path). {@link #NEXT_JSON_CONTENT}
     * lets a test control the JSON body returned to interview-question generation.
     */
    static final class StubAIService implements AIService {
        static final AtomicBoolean CONFIGURED = new AtomicBoolean(true);
        static final AtomicBoolean FAIL_NEXT_CALL = new AtomicBoolean(false);
        static final AtomicReference<String> NEXT_JSON_CONTENT = new AtomicReference<>();
        static final List<List<AIChatMessage>> CAPTURED_REQUESTS =
                Collections.synchronizedList(new ArrayList<>());

        @Override
        public boolean isConfigured() {
            return CONFIGURED.get();
        }

        @Override
        public String resolveModel() {
            return "stub-model-1";
        }

        @Override
        public List<AIModelInfo> listModels() {
            return List.of(new AIModelInfo("stub-model-1", "stub-owner", 8192));
        }

        @Override
        public AICompletion complete(AICompletionRequest request) {
            CAPTURED_REQUESTS.add(request.messages());
            if (FAIL_NEXT_CALL.getAndSet(false)) {
                throw new AIUnavailableException("Stubbed provider failure for a test.");
            }
            String content = NEXT_JSON_CONTENT.get();
            return new AICompletion(
                    content != null ? content : "{\"questions\":[]}", "stub-model-1", 12, 34, 46, 7L);
        }
    }

    private static final String VALID_QUESTIONS_JSON =
            "{\"questions\":["
                    + "{\"question\":\"Explain the CAP theorem.\",\"answer\":\"Consistency, Availability, "
                    + "Partition tolerance - pick two.\",\"explanation\":\"Distributed systems trade-off.\","
                    + "\"tags\":\"distributed,systems\"},"
                    + "{\"question\":\"What is a hash map?\",\"answer\":\"A key-value data structure.\","
                    + "\"explanation\":\"Average O(1) lookup.\",\"tags\":\"data-structures\"},"
                    + "{\"question\":\"\",\"answer\":\"blank question, must be skipped\",\"explanation\":null,"
                    + "\"tags\":null}"
                    + "]}";

    // ------------------------------------------------------------------
    // Fixture builders
    // ------------------------------------------------------------------

    private Department persistDepartment() {
        String t = tag();
        return departmentRepository.save(Department.builder().code(t + "D").name(t + " Dept").build());
    }

    private Course persistCourse(Department department) {
        String t = tag();
        return courseRepository.save(
                Course.builder().code(t + "C").name(t + " Course").department(department).build());
    }

    private User persistUser(Role role) {
        String t = tag();
        return userRepository.save(
                User.builder()
                        .email(t.toLowerCase() + "@example.com")
                        .password(passwordEncoder.encode(RAW_PASSWORD))
                        .fullName(t + " " + role.name())
                        .role(role)
                        .build());
    }

    private Student persistActiveStudent() {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        String t = tag();
        User user = persistUser(Role.STUDENT);
        return studentRepository.save(
                Student.builder()
                        .user(user)
                        .registerNumber(t + "REG")
                        .department(department)
                        .course(course)
                        .currentSemester(1)
                        .section("A")
                        .admissionYear(2024)
                        .status(StudentStatus.ACTIVE)
                        .build());
    }

    private String login(String email, String password) throws Exception {
        String body =
                mockMvc.perform(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(body).get("token").asString();
    }

    private String loginAsStudent(Student student) throws Exception {
        return login(student.getUser().getEmail(), RAW_PASSWORD);
    }

    private String loginAsUser(User user) throws Exception {
        return login(user.getEmail(), RAW_PASSWORD);
    }

    private long logCountForUser(Long userId) {
        return aiRequestLogRepository.countByUserIdAndCreatedAtGreaterThanEqual(
                userId, LocalDateTime.now().minusDays(1));
    }

    private List<Long> existingLogIds() {
        return aiRequestLogRepository.findAll().stream().map(AIRequestLog::getId).toList();
    }

    private AIRequestLog theOneNewLog(List<Long> logIdsBefore) {
        List<AIRequestLog> newLogs =
                aiRequestLogRepository.findAll().stream().filter(l -> !logIdsBefore.contains(l.getId())).toList();
        assertThat(newLogs).hasSize(1);
        return newLogs.get(0);
    }

    // ------------------------------------------------------------------
    // (1) Valid generation persists N private AI_GENERATED rows for the caller; a
    // second student's bank browse does not see them.
    // ------------------------------------------------------------------

    @Test
    void validGeneration_persistsPrivateAiQuestions_notVisibleToASecondStudent() throws Exception {
        Student student = persistActiveStudent();
        String token = loginAsStudent(student);

        StubAIService.NEXT_JSON_CONTENT.set(VALID_QUESTIONS_JSON);

        String requestBody =
                "{\"category\":\"TECHNICAL\",\"difficulty\":\"MEDIUM\",\"topic\":\"Distributed systems\","
                        + "\"companyName\":null,\"count\":5}";
        String responseBody =
                mockMvc.perform(
                                post("/api/interview-questions/generate")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(requestBody))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);

        // The blank-question entry was skipped, so only 2 of the 3 JSON entries persist.
        assertThat(json.get("count").asInt()).isEqualTo(2);
        assertThat(json.get("model").asString()).isEqualTo("stub-model-1");
        JsonNode questions = json.get("questions");
        assertThat(questions).hasSize(2);
        for (JsonNode q : questions) {
            assertThat(q.get("source").asString()).isEqualTo("AI_GENERATED");
            assertThat(q.get("model").asString()).isEqualTo("stub-model-1");
            assertThat(q.get("mine").asBoolean()).isTrue();
            assertThat(q.get("ownerStudentId").asLong()).isEqualTo(student.getId());
        }

        List<InterviewQuestion> persisted =
                interviewQuestionRepository.findAll().stream()
                        .filter(q -> q.getSource() == InterviewQuestionSource.AI_GENERATED)
                        .filter(q -> q.getOwnerStudent() != null && q.getOwnerStudent().getId().equals(student.getId()))
                        .toList();
        assertThat(persisted).hasSize(2);
        assertThat(persisted).allMatch(q -> q.getModel() != null && !q.getModel().isBlank());
        assertThat(persisted).allMatch(q -> q.getOwnerStudent() != null);

        // A second student's bank browse must not see these private rows.
        Student intruder = persistActiveStudent();
        String intruderToken = loginAsStudent(intruder);
        String bankResponse =
                mockMvc.perform(
                                get("/api/interview-questions")
                                        .header("Authorization", "Bearer " + intruderToken)
                                        .param("size", "100"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode bankContent = objectMapper.readTree(bankResponse).get("content");
        for (JsonNode row : bankContent) {
            assertThat(row.get("id").asLong()).isNotIn(
                    questions.get(0).get("id").asLong(), questions.get(1).get("id").asLong());
        }
    }

    // ------------------------------------------------------------------
    // (2) Provider not configured -> 503 AI_UNAVAILABLE, zero rows written, a
    // NOT_CONFIGURED ledger row present.
    // ------------------------------------------------------------------

    @Test
    void providerNotConfigured_returns503_writesZeroQuestions_andCommitsNotConfiguredLedgerRow() throws Exception {
        Student student = persistActiveStudent();
        String token = loginAsStudent(student);
        Long userId = student.getUser().getId();

        long questionsBefore = interviewQuestionRepository.count();
        List<Long> logIdsBefore = existingLogIds();

        StubAIService.CONFIGURED.set(false);
        try {
            String requestBody = "{\"category\":\"HR\",\"difficulty\":null,\"topic\":null,\"companyName\":null,\"count\":3}";
            String responseBody =
                    mockMvc.perform(
                                    post("/api/interview-questions/generate")
                                            .header("Authorization", "Bearer " + token)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(requestBody))
                            .andExpect(status().isServiceUnavailable())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            assertThat(objectMapper.readTree(responseBody).get("error").asString()).isEqualTo("AI_UNAVAILABLE");
        } finally {
            StubAIService.CONFIGURED.set(true);
        }

        assertThat(interviewQuestionRepository.count()).isEqualTo(questionsBefore);

        AIRequestLog newLog = theOneNewLog(logIdsBefore);
        assertThat(newLog.getOutcome()).isEqualTo(AIRequestOutcome.NOT_CONFIGURED);
        assertThat(newLog.getErrorMessage()).isNotBlank();
        assertThat(logCountForUser(userId)).isGreaterThan(0);
    }

    // ------------------------------------------------------------------
    // (3) Provider throws AIUnavailableException -> 503, zero questions written, a
    // PROVIDER_ERROR ledger row present.
    // ------------------------------------------------------------------

    @Test
    void providerThrows_returns503_writesZeroQuestions_andCommitsProviderErrorLedgerRow() throws Exception {
        Student student = persistActiveStudent();
        String token = loginAsStudent(student);

        long questionsBefore = interviewQuestionRepository.count();
        List<Long> logIdsBefore = existingLogIds();

        StubAIService.FAIL_NEXT_CALL.set(true);
        try {
            String requestBody =
                    "{\"category\":\"CODING\",\"difficulty\":\"HARD\",\"topic\":\"Trees\",\"companyName\":null,"
                            + "\"count\":4}";
            mockMvc.perform(
                            post("/api/interview-questions/generate")
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
                    .andExpect(status().isServiceUnavailable());
        } finally {
            StubAIService.FAIL_NEXT_CALL.set(false);
        }

        assertThat(interviewQuestionRepository.count()).isEqualTo(questionsBefore);

        AIRequestLog newLog = theOneNewLog(logIdsBefore);
        assertThat(newLog.getOutcome()).isEqualTo(AIRequestOutcome.PROVIDER_ERROR);
        assertThat(newLog.getErrorMessage()).isNotBlank();
    }

    // ------------------------------------------------------------------
    // (4) Unparseable content -> 503, zero questions written, a ledger row present. No
    // fabricated question is ever stored.
    // ------------------------------------------------------------------

    @Test
    void unparseableContent_returns503_writesZeroQuestions_neverFabricatesAQuestion() throws Exception {
        Student student = persistActiveStudent();
        String token = loginAsStudent(student);

        long questionsBefore = interviewQuestionRepository.count();
        List<Long> logIdsBefore = existingLogIds();

        StubAIService.NEXT_JSON_CONTENT.set("not json at all, just prose the model wrote instead");
        try {
            String requestBody =
                    "{\"category\":\"APTITUDE\",\"difficulty\":null,\"topic\":null,\"companyName\":null,"
                            + "\"count\":5}";
            mockMvc.perform(
                            post("/api/interview-questions/generate")
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
                    .andExpect(status().isServiceUnavailable());
        } finally {
            StubAIService.NEXT_JSON_CONTENT.set(null);
        }

        assertThat(interviewQuestionRepository.count()).isEqualTo(questionsBefore);
        theOneNewLog(logIdsBefore); // present, outcome not asserted further here
    }

    // ------------------------------------------------------------------
    // (5) Blank-question entries in an otherwise valid response are skipped rather than
    // causing a 500 — covered inline by test (1) via VALID_QUESTIONS_JSON's third entry,
    // and re-verified standalone with an all-blank response (must fail cleanly, not 500).
    // ------------------------------------------------------------------

    @Test
    void allBlankQuestionEntries_areSkipped_fallingBackToTheNoUsableQuestionsFailure() throws Exception {
        Student student = persistActiveStudent();
        String token = loginAsStudent(student);

        long questionsBefore = interviewQuestionRepository.count();

        StubAIService.NEXT_JSON_CONTENT.set(
                "{\"questions\":[{\"question\":\"\",\"answer\":\"x\"},{\"question\":\"   \",\"answer\":\"y\"}]}");
        try {
            String requestBody =
                    "{\"category\":\"BEHAVIOURAL\",\"difficulty\":null,\"topic\":null,\"companyName\":null,"
                            + "\"count\":2}";
            String responseBody =
                    mockMvc.perform(
                                    post("/api/interview-questions/generate")
                                            .header("Authorization", "Bearer " + token)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(requestBody))
                            .andExpect(status().isServiceUnavailable())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            // A clean 503, never a 500 — even though every entry was unusable.
            assertThat(objectMapper.readTree(responseBody).get("error").asString()).isEqualTo("AI_UNAVAILABLE");
        } finally {
            StubAIService.NEXT_JSON_CONTENT.set(null);
        }

        assertThat(interviewQuestionRepository.count()).isEqualTo(questionsBefore);
    }

    // ------------------------------------------------------------------
    // (6) COMPANY_SPECIFIC with no companyName -> 400, not 500.
    // ------------------------------------------------------------------

    @Test
    void companySpecificWithNoCompanyName_returns400_notAProviderCall() throws Exception {
        Student student = persistActiveStudent();
        String token = loginAsStudent(student);

        String requestBody =
                "{\"category\":\"COMPANY_SPECIFIC\",\"difficulty\":null,\"topic\":null,\"companyName\":null,"
                        + "\"count\":3}";
        String responseBody =
                mockMvc.perform(
                                post("/api/interview-questions/generate")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(requestBody))
                        .andExpect(status().isBadRequest())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(objectMapper.readTree(responseBody).get("error").asString()).isEqualTo("BAD_REQUEST");
    }

    // ------------------------------------------------------------------
    // (7) FACULTY or ADMIN caller -> 403.
    // ------------------------------------------------------------------

    @Test
    void facultyAndAdminCallers_areRefused() throws Exception {
        User faculty = persistUser(Role.FACULTY);
        User admin = persistUser(Role.ADMIN);
        String facultyToken = loginAsUser(faculty);
        String adminToken = loginAsUser(admin);

        String requestBody =
                "{\"category\":\"TECHNICAL\",\"difficulty\":null,\"topic\":null,\"companyName\":null,\"count\":3}";

        mockMvc.perform(
                        post("/api/interview-questions/generate")
                                .header("Authorization", "Bearer " + facultyToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        post("/api/interview-questions/generate")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // (8) Exceeding the per-minute rate limit -> 429.
    // ------------------------------------------------------------------

    @Test
    void exceedingThePerMinuteRateLimit_returns429WithRateLimitExceededErrorCode() throws Exception {
        Student student = persistActiveStudent();
        String token = loginAsStudent(student);

        StubAIService.NEXT_JSON_CONTENT.set(VALID_QUESTIONS_JSON);

        // Default smartcampus.ai.rate-limit.per-minute is 5 — the 6th request in the
        // same minute must be rejected.
        for (int i = 0; i < 5; i++) {
            String requestBody =
                    "{\"category\":\"TECHNICAL\",\"difficulty\":null,\"topic\":\"Topic " + i
                            + "\",\"companyName\":null,\"count\":1}";
            mockMvc.perform(
                            post("/api/interview-questions/generate")
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
                    .andExpect(status().isCreated());
        }

        String requestBody =
                "{\"category\":\"TECHNICAL\",\"difficulty\":null,\"topic\":\"One too many\",\"companyName\":null,"
                        + "\"count\":1}";
        String sixthResponseBody =
                mockMvc.perform(
                                post("/api/interview-questions/generate")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(requestBody))
                        .andExpect(status().isTooManyRequests())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(objectMapper.readTree(sixthResponseBody).get("error").asString())
                .isEqualTo("RATE_LIMIT_EXCEEDED");
    }
}
