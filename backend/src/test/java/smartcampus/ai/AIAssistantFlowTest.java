package smartcampus.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import smartcampus.entity.AIConversation;
import smartcampus.entity.AIMessage;
import smartcampus.entity.AIMessageRole;
import smartcampus.entity.AIRequestLog;
import smartcampus.entity.AIRequestOutcome;
import smartcampus.entity.AIStudyPlan;
import smartcampus.entity.AIStudyPlanItem;
import smartcampus.entity.AIStudyPlanSource;
import smartcampus.entity.Attendance;
import smartcampus.entity.AttendanceStatus;
import smartcampus.entity.Course;
import smartcampus.entity.Department;
import smartcampus.entity.Enrollment;
import smartcampus.entity.EnrollmentStatus;
import smartcampus.entity.Exam;
import smartcampus.entity.ExamStatus;
import smartcampus.entity.ExamType;
import smartcampus.entity.Marks;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.Subject;
import smartcampus.entity.User;
import smartcampus.exception.AIUnavailableException;
import smartcampus.repository.AIConversationRepository;
import smartcampus.repository.AIMessageRepository;
import smartcampus.repository.AIRequestLogRepository;
import smartcampus.repository.AIStudyPlanItemRepository;
import smartcampus.repository.AIStudyPlanRepository;
import smartcampus.repository.AttendanceRepository;
import smartcampus.repository.CourseRepository;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.EnrollmentRepository;
import smartcampus.repository.ExamRepository;
import smartcampus.repository.MarksRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.SubjectRepository;
import smartcampus.repository.UserRepository;
import smartcampus.service.AIChatMessage;
import smartcampus.service.AICompletion;
import smartcampus.service.AICompletionRequest;
import smartcampus.service.AIModelInfo;
import smartcampus.service.AIService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 6 (AI) verification: real HTTP, real JWTs, real MySQL (Testcontainers, real
 * Flyway V6 migration) and the REAL orchestration/controller layer this task built —
 * only the external provider is replaced, by a {@link StubAIService} bean substituted
 * for {@code GroqAIService} via {@code @Primary} in {@link StubAIServiceConfig}, so
 * these tests never need network access or an API key.
 *
 * <p>Every fixture code/email is derived from a per-JVM {@link AtomicInteger}, never
 * {@code System.nanoTime()} — PROJECT_PLAN.md documents a real duplicate-key flake
 * elsewhere caused by exactly that pattern.
 */
@Import({TestcontainersConfiguration.class, AIAssistantFlowTest.StubAIServiceConfig.class})
@SpringBootTest
@AutoConfigureMockMvc
class AIAssistantFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private ExamRepository examRepository;
    @Autowired private MarksRepository marksRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AIConversationRepository aiConversationRepository;
    @Autowired private AIMessageRepository aiMessageRepository;
    @Autowired private AIRequestLogRepository aiRequestLogRepository;
    @Autowired private AIStudyPlanRepository aiStudyPlanRepository;
    @Autowired private AIStudyPlanItemRepository aiStudyPlanItemRepository;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String RAW_PASSWORD = "CheckpointPass1!";
    // "AIT" tags every fixture code/email this class creates, distinguishing them from
    // any sibling checkpoint test class sharing the cached Spring context / MySQL
    // instance (see MarksAndGradesCheckpointTest's identical note).
    private static final String PREFIX = "AIT";

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
     * completion — never a network call. {@link #forceNextCallToFail} makes exactly
     * the next {@link #complete} call throw {@link AIUnavailableException}, proving the
     * PROVIDER_ERROR / no-partial-persistence path. {@link #nextJsonContent} lets a
     * test control the JSON body returned to study-plan generation.
     */
    static final class StubAIService implements AIService {
        static final AtomicBoolean FAIL_NEXT_CALL = new AtomicBoolean(false);
        static final AtomicReference<String> NEXT_JSON_CONTENT = new AtomicReference<>();
        static final List<List<AIChatMessage>> CAPTURED_REQUESTS =
                Collections.synchronizedList(new ArrayList<>());

        @Override
        public boolean isConfigured() {
            return true;
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
            if (request.jsonObject()) {
                String content = NEXT_JSON_CONTENT.get();
                return new AICompletion(
                        content != null ? content : "{\"title\":\"Plan\",\"goal\":null,\"items\":[]}",
                        "stub-model-1", 12, 34, 46, 7L);
            }
            return new AICompletion("This is a stubbed AI answer.", "stub-model-1", 12, 34, 46, 7L);
        }
    }

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

    private Subject persistSubject(Course course) {
        String t = tag();
        return subjectRepository.save(
                Subject.builder().code(t + "S").name(t + " Subject").credits(4).semester(1).course(course).build());
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

    private Student persistActiveStudent(Department department, Course course) {
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

    private void enroll(Student student, Subject subject) {
        enrollmentRepository.save(
                Enrollment.builder()
                        .student(student)
                        .subject(subject)
                        .academicYear("2025-2026")
                        .semester(1)
                        .section("A")
                        .status(EnrollmentStatus.ACTIVE)
                        .build());
    }

    /** A graded exam (past date) whose marks feed the grounding proof. */
    private Exam persistGradedExam(Subject subject, String title, BigDecimal maximumMarks) {
        return examRepository.save(
                Exam.builder()
                        .subject(subject)
                        .title(title)
                        .examType(ExamType.SEMESTER)
                        .academicYear("2025-2026")
                        .semester(1)
                        .section("A")
                        .examDate(LocalDate.now().minusDays(10))
                        .maximumMarks(maximumMarks)
                        .status(ExamStatus.SCHEDULED)
                        .build());
    }

    /** A future SCHEDULED exam — this is what {@code /api/ai/context}'s "upcoming exams" surfaces. */
    private Exam persistUpcomingExam(Subject subject, String title, BigDecimal maximumMarks) {
        return examRepository.save(
                Exam.builder()
                        .subject(subject)
                        .title(title)
                        .examType(ExamType.QUIZ)
                        .academicYear("2025-2026")
                        .semester(1)
                        .section("A")
                        .examDate(LocalDate.now().plusDays(7))
                        .maximumMarks(maximumMarks)
                        .status(ExamStatus.SCHEDULED)
                        .build());
    }

    private void recordMarks(Exam exam, Student student, BigDecimal marksObtained) {
        marksRepository.save(Marks.builder().exam(exam).student(student).marksObtained(marksObtained).build());
    }

    private void recordAttendance(
            Student student, Subject subject, int presentCount, int absentCount) {
        LocalDate date = LocalDate.now().minusDays(20);
        int period = 1;
        for (int i = 0; i < presentCount; i++) {
            attendanceRepository.save(
                    Attendance.builder()
                            .student(student)
                            .subject(subject)
                            .academicYear("2025-2026")
                            .semester(1)
                            .section("A")
                            .attendanceDate(date.plusDays(i))
                            .period(period)
                            .status(AttendanceStatus.PRESENT)
                            .build());
        }
        for (int i = 0; i < absentCount; i++) {
            attendanceRepository.save(
                    Attendance.builder()
                            .student(student)
                            .subject(subject)
                            .academicYear("2025-2026")
                            .semester(1)
                            .section("A")
                            .attendanceDate(date.plusDays(presentCount + i))
                            .period(period)
                            .status(AttendanceStatus.ABSENT)
                            .build());
        }
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

    /** A fully-fixtured, grounded student: one graded subject, attendance, and an upcoming exam. */
    private record GroundedFixture(
            Student student, Subject subject, String upcomingExamTitle, String gradedExamTitle) {}

    private GroundedFixture buildGroundedStudent() {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subject = persistSubject(course);
        Student student = persistActiveStudent(department, course);
        enroll(student, subject);

        String gradedTitle = "Grounding Graded " + tag();
        Exam gradedExam = persistGradedExam(subject, gradedTitle, new BigDecimal("100.00"));
        recordMarks(gradedExam, student, new BigDecimal("42.00"));

        recordAttendance(student, subject, 3, 1); // 3/4 = 75%

        String upcomingTitle = "Grounding Upcoming " + tag();
        persistUpcomingExam(subject, upcomingTitle, new BigDecimal("50.00"));

        return new GroundedFixture(student, subject, upcomingTitle, gradedTitle);
    }

    // ------------------------------------------------------------------
    // (1) Grounding proof.
    // ------------------------------------------------------------------

    @Test
    void systemMessage_isGroundedInTheStudentsRealAcademicRecord() throws Exception {
        GroundedFixture fixture = buildGroundedStudent();
        String token = loginAsStudent(fixture.student());

        String createBody = "{\"title\":null,\"feature\":\"CHAT\",\"message\":\"How am I doing?\"}";
        String responseBody =
                mockMvc.perform(
                                post("/api/ai/conversations")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(createBody))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode created = objectMapper.readTree(responseBody);
        long conversationId = created.get("conversation").get("id").asLong();

        List<AIMessage> messages =
                aiMessageRepository.findByConversationIdOrderBySeqNoAsc(conversationId);
        AIMessage systemMessage =
                messages.stream().filter(m -> m.getRole() == AIMessageRole.SYSTEM).findFirst().orElseThrow();

        assertThat(systemMessage.isGrounded()).isTrue();
        String content = systemMessage.getContent();
        // Real subject code, real attendance count (plain longs — no BigDecimal
        // formatting ambiguity) and the real upcoming-exam title — none of these could
        // appear in the prompt unless it was built from this exact student's live rows.
        assertThat(content).contains(fixture.subject().getCode());
        assertThat(content).doesNotContain("No marks have been recorded for this student yet.");
        assertThat(content).contains("3/4 classes attended");
        assertThat(content).contains(fixture.upcomingExamTitle());
    }

    // ------------------------------------------------------------------
    // (2) Contiguous seq_no; message_count/last_message_at consistency.
    // ------------------------------------------------------------------

    @Test
    void createThenContinue_persistsContiguousSeqNo_andConsistentCounters() throws Exception {
        GroundedFixture fixture = buildGroundedStudent();
        String token = loginAsStudent(fixture.student());

        String createBody = "{\"title\":\"My Chat\",\"feature\":\"CHAT\",\"message\":\"First question\"}";
        String createResponseBody =
                mockMvc.perform(
                                post("/api/ai/conversations")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(createBody))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long conversationId = objectMapper.readTree(createResponseBody).get("conversation").get("id").asLong();

        String continueBody = "{\"message\":\"Second question\"}";
        mockMvc.perform(
                        post("/api/ai/conversations/" + conversationId + "/messages")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(continueBody))
                .andExpect(status().isOk());

        List<AIMessage> messages = aiMessageRepository.findByConversationIdOrderBySeqNoAsc(conversationId);
        // SYSTEM, USER, ASSISTANT for create; the fresh SYSTEM is identical to the
        // stored one (same grounded student, no time-driven change) so continue adds
        // only USER + ASSISTANT.
        for (int i = 0; i < messages.size(); i++) {
            assertThat(messages.get(i).getSeqNo()).isEqualTo(i);
        }

        AIConversation conversation =
                aiConversationRepository.findById(conversationId).orElseThrow();
        assertThat(conversation.getMessageCount()).isEqualTo(messages.size());
        assertThat(conversation.getLastMessageAt())
                .isEqualTo(messages.get(messages.size() - 1).getCreatedAt());

        // Every ASSISTANT row satisfies the anti-fabrication rule: a real model id.
        assertThat(messages.stream().filter(m -> m.getRole() == AIMessageRole.ASSISTANT))
                .allMatch(m -> m.getModel() != null && !m.getModel().isBlank());
    }

    // ------------------------------------------------------------------
    // (3) Rename + delete; delete removes the messages.
    // ------------------------------------------------------------------

    @Test
    void rename_thenDelete_removesTheConversationAndItsMessages() throws Exception {
        GroundedFixture fixture = buildGroundedStudent();
        String token = loginAsStudent(fixture.student());

        String createBody = "{\"title\":\"Original Title\",\"feature\":\"CHAT\",\"message\":\"Hello\"}";
        String createResponseBody =
                mockMvc.perform(
                                post("/api/ai/conversations")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(createBody))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long conversationId = objectMapper.readTree(createResponseBody).get("conversation").get("id").asLong();

        String renameBody = "{\"title\":\"Renamed Title\"}";
        String renameResponseBody =
                mockMvc.perform(
                                put("/api/ai/conversations/" + conversationId)
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(renameBody))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(objectMapper.readTree(renameResponseBody).get("title").asString()).isEqualTo("Renamed Title");
        assertThat(aiConversationRepository.findById(conversationId).orElseThrow().getTitle())
                .isEqualTo("Renamed Title");

        mockMvc.perform(
                        delete("/api/ai/conversations/" + conversationId)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(aiConversationRepository.findById(conversationId)).isEmpty();
        assertThat(aiMessageRepository.findByConversationIdOrderBySeqNoAsc(conversationId)).isEmpty();
    }

    // ------------------------------------------------------------------
    // (4) Ownership: a second student gets 404, never 403.
    // ------------------------------------------------------------------

    @Test
    void aSecondStudent_cannotGetRenameOrDelete_theFirstStudentsConversation_receives404() throws Exception {
        GroundedFixture owner = buildGroundedStudent();
        String ownerToken = loginAsStudent(owner.student());

        String createBody = "{\"title\":\"Owner Chat\",\"feature\":\"CHAT\",\"message\":\"Private question\"}";
        String createResponseBody =
                mockMvc.perform(
                                post("/api/ai/conversations")
                                        .header("Authorization", "Bearer " + ownerToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(createBody))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long conversationId = objectMapper.readTree(createResponseBody).get("conversation").get("id").asLong();

        GroundedFixture intruder = buildGroundedStudent();
        String intruderToken = loginAsStudent(intruder.student());

        mockMvc.perform(
                        get("/api/ai/conversations/" + conversationId)
                                .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(
                        put("/api/ai/conversations/" + conversationId)
                                .header("Authorization", "Bearer " + intruderToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"Hijacked\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(
                        delete("/api/ai/conversations/" + conversationId)
                                .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());

        // Untouched: the owner's conversation and its original title survive.
        assertThat(aiConversationRepository.findById(conversationId).orElseThrow().getTitle())
                .isEqualTo("Owner Chat");
    }

    // ------------------------------------------------------------------
    // (5) Provider failure: no conversation/message persisted, but a PROVIDER_ERROR
    // ledger row IS committed — the transaction-split trap.
    // ------------------------------------------------------------------

    @Test
    void providerFailure_persistsNoConversationOrMessage_butCommitsAProviderErrorLedgerRow() throws Exception {
        GroundedFixture fixture = buildGroundedStudent();
        String token = loginAsStudent(fixture.student());
        Long userId = fixture.student().getUser().getId();

        long conversationsBefore = aiConversationRepository.count();
        long logsBefore =
                aiRequestLogRepository.countByUserIdAndCreatedAtGreaterThanEqual(
                        userId, java.time.LocalDateTime.now().minusDays(1));
        // Snapshot existing log ids (never dereferencing the lazy `user` association) so
        // the new row can be found by id-difference after the call — safe outside any
        // open persistence context.
        List<Long> logIdsBefore = aiRequestLogRepository.findAll().stream().map(AIRequestLog::getId).toList();

        StubAIService.FAIL_NEXT_CALL.set(true);
        try {
            mockMvc.perform(
                            post("/api/ai/conversations")
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"feature\":\"CHAT\",\"message\":\"This will fail\"}"))
                    .andExpect(status().isServiceUnavailable());
        } finally {
            StubAIService.FAIL_NEXT_CALL.set(false);
        }

        // No conversation was persisted for the failed attempt.
        assertThat(aiConversationRepository.count()).isEqualTo(conversationsBefore);

        // But the ledger row for the failed attempt WAS committed — the transaction
        // survives even though the orchestrating request went on to throw.
        assertThat(
                        aiRequestLogRepository.countByUserIdAndCreatedAtGreaterThanEqual(
                                userId, java.time.LocalDateTime.now().minusDays(1)))
                .isGreaterThan(logsBefore);

        List<AIRequestLog> newLogs =
                aiRequestLogRepository.findAll().stream()
                        .filter(l -> !logIdsBefore.contains(l.getId()))
                        .toList();
        assertThat(newLogs).hasSize(1);
        AIRequestLog newLog = newLogs.get(0);
        assertThat(newLog.getOutcome()).isEqualTo(AIRequestOutcome.PROVIDER_ERROR);
        assertThat(newLog.getErrorMessage()).isNotBlank();
        assertThat(newLog.getConversation()).isNull();
    }

    // ------------------------------------------------------------------
    // (6) Rate limiting: exceeding the per-minute cap returns 429 / RATE_LIMIT_EXCEEDED.
    // ------------------------------------------------------------------

    @Test
    void exceedingThePerMinuteRateLimit_returns429WithRateLimitExceededErrorCode() throws Exception {
        GroundedFixture fixture = buildGroundedStudent();
        String token = loginAsStudent(fixture.student());

        // Default smartcampus.ai.rate-limit.per-minute is 5 — the 6th request in the
        // same minute must be rejected.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(
                            post("/api/ai/explain")
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"topic\":\"Topic " + i + "\"}"))
                    .andExpect(status().isOk());
        }

        String sixthResponseBody =
                mockMvc.perform(
                                post("/api/ai/explain")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"topic\":\"One too many\"}"))
                        .andExpect(status().isTooManyRequests())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(objectMapper.readTree(sixthResponseBody).get("error").asString())
                .isEqualTo("RATE_LIMIT_EXCEEDED");
    }

    // ------------------------------------------------------------------
    // (7) A FACULTY or ADMIN caller is refused on /api/ai/conversations.
    // ------------------------------------------------------------------

    @Test
    void facultyAndAdminCallers_areRefused_onAiConversations() throws Exception {
        User faculty = persistUser(Role.FACULTY);
        User admin = persistUser(Role.ADMIN);
        String facultyToken = loginAsUser(faculty);
        String adminToken = loginAsUser(admin);

        mockMvc.perform(
                        get("/api/ai/conversations").header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ai/conversations").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        post("/api/ai/conversations")
                                .header("Authorization", "Bearer " + facultyToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"feature\":\"CHAT\",\"message\":\"Hi\"}"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // (8) Study-plan generation: items in position order, source=AI_GENERATED, a
    // non-null model; editing an item flips edited to true.
    // ------------------------------------------------------------------

    @Test
    void generatedStudyPlan_persistsItemsInPositionOrder_andEditingAnItemFlipsEditedTrue() throws Exception {
        GroundedFixture fixture = buildGroundedStudent();
        String token = loginAsStudent(fixture.student());

        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.now().plusDays(10);
        LocalDate earlyDate = LocalDate.now().plusDays(2);
        LocalDate lateDate = LocalDate.now().plusDays(8);
        LocalDate outOfRangeDate = LocalDate.now().minusDays(5); // must be dropped

        String json =
                "{\"title\":\"Custom Study Plan\",\"goal\":\"Do well\",\"items\":["
                        + "{\"scheduledDate\":\"" + lateDate + "\",\"subjectCode\":null,"
                        + "\"subjectLabel\":\"" + fixture.subject().getName() + "\",\"title\":\"Late Item\","
                        + "\"description\":\"desc\",\"durationMinutes\":9999},"
                        + "{\"scheduledDate\":\"" + earlyDate + "\",\"subjectCode\":\""
                        + fixture.subject().getCode() + "\",\"subjectLabel\":\"ignored\",\"title\":\"Early Item\","
                        + "\"description\":\"desc\",\"durationMinutes\":45},"
                        + "{\"scheduledDate\":\"" + outOfRangeDate + "\",\"subjectCode\":null,"
                        + "\"subjectLabel\":\"x\",\"title\":\"Out Of Range Item\",\"description\":\"x\","
                        + "\"durationMinutes\":30}"
                        + "]}";
        StubAIService.NEXT_JSON_CONTENT.set(json);

        String requestBody =
                "{\"title\":null,\"goal\":null,\"startDate\":\"" + start + "\",\"endDate\":\"" + end
                        + "\",\"subjectIds\":null,\"dailyMinutes\":60}";
        String responseBody =
                mockMvc.perform(
                                post("/api/ai/study-plans/generate")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(requestBody))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode plan = objectMapper.readTree(responseBody);

        assertThat(plan.get("source").asString()).isEqualTo("AI_GENERATED");
        assertThat(plan.get("model").isNull()).isFalse();
        assertThat(plan.get("model").asString()).isNotBlank();
        assertThat(plan.get("edited").asBoolean()).isFalse();

        JsonNode items = plan.get("items");
        // The out-of-range item was dropped; only the two in-range items remain.
        assertThat(items).hasSize(2);
        // Sorted by scheduledDate ascending -> position 0 is the EARLY item.
        assertThat(items.get(0).get("position").asInt()).isEqualTo(0);
        assertThat(items.get(0).get("title").asString()).isEqualTo("Early Item");
        assertThat(items.get(0).get("subjectCode").asString()).isEqualTo(fixture.subject().getCode());
        assertThat(items.get(1).get("position").asInt()).isEqualTo(1);
        assertThat(items.get(1).get("title").asString()).isEqualTo("Late Item");
        // durationMinutes 9999 is out of [1,1440] -> stored as null, never clamped.
        assertThat(items.get(1).get("durationMinutes").isNull()).isTrue();

        long planId = plan.get("id").asLong();
        long itemId = items.get(0).get("id").asLong();

        List<AIStudyPlanItem> persistedItems =
                aiStudyPlanItemRepository.findByStudyPlanIdOrderByPositionAsc(planId);
        assertThat(persistedItems).hasSize(2);
        for (int i = 0; i < persistedItems.size(); i++) {
            assertThat(persistedItems.get(i).getPosition()).isEqualTo(i);
        }

        AIStudyPlan storedPlan = aiStudyPlanRepository.findById(planId).orElseThrow();
        assertThat(storedPlan.getSource()).isEqualTo(AIStudyPlanSource.AI_GENERATED);
        assertThat(storedPlan.isEdited()).isFalse();

        // Edit the item via PUT — must flip the plan's edited flag to true.
        String updateItemBody =
                "{\"subjectId\":null,\"subjectLabel\":\"Edited Label\",\"scheduledDate\":\"" + earlyDate
                        + "\",\"title\":\"Edited Early Item\",\"description\":\"edited\","
                        + "\"durationMinutes\":30,\"completed\":true,\"position\":0}";
        mockMvc.perform(
                        put("/api/ai/study-plans/" + planId + "/items/" + itemId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateItemBody))
                .andExpect(status().isOk());

        AIStudyPlan afterEdit = aiStudyPlanRepository.findById(planId).orElseThrow();
        assertThat(afterEdit.isEdited()).isTrue();

        AIStudyPlanItem editedItem =
                aiStudyPlanItemRepository.findByIdAndStudyPlanId(itemId, planId).orElseThrow();
        assertThat(editedItem.getTitle()).isEqualTo("Edited Early Item");
        assertThat(editedItem.isCompleted()).isTrue();
        assertThat(editedItem.getCompletedAt()).isNotNull();
    }
}
