package smartcampus.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import smartcampus.TestcontainersConfiguration;
import smartcampus.dto.ApplicationStatusUpdateRequest;
import smartcampus.dto.AttendanceBulkRequest;
import smartcampus.dto.AttendanceMarkEntry;
import smartcampus.dto.AuthResponse;
import smartcampus.dto.CompanyCreateRequest;
import smartcampus.dto.CompanyResponse;
import smartcampus.dto.InterviewRescheduleRequest;
import smartcampus.dto.InterviewScheduleRequest;
import smartcampus.dto.JobCreateRequest;
import smartcampus.dto.JobResponse;
import smartcampus.dto.JobStatusUpdateRequest;
import smartcampus.entity.ApplicationStatus;
import smartcampus.entity.AttendanceStatus;
import smartcampus.entity.Course;
import smartcampus.entity.Department;
import smartcampus.entity.Enrollment;
import smartcampus.entity.EnrollmentStatus;
import smartcampus.entity.InterviewMode;
import smartcampus.entity.InterviewType;
import smartcampus.entity.Job;
import smartcampus.entity.JobStatus;
import smartcampus.entity.JobType;
import smartcampus.entity.Notification;
import smartcampus.entity.NotificationType;
import smartcampus.entity.PlacementApplication;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.Subject;
import smartcampus.entity.User;
import smartcampus.repository.CompanyRepository;
import smartcampus.repository.CourseRepository;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.EnrollmentRepository;
import smartcampus.repository.JobRepository;
import smartcampus.repository.NotificationRepository;
import smartcampus.repository.PlacementApplicationRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.SubjectRepository;
import smartcampus.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 11 wiring checkpoint: proves that {@code ATTENDANCE_WARNING}, {@code
 * PLACEMENT_UPDATE}, {@code APPLICATION_UPDATE} and {@code INTERVIEW_UPDATE}
 * notifications are produced by REAL state changes in the Phase 4/8/10 services — not a
 * seeder — by driving the real HTTP endpoints (real bearer tokens, real MySQL via
 * Testcontainers) and reading the resulting rows straight from {@link
 * NotificationRepository}.
 *
 * <p>Every fixture is tagged with the per-JVM {@link AtomicInteger}-derived {@code "RTW"}
 * prefix (Real-Time Wiring), matching the convention in {@code
 * InterviewSchedulingCheckpointTest} / {@code PlacementCheckpointTest}: Spring's
 * TestContext framework caches one {@link org.springframework.context.ApplicationContext}
 * (and Testcontainers MySQL instance) across the whole suite, so a distinct prefix is
 * what actually keeps this class's rows from colliding with a sibling class's rows in
 * the same physical tables.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class NotificationEventWiringTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private PlacementApplicationRepository placementApplicationRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String PREFIX = "RTW";
    private static final String RAW_PASSWORD = "CheckpointPass1!";

    private static String tag() {
        return PREFIX + SEQUENCE.incrementAndGet();
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

    private Subject persistSubject(Course course, int semester, int credits) {
        String t = tag();
        return subjectRepository.save(
                Subject.builder()
                        .code(t + "S")
                        .name(t + " Subject")
                        .credits(credits)
                        .semester(semester)
                        .course(course)
                        .build());
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

    private Student persistActiveStudent(Department department, Course course, int semester, String section) {
        String t = tag();
        User user = persistUser(Role.STUDENT);
        return studentRepository.save(
                Student.builder()
                        .user(user)
                        .registerNumber(t + "REG")
                        .department(department)
                        .course(course)
                        .currentSemester(semester)
                        .section(section)
                        .admissionYear(2024)
                        .status(StudentStatus.ACTIVE)
                        .build());
    }

    private void enroll(Student student, Subject subject, String academicYear, int semester, String section) {
        enrollmentRepository.save(
                Enrollment.builder()
                        .student(student)
                        .subject(subject)
                        .academicYear(academicYear)
                        .semester(semester)
                        .section(section)
                        .status(EnrollmentStatus.ACTIVE)
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
        return objectMapper.readValue(body, AuthResponse.class).token();
    }

    private String loginAsStudent(Student student) throws Exception {
        return login(student.getUser().getEmail(), RAW_PASSWORD);
    }

    private String adminToken() throws Exception {
        return login(persistUser(Role.ADMIN).getEmail(), RAW_PASSWORD);
    }

    // ------------------------------------------------------------------
    // Notification-repository assertions
    // ------------------------------------------------------------------

    private List<Notification> notificationsFor(Long userId, NotificationType type) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getUser().getId().equals(userId) && n.getType() == type)
                .toList();
    }

    // ==================================================================
    // A. ATTENDANCE_WARNING
    // ==================================================================

    private void bulkMark(
            String token, Long subjectId, String academicYear, int semester, String section,
            LocalDate date, int period, long studentId, AttendanceStatus status)
            throws Exception {
        AttendanceBulkRequest request =
                new AttendanceBulkRequest(
                        subjectId,
                        academicYear,
                        semester,
                        section,
                        date,
                        period,
                        List.of(new AttendanceMarkEntry(studentId, status, null)));
        mockMvc.perform(
                        post("/api/attendance/bulk")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void attendanceWarning_belowThreshold_createsExactlyOne_dedupedOnRemark_andRecipientIsTheStudent()
            throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subject = persistSubject(course, 1, 4);
        Student student = persistActiveStudent(department, course, 1, "A");
        String academicYear = "2025-2026";
        int semester = 1;
        String section = "A";
        enroll(student, subject, academicYear, semester, section);

        String admin = adminToken();

        // First mark: ABSENT -> 0/1 = 0% < 75% -> the warning is created here.
        bulkMark(
                admin, subject.getId(), academicYear, semester, section,
                LocalDate.now().minusDays(2), 1, student.getId(), AttendanceStatus.ABSENT);
        List<Notification> afterFirst = notificationsFor(student.getUser().getId(), NotificationType.ATTENDANCE_WARNING);
        assertThat(afterFirst).hasSize(1);
        assertThat(afterFirst.get(0).getUser().getId()).isEqualTo(student.getUser().getId());
        assertThat(afterFirst.get(0).getMessage()).contains("0.00").contains("75");

        // Mark the same subject/term a second time (still low) -> deduped, no second row.
        bulkMark(
                admin, subject.getId(), academicYear, semester, section,
                LocalDate.now().minusDays(1), 2, student.getId(), AttendanceStatus.ABSENT);
        List<Notification> afterSecond = notificationsFor(student.getUser().getId(), NotificationType.ATTENDANCE_WARNING);
        assertThat(afterSecond).hasSize(1);
        assertThat(afterSecond.get(0).getId()).isEqualTo(afterFirst.get(0).getId());
    }

    @Test
    void attendanceWarning_atOrAboveThreshold_createsNone() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subject = persistSubject(course, 1, 4);
        Student student = persistActiveStudent(department, course, 1, "A");
        String academicYear = "2025-2026";
        int semester = 1;
        String section = "A";
        enroll(student, subject, academicYear, semester, section);

        String admin = adminToken();

        // A single PRESENT record -> 100% -> not low.
        bulkMark(
                admin, subject.getId(), academicYear, semester, section,
                LocalDate.now().minusDays(1), 1, student.getId(), AttendanceStatus.PRESENT);

        assertThat(notificationsFor(student.getUser().getId(), NotificationType.ATTENDANCE_WARNING)).isEmpty();
    }

    @Test
    void attendanceWarning_everySessionCancelled_heldZero_createsNone() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subject = persistSubject(course, 1, 4);
        Student student = persistActiveStudent(department, course, 1, "A");
        String academicYear = "2025-2026";
        int semester = 1;
        String section = "A";
        enroll(student, subject, academicYear, semester, section);

        String admin = adminToken();

        // heldClasses == 0 (every session CANCELLED) is G6's null-not-zero case: never
        // "low", however the percentage math is read.
        bulkMark(
                admin, subject.getId(), academicYear, semester, section,
                LocalDate.now().minusDays(1), 1, student.getId(), AttendanceStatus.CANCELLED);

        assertThat(notificationsFor(student.getUser().getId(), NotificationType.ATTENDANCE_WARNING)).isEmpty();
    }

    // ==================================================================
    // B. PLACEMENT_UPDATE
    // ==================================================================

    private MockHttpServletResponse postJson(String token, String url, Object body) throws Exception {
        return mockMvc.perform(
                        post(url)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andReturn()
                .getResponse();
    }

    private CompanyResponse createCompany(String adminToken) throws Exception {
        CompanyCreateRequest request =
                new CompanyCreateRequest(tag() + " Company", null, null, null, null, null, null, null);
        MockHttpServletResponse response = postJson(adminToken, "/api/companies", request);
        assertThat(response.getStatus()).isEqualTo(201);
        return objectMapper.readValue(response.getContentAsString(), CompanyResponse.class);
    }

    @Test
    void placementDriveOpen_notifiesActiveStudentInEligibleDepartment_notStudentInOtherDepartment()
            throws Exception {
        Department eligibleDept = persistDepartment();
        Department otherDept = persistDepartment();
        Course eligibleCourse = persistCourse(eligibleDept);
        Course otherCourse = persistCourse(otherDept);
        Student eligibleStudent = persistActiveStudent(eligibleDept, eligibleCourse, 6, "A");
        Student otherStudent = persistActiveStudent(otherDept, otherCourse, 6, "A");

        String admin = adminToken();
        CompanyResponse company = createCompany(admin);

        JobCreateRequest createRequest =
                new JobCreateRequest(
                        company.id(),
                        tag() + " Drive",
                        null,
                        null,
                        JobType.FULL_TIME,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(eligibleDept.getId()),
                        LocalDateTime.now().plusDays(30),
                        null,
                        JobStatus.DRAFT);
        MockHttpServletResponse createResponse = postJson(admin, "/api/jobs", createRequest);
        assertThat(createResponse.getStatus()).isEqualTo(201);
        JobResponse job = objectMapper.readValue(createResponse.getContentAsString(), JobResponse.class);

        // No PLACEMENT_UPDATE while the drive is DRAFT.
        assertThat(notificationsFor(eligibleStudent.getUser().getId(), NotificationType.PLACEMENT_UPDATE)).isEmpty();

        // DRAFT -> OPEN is the one transition that broadcasts.
        MockHttpServletResponse statusResponse =
                mockMvc.perform(
                                patch("/api/jobs/" + job.id() + "/status")
                                        .header("Authorization", "Bearer " + admin)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(new JobStatusUpdateRequest(JobStatus.OPEN))))
                        .andReturn()
                        .getResponse();
        assertThat(statusResponse.getStatus()).isEqualTo(200);

        List<Notification> eligible =
                notificationsFor(eligibleStudent.getUser().getId(), NotificationType.PLACEMENT_UPDATE);
        assertThat(eligible).hasSize(1);
        assertThat(eligible.get(0).getUser().getId()).isEqualTo(eligibleStudent.getUser().getId());
        assertThat(eligible.get(0).getReferenceId()).isEqualTo(job.id());

        assertThat(notificationsFor(otherStudent.getUser().getId(), NotificationType.PLACEMENT_UPDATE)).isEmpty();
    }

    // ==================================================================
    // C. APPLICATION_UPDATE
    // ==================================================================

    @Test
    void applicationStatusChange_adminNotifiesStudentWithNewStatus_withdrawNotifiesNobody() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Student student = persistActiveStudent(department, course, 8, "A");

        User adminUser = persistUser(Role.ADMIN);
        String admin = login(adminUser.getEmail(), RAW_PASSWORD);
        CompanyResponse company = createCompany(admin);

        Job job =
                jobRepository.save(
                        Job.builder()
                                .company(companyRepository.findById(company.id()).orElseThrow())
                                .title(tag() + " App Job")
                                .jobType(JobType.FULL_TIME)
                                .salaryCurrency("INR")
                                .applicationDeadline(LocalDateTime.now().plusDays(10))
                                .status(JobStatus.OPEN)
                                .postedBy(adminUser)
                                .build());

        PlacementApplication application =
                placementApplicationRepository.save(
                        PlacementApplication.builder()
                                .job(job)
                                .student(student)
                                .status(ApplicationStatus.APPLIED)
                                .build());

        // Admin moves APPLIED -> UNDER_REVIEW: notifies the student, message names the new status.
        ApplicationStatusUpdateRequest statusUpdate =
                new ApplicationStatusUpdateRequest(ApplicationStatus.UNDER_REVIEW, "Reviewing your profile");
        MockHttpServletResponse statusResponse =
                mockMvc.perform(
                                patch("/api/applications/" + application.getId() + "/status")
                                        .header("Authorization", "Bearer " + admin)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(statusUpdate)))
                        .andReturn()
                        .getResponse();
        assertThat(statusResponse.getStatus()).isEqualTo(200);

        List<Notification> afterAdminChange =
                notificationsFor(student.getUser().getId(), NotificationType.APPLICATION_UPDATE);
        assertThat(afterAdminChange).hasSize(1);
        assertThat(afterAdminChange.get(0).getUser().getId()).isEqualTo(student.getUser().getId());
        assertThat(afterAdminChange.get(0).getMessage()).contains("UNDER_REVIEW");

        // The student withdraws their own application: no additional notification.
        String studentToken = loginAsStudent(student);
        MockHttpServletResponse withdrawResponse =
                mockMvc.perform(
                                post("/api/applications/" + application.getId() + "/withdraw")
                                        .header("Authorization", "Bearer " + studentToken))
                        .andReturn()
                        .getResponse();
        assertThat(withdrawResponse.getStatus()).isEqualTo(200);

        List<Notification> afterWithdraw =
                notificationsFor(student.getUser().getId(), NotificationType.APPLICATION_UPDATE);
        assertThat(afterWithdraw).hasSize(1);
        assertThat(afterWithdraw.get(0).getId()).isEqualTo(afterAdminChange.get(0).getId());
    }

    // ==================================================================
    // D. INTERVIEW_UPDATE
    // ==================================================================

    @Test
    void interviewReschedule_adminNotifiesStudent_studentReschedulingOwnInterviewNotifiesNobody()
            throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Student student = persistActiveStudent(department, course, 5, "A");
        String studentToken = loginAsStudent(student);
        String admin = adminToken();

        LocalDateTime start = LocalDateTime.of(2027, 6, 1, 10, 0);

        // The student schedules their OWN interview: self-action, no notification.
        InterviewScheduleRequest scheduleRequest =
                new InterviewScheduleRequest(
                        null,
                        tag() + " Interview",
                        InterviewType.TECHNICAL,
                        "Acme Corp",
                        "Round 1",
                        InterviewMode.ONLINE,
                        "https://meet.example.com/" + tag(),
                        null,
                        "Jane Doe",
                        start,
                        start.plusHours(1),
                        null);
        MockHttpServletResponse scheduleResponse =
                mockMvc.perform(
                                post("/api/interviews")
                                        .header("Authorization", "Bearer " + studentToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(scheduleRequest)))
                        .andReturn()
                        .getResponse();
        assertThat(scheduleResponse.getStatus()).isEqualTo(201);
        long interviewId = objectMapper.readTree(scheduleResponse.getContentAsString()).get("id").asLong();

        assertThat(notificationsFor(student.getUser().getId(), NotificationType.INTERVIEW_UPDATE)).isEmpty();

        // An ADMIN reschedules it: notifies the student.
        InterviewRescheduleRequest adminReschedule =
                new InterviewRescheduleRequest(start.plusDays(1), start.plusDays(1).plusHours(1));
        MockHttpServletResponse adminRescheduleResponse =
                mockMvc.perform(
                                put("/api/interviews/" + interviewId + "/reschedule")
                                        .header("Authorization", "Bearer " + admin)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(adminReschedule)))
                        .andReturn()
                        .getResponse();
        assertThat(adminRescheduleResponse.getStatus()).isEqualTo(200);

        List<Notification> afterAdminReschedule =
                notificationsFor(student.getUser().getId(), NotificationType.INTERVIEW_UPDATE);
        assertThat(afterAdminReschedule).hasSize(1);
        assertThat(afterAdminReschedule.get(0).getUser().getId()).isEqualTo(student.getUser().getId());

        // The student reschedules their OWN interview again: no additional notification.
        InterviewRescheduleRequest ownReschedule =
                new InterviewRescheduleRequest(start.plusDays(2), start.plusDays(2).plusHours(1));
        MockHttpServletResponse ownRescheduleResponse =
                mockMvc.perform(
                                put("/api/interviews/" + interviewId + "/reschedule")
                                        .header("Authorization", "Bearer " + studentToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(ownReschedule)))
                        .andReturn()
                        .getResponse();
        assertThat(ownRescheduleResponse.getStatus()).isEqualTo(200);

        List<Notification> afterOwnReschedule =
                notificationsFor(student.getUser().getId(), NotificationType.INTERVIEW_UPDATE);
        assertThat(afterOwnReschedule).hasSize(1);
        assertThat(afterOwnReschedule.get(0).getId()).isEqualTo(afterAdminReschedule.get(0).getId());
    }
}
