package smartcampus.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import smartcampus.dto.AIStatusResponse;
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
import smartcampus.repository.AttendanceRepository;
import smartcampus.repository.CourseRepository;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.EnrollmentRepository;
import smartcampus.repository.ExamRepository;
import smartcampus.repository.MarksRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.SubjectRepository;
import smartcampus.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Independent adversarial verification for the Phase 6 (AI) wave-B integration,
 * written by the verifying agent — NOT part of the original build. Reuses
 * {@link AIAssistantFlowTest}'s {@code StubAIService} (still {@code @Primary} for this
 * whole test source root wherever it is imported) so no network call or API key is
 * required, exactly like the sibling class.
 *
 * <p>Covers checkpoint items not already exercised by {@code AIAssistantFlowTest}:
 * per-caller rate-limit isolation (one student's exhausted limit must not affect
 * another student's), {@code GET /api/ai/context} being STUDENT-only, and the response
 * DTO {@code AIStatusResponse} never declaring any field that could carry a secret.
 */
@Import({TestcontainersConfiguration.class, AIAssistantFlowTest.StubAIServiceConfig.class})
@SpringBootTest
@AutoConfigureMockMvc
class AIWaveBVerificationTest {

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

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String RAW_PASSWORD = "CheckpointPass1!";
    private static final String PREFIX = "AIWBV";

    private static String tag() {
        return PREFIX + SEQUENCE.incrementAndGet();
    }

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

    private void recordMarks(Exam exam, Student student, BigDecimal marksObtained) {
        marksRepository.save(Marks.builder().exam(exam).student(student).marksObtained(marksObtained).build());
    }

    private void recordAttendance(Student student, Subject subject, int presentCount, int absentCount) {
        LocalDate date = LocalDate.now().minusDays(20);
        for (int i = 0; i < presentCount; i++) {
            attendanceRepository.save(
                    Attendance.builder()
                            .student(student)
                            .subject(subject)
                            .academicYear("2025-2026")
                            .semester(1)
                            .section("A")
                            .attendanceDate(date.plusDays(i))
                            .period(1)
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
                            .period(1)
                            .status(AttendanceStatus.ABSENT)
                            .build());
        }
    }

    private Student buildGroundedStudent() {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subject = persistSubject(course);
        Student student = persistActiveStudent(department, course);
        enroll(student, subject);
        Exam exam = persistGradedExam(subject, "Exam " + tag(), new BigDecimal("100.00"));
        recordMarks(exam, student, new BigDecimal("40.00"));
        recordAttendance(student, subject, 2, 2);
        return student;
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

    // ------------------------------------------------------------------
    // Rate limiting must be scoped PER CALLER, not global.
    // ------------------------------------------------------------------

    @Test
    void rateLimitIsIsolatedPerCaller_studentAsExhaustionNeverBlocksStudentB() throws Exception {
        Student studentA = buildGroundedStudent();
        Student studentB = buildGroundedStudent();
        String tokenA = loginAsStudent(studentA);
        String tokenB = loginAsStudent(studentB);

        // Exhaust A's per-minute cap (default 5).
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(
                            post("/api/ai/explain")
                                    .header("Authorization", "Bearer " + tokenA)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"topic\":\"A topic " + i + "\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(
                        post("/api/ai/explain")
                                .header("Authorization", "Bearer " + tokenA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"topic\":\"A one too many\"}"))
                .andExpect(status().isTooManyRequests());

        // B, a completely different caller, must be unaffected.
        mockMvc.perform(
                        post("/api/ai/explain")
                                .header("Authorization", "Bearer " + tokenB)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"topic\":\"B topic\"}"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // GET /api/ai/context is STUDENT-only, same as the conversation endpoints.
    // ------------------------------------------------------------------

    @Test
    void aiContext_refusesFacultyAndAdmin_grantsOwnStudent() throws Exception {
        Student student = buildGroundedStudent();
        User faculty = persistUser(Role.FACULTY);
        User admin = persistUser(Role.ADMIN);

        String studentToken = loginAsStudent(student);
        String facultyToken = loginAsUser(faculty);
        String adminToken = loginAsUser(admin);

        mockMvc.perform(get("/api/ai/context").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ai/context").header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ai/context").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // GET /api/ai/models is ADMIN-only — a STUDENT or FACULTY token must 403, not
    // silently fall through to authenticated().
    // ------------------------------------------------------------------

    @Test
    void aiModels_refusesStudentAndFaculty_allowsAdminThroughToTheServiceLayer() throws Exception {
        Student student = buildGroundedStudent();
        User faculty = persistUser(Role.FACULTY);
        User admin = persistUser(Role.ADMIN);

        String studentToken = loginAsStudent(student);
        String facultyToken = loginAsUser(faculty);
        String adminToken = loginAsUser(admin);

        mockMvc.perform(get("/api/ai/models").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ai/models").header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isForbidden());
        // Admin reaches the service layer — with the stub configured, that is a 200
        // with the stub's model list, proving the route rule (not the service) was the
        // only thing standing between a non-admin and this diagnostic endpoint.
        mockMvc.perform(get("/api/ai/models").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // §25/§61: the AIStatusResponse contract can never carry a secret, structurally —
    // not just "nobody happened to populate one". A future edit that adds an apiKey
    // field to this record would be a live §61 leak the day it shipped; this test
    // fails the build the moment that happens, before any HTTP round trip is needed.
    // ------------------------------------------------------------------

    @Test
    void aiStatusResponseRecord_declaresNoFieldNamedLikeASecret() {
        for (RecordComponent component : AIStatusResponse.class.getRecordComponents()) {
            String name = component.getName().toLowerCase();
            assertThat(name)
                    .as("AIStatusResponse component '%s' looks like it could carry a secret", name)
                    .doesNotContain("key")
                    .doesNotContain("secret")
                    .doesNotContain("token")
                    .doesNotContain("password");
        }
    }

    @Test
    void statusEndpoint_responseBody_neverContainsApiKeyOrGroqCredentialShapedText() throws Exception {
        Student student = buildGroundedStudent();
        String token = loginAsStudent(student);

        String body =
                mockMvc.perform(get("/api/ai/status").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertThat(json.propertyNames()).noneMatch(n -> n.toLowerCase().contains("key"));
        assertThat(body).doesNotContain("gsk_"); // Groq API key prefix
    }
}
