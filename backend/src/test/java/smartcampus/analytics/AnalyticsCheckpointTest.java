package smartcampus.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
import smartcampus.dto.AcademicResultResponse;
import smartcampus.dto.AnalyticsAdminResponse;
import smartcampus.dto.AnalyticsClassResponse;
import smartcampus.dto.AnalyticsFilterOptionsResponse;
import smartcampus.dto.AnalyticsStudentResponse;
import smartcampus.dto.AttendanceMarkEntry;
import smartcampus.dto.AttendanceBulkRequest;
import smartcampus.dto.AuthResponse;
import smartcampus.dto.CohortStudentRow;
import smartcampus.dto.ExamResponse;
import smartcampus.dto.ExamCreateRequest;
import smartcampus.dto.MarksBulkRequest;
import smartcampus.dto.MarksEntry;
import smartcampus.dto.SubjectPerformanceRow;
import smartcampus.entity.Course;
import smartcampus.entity.Department;
import smartcampus.entity.Enrollment;
import smartcampus.entity.EnrollmentStatus;
import smartcampus.entity.AttendanceStatus;
import smartcampus.entity.ExamType;
import smartcampus.entity.Faculty;
import smartcampus.entity.FacultyStatus;
import smartcampus.entity.FacultySubjectAssignment;
import smartcampus.entity.PerformanceBand;
import smartcampus.entity.PerformanceCategory;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.Subject;
import smartcampus.entity.User;
import smartcampus.repository.CourseRepository;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.EnrollmentRepository;
import smartcampus.repository.FacultyRepository;
import smartcampus.repository.FacultySubjectAssignmentRepository;
import smartcampus.repository.PerformanceBandRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.SubjectRepository;
import smartcampus.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * The PROJECT_PLAN.md Phase 5 checkpoint: "every figure on every dashboard traces to a
 * database aggregation — no hard-coded numbers anywhere" (§60, §69), plus the security
 * boundary {@code AnalyticsScopeResolver} exists to enforce. Exercised through the real
 * {@code SecurityConfig} filter chain and the real {@code /api/analytics/**} and {@code
 * /api/performance-bands} controllers against Testcontainers MySQL with real Flyway
 * migrations — no H2, no mocking of the authorization or aggregation layers.
 *
 * <p>Every fixture code/email is derived from a per-JVM {@link AtomicInteger} tagged
 * {@code "AC"} (Analytics Checkpoint), never {@code System.nanoTime()} — see
 * {@code MarksAndGradesCheckpointTest}'s identical note: Spring's TestContext framework
 * caches a single ApplicationContext (and Testcontainers MySQL instance) across every
 * test class in the suite with a matching signature, so a distinct prefix is what
 * actually guarantees this class's rows never collide with a sibling test class's rows
 * in the same physical tables.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsCheckpointTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private FacultyRepository facultyRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private FacultySubjectAssignmentRepository facultySubjectAssignmentRepository;
    @Autowired private PerformanceBandRepository performanceBandRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String PREFIX = "AC";
    private static final String RAW_PASSWORD = "CheckpointPass1!";

    private static String tag() {
        return String.valueOf(SEQUENCE.incrementAndGet());
    }

    // ------------------------------------------------------------------
    // Fixture builders
    // ------------------------------------------------------------------

    private Department persistDepartment() {
        String t = tag();
        return departmentRepository.save(
                Department.builder().code(PREFIX + "D" + t).name(PREFIX + " Dept " + t).build());
    }

    private Course persistCourse(Department department) {
        String t = tag();
        return courseRepository.save(
                Course.builder().code(PREFIX + "C" + t).name(PREFIX + " Course " + t).department(department).build());
    }

    private Subject persistSubject(Course course, int credits, int semester) {
        String t = tag();
        return subjectRepository.save(
                Subject.builder()
                        .code(PREFIX + "S" + t)
                        .name(PREFIX + " Subject " + t)
                        .credits(credits)
                        .semester(semester)
                        .course(course)
                        .build());
    }

    private User persistUser(String prefix, Role role) {
        String t = tag();
        String email = PREFIX.toLowerCase() + "-" + prefix + t + "@example.com";
        return userRepository.save(
                User.builder()
                        .email(email)
                        .password(passwordEncoder.encode(RAW_PASSWORD))
                        .fullName(PREFIX + " " + prefix + " " + t)
                        .role(role)
                        .build());
    }

    private Student persistActiveStudent(Department department, Course course, Integer semester, String section) {
        String t = tag();
        User user = persistUser("student", Role.STUDENT);
        return studentRepository.save(
                Student.builder()
                        .user(user)
                        .registerNumber(PREFIX + "REG" + t)
                        .department(department)
                        .course(course)
                        .currentSemester(semester)
                        .section(section)
                        .status(StudentStatus.ACTIVE)
                        .build());
    }

    private Faculty persistActiveFaculty(Department department) {
        String t = tag();
        User user = persistUser("faculty", Role.FACULTY);
        return facultyRepository.save(
                Faculty.builder()
                        .user(user)
                        .employeeCode(PREFIX + "EMP" + t)
                        .department(department)
                        .status(FacultyStatus.ACTIVE)
                        .build());
    }

    private void assign(Faculty faculty, Subject subject, String academicYear, Integer semester, String section) {
        facultySubjectAssignmentRepository.save(
                FacultySubjectAssignment.builder()
                        .faculty(faculty)
                        .subject(subject)
                        .academicYear(academicYear)
                        .semester(semester)
                        .section(section)
                        .build());
    }

    private void enroll(Student student, Subject subject, String academicYear, Integer semester, String section) {
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

    private String loginAsFaculty(Faculty faculty) throws Exception {
        return login(faculty.getUser().getEmail(), RAW_PASSWORD);
    }

    private String adminToken() throws Exception {
        User admin = persistUser("admin", Role.ADMIN);
        return login(admin.getEmail(), RAW_PASSWORD);
    }

    // ------------------------------------------------------------------
    // Thin HTTP wrappers around the real controllers
    // ------------------------------------------------------------------

    private void markAttendance(
            String token,
            Long subjectId,
            String academicYear,
            int semester,
            String section,
            LocalDate date,
            int period,
            Long studentId,
            AttendanceStatus status)
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

    private ExamResponse createExam(
            String token,
            Long subjectId,
            String academicYear,
            int semester,
            String section,
            String title,
            BigDecimal maximumMarks)
            throws Exception {
        ExamCreateRequest request =
                new ExamCreateRequest(
                        subjectId,
                        title,
                        ExamType.INTERNAL_1,
                        academicYear,
                        semester,
                        section,
                        LocalDate.now(),
                        maximumMarks);
        String body =
                mockMvc.perform(
                                post("/api/exams")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readValue(body, ExamResponse.class);
    }

    private void enterMarks(String token, Long examId, Long studentId, BigDecimal marksObtained) throws Exception {
        MarksBulkRequest request = new MarksBulkRequest(examId, List.of(new MarksEntry(studentId, marksObtained, null)));
        mockMvc.perform(
                        post("/api/marks/bulk")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private AnalyticsStudentResponse myAnalytics(String token) throws Exception {
        String body =
                mockMvc.perform(get("/api/analytics/me").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readValue(body, AnalyticsStudentResponse.class);
    }

    private AcademicResultResponse myMarksSummary(String token) throws Exception {
        String body =
                mockMvc.perform(get("/api/marks/me/summary").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readValue(body, AcademicResultResponse.class);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    // ------------------------------------------------------------------
    // (1) Real aggregation: attendance%/marks% match hand-computed sums, cgpa matches
    //     the SAME computation the marks page already shows (reuse, not a parallel calc).
    // ------------------------------------------------------------------

    @Test
    void studentAnalytics_attendanceAndMarksPercentagesMatchHandComputedSums_cgpaMatchesMarksPage() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subject = persistSubject(course, 4, 1);
        String academicYear = "2025-2026";
        int semester = 1;
        String section = "A";

        Student student = persistActiveStudent(department, course, semester, section);
        enroll(student, subject, academicYear, semester, section);

        String admin = adminToken();

        // 10 held classes: 9 PRESENT + 1 ABSENT -> 90.00% attendance, hand-computable.
        for (int period = 1; period <= 10; period++) {
            AttendanceStatus status = period == 10 ? AttendanceStatus.ABSENT : AttendanceStatus.PRESENT;
            markAttendance(admin, subject.getId(), academicYear, semester, section, LocalDate.now(), period, student.getId(), status);
        }

        ExamResponse exam = createExam(admin, subject.getId(), academicYear, semester, section, "Internal 1", bd("100.00"));
        enterMarks(admin, exam.id(), student.getId(), bd("90.00"));

        String studentToken = loginAsStudent(student);
        AnalyticsStudentResponse response = myAnalytics(studentToken);

        assertThat(response.attendancePercentage()).isEqualByComparingTo(bd("90.00"));
        assertThat(response.marksPercentage()).isEqualByComparingTo(bd("90.00"));

        AcademicResultResponse marksSummary = myMarksSummary(studentToken);
        assertThat(response.cgpa()).isEqualByComparingTo(marksSummary.cgpa());
        assertThat(response.cgpa()).isNotNull();

        // marks=90, attendance=90 -> EXCELLENT under the seeded bands (>=85 marks, >=90 attendance).
        assertThat(response.classification().category()).isEqualTo(PerformanceCategory.EXCELLENT);
    }

    // ------------------------------------------------------------------
    // (2) Classification is DB-driven: editing a performance_bands row through the
    //     repository moves the SAME student between categories with no code change.
    // ------------------------------------------------------------------

    @Test
    void classification_isDrivenByPerformanceBandsTable_thresholdEditMovesTheSameStudent() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subject = persistSubject(course, 4, 1);
        String academicYear = "2025-2026";
        int semester = 1;
        String section = "A";

        Student student = persistActiveStudent(department, course, semester, section);
        enroll(student, subject, academicYear, semester, section);

        String admin = adminToken();
        for (int period = 1; period <= 10; period++) {
            AttendanceStatus status = period == 10 ? AttendanceStatus.ABSENT : AttendanceStatus.PRESENT;
            markAttendance(admin, subject.getId(), academicYear, semester, section, LocalDate.now(), period, student.getId(), status);
        }
        ExamResponse exam = createExam(admin, subject.getId(), academicYear, semester, section, "Internal 1", bd("100.00"));
        enterMarks(admin, exam.id(), student.getId(), bd("90.00"));

        String studentToken = loginAsStudent(student);

        assertThat(myAnalytics(studentToken).classification().category()).isEqualTo(PerformanceCategory.EXCELLENT);

        PerformanceBand excellent = performanceBandRepository.findByCategory(PerformanceCategory.EXCELLENT).orElseThrow();
        BigDecimal originalThreshold = excellent.getMinMarksPercentage();
        try {
            // Raise the EXCELLENT bar above this student's 90.00% marks — no Java code
            // changes, only the database row.
            excellent.setMinMarksPercentage(bd("95.00"));
            performanceBandRepository.saveAndFlush(excellent);

            AnalyticsStudentResponse afterEdit = myAnalytics(studentToken);
            assertThat(afterEdit.classification().category()).isEqualTo(PerformanceCategory.GOOD);
            // The underlying figures did not change, only the classification.
            assertThat(afterEdit.marksPercentage()).isEqualByComparingTo(bd("90.00"));
            assertThat(afterEdit.attendancePercentage()).isEqualByComparingTo(bd("90.00"));
        } finally {
            excellent.setMinMarksPercentage(originalThreshold);
            performanceBandRepository.saveAndFlush(excellent);
        }

        // Restored: back to EXCELLENT with no other change.
        assertThat(myAnalytics(studentToken).classification().category()).isEqualTo(PerformanceCategory.EXCELLENT);
    }

    // ------------------------------------------------------------------
    // (3) §69: attendance but zero graded marks -> marksPercentage null, category null,
    //     a non-null reason -- NEVER a fabricated 0.00 or a default AT_RISK verdict.
    // ------------------------------------------------------------------

    @Test
    void studentWithAttendanceButNoGradedMarks_isNullNotZero_neverDefaultedToAtRisk() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subject = persistSubject(course, 3, 1);
        String academicYear = "2025-2026";
        int semester = 1;
        String section = "A";

        Student student = persistActiveStudent(department, course, semester, section);
        enroll(student, subject, academicYear, semester, section);

        String admin = adminToken();
        markAttendance(admin, subject.getId(), academicYear, semester, section, LocalDate.now(), 1, student.getId(), AttendanceStatus.PRESENT);
        markAttendance(admin, subject.getId(), academicYear, semester, section, LocalDate.now(), 2, student.getId(), AttendanceStatus.PRESENT);
        // No exam, no marks at all for this student.

        String studentToken = loginAsStudent(student);
        AnalyticsStudentResponse response = myAnalytics(studentToken);

        assertThat(response.attendancePercentage()).isEqualByComparingTo(bd("100.00"));
        assertThat(response.marksPercentage()).isNull();
        assertThat(response.gpa()).isNull();
        assertThat(response.cgpa()).isNull();
        assertThat(response.classification().category()).isNull();
        assertThat(response.classification().colorHex()).isNull();
        assertThat(response.classification().reason()).isNotBlank();
        assertThat(response.classification().reason()).doesNotContain("AT_RISK");
    }

    // ------------------------------------------------------------------
    // (4) G6: a subject where every session was CANCELLED reports a null attendance
    //     percentage, never a fabricated 0.
    // ------------------------------------------------------------------

    @Test
    void subjectWithEverySessionCancelled_reportsNullAttendancePercentage_notZero() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subject = persistSubject(course, 3, 1);
        String academicYear = "2025-2026";
        int semester = 1;
        String section = "A";

        Student student = persistActiveStudent(department, course, semester, section);
        enroll(student, subject, academicYear, semester, section);

        String admin = adminToken();
        markAttendance(
                admin, subject.getId(), academicYear, semester, section, LocalDate.now(), 1, student.getId(), AttendanceStatus.CANCELLED);
        markAttendance(
                admin, subject.getId(), academicYear, semester, section, LocalDate.now(), 2, student.getId(), AttendanceStatus.CANCELLED);
        markAttendance(
                admin, subject.getId(), academicYear, semester, section, LocalDate.now(), 3, student.getId(), AttendanceStatus.CANCELLED);

        String studentToken = loginAsStudent(student);
        AnalyticsStudentResponse response = myAnalytics(studentToken);

        assertThat(response.attendancePercentage()).isNull();
        SubjectPerformanceRow row =
                response.subjects().stream().filter(s -> s.subjectId().equals(subject.getId())).findFirst().orElseThrow();
        assertThat(row.attendancePercentage()).isNull();
        assertThat(row.heldClasses()).isEqualTo(0L);
    }

    // ------------------------------------------------------------------
    // (5) SECURITY: the AnalyticsScopeResolver tuple boundary. A faculty assigned to
    //     (subjectX, year, sem, section A) must never see section B's students, and
    //     omitting subjectId returns ONLY their own assigned tuples' students.
    // ------------------------------------------------------------------

    @Test
    void facultyClassAnalytics_neverLeaksAnotherSectionsStudents() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subject = persistSubject(course, 4, 1);
        String academicYear = "2025-2026";
        int semester = 3;
        String sectionA = "A";
        String sectionB = "B";

        Faculty facultyX = persistActiveFaculty(department);
        assign(facultyX, subject, academicYear, semester, sectionA);

        Student studentA = persistActiveStudent(department, course, semester, sectionA);
        enroll(studentA, subject, academicYear, semester, sectionA);
        Student studentB = persistActiveStudent(department, course, semester, sectionB);
        enroll(studentB, subject, academicYear, semester, sectionB);

        String admin = adminToken();
        markAttendance(
                admin, subject.getId(), academicYear, semester, sectionA, LocalDate.now(), 1, studentA.getId(), AttendanceStatus.PRESENT);
        markAttendance(
                admin, subject.getId(), academicYear, semester, sectionB, LocalDate.now(), 1, studentB.getId(), AttendanceStatus.PRESENT);

        ExamResponse examA = createExam(admin, subject.getId(), academicYear, semester, sectionA, "Internal 1 - A", bd("100.00"));
        enterMarks(admin, examA.id(), studentA.getId(), bd("80.00"));
        ExamResponse examB = createExam(admin, subject.getId(), academicYear, semester, sectionB, "Internal 1 - B", bd("100.00"));
        enterMarks(admin, examB.id(), studentB.getId(), bd("80.00"));

        String facultyToken = loginAsFaculty(facultyX);

        // Exact tuple for section B, which facultyX is NOT assigned to -> denied outright.
        mockMvc.perform(
                        get("/api/analytics/class")
                                .header("Authorization", "Bearer " + facultyToken)
                                .param("subjectId", String.valueOf(subject.getId()))
                                .param("academicYear", academicYear)
                                .param("semester", String.valueOf(semester))
                                .param("section", sectionB))
                .andExpect(status().isForbidden());

        // Omitting section entirely must fall back to ONLY facultyX's own assigned
        // tuple's students -- never an unfiltered view of both sections.
        String bodyNoSection =
                mockMvc.perform(
                                get("/api/analytics/class")
                                        .header("Authorization", "Bearer " + facultyToken)
                                        .param("subjectId", String.valueOf(subject.getId()))
                                        .param("academicYear", academicYear)
                                        .param("semester", String.valueOf(semester)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        AnalyticsClassResponse responseNoSection = objectMapper.readValue(bodyNoSection, AnalyticsClassResponse.class);
        List<Long> idsNoSection = responseNoSection.students().stream().map(CohortStudentRow::studentId).toList();
        assertThat(idsNoSection).contains(studentA.getId());
        assertThat(idsNoSection).doesNotContain(studentB.getId());

        // Omitting subjectId (and every other filter) too must still resolve to ONLY
        // facultyX's own assigned tuples -- the dangerous mistake this phase warns
        // against is filtering by subjectIds alone and skipping the tuple check.
        String bodyNoFilters =
                mockMvc.perform(get("/api/analytics/class").header("Authorization", "Bearer " + facultyToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        AnalyticsClassResponse responseNoFilters = objectMapper.readValue(bodyNoFilters, AnalyticsClassResponse.class);
        List<Long> idsNoFilters = responseNoFilters.students().stream().map(CohortStudentRow::studentId).toList();
        assertThat(idsNoFilters).contains(studentA.getId());
        assertThat(idsNoFilters).doesNotContain(studentB.getId());
    }

    // ------------------------------------------------------------------
    // (6) A STUDENT caller is rejected from the faculty/admin-only routes.
    // ------------------------------------------------------------------

    @Test
    void studentCaller_isRejectedFromOverviewFiltersAndAnotherStudentsRecord() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Student self = persistActiveStudent(department, course, 1, "A");
        Student other = persistActiveStudent(department, course, 1, "A");
        String studentToken = loginAsStudent(self);

        mockMvc.perform(get("/api/analytics/overview").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/analytics/filters").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        get("/api/analytics/students/" + other.getId()).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // (7) A student with NO data at all gets 200 with nulls and empty lists, not a 500.
    // ------------------------------------------------------------------

    @Test
    void myAnalytics_forStudentWithNoData_returns200WithNullsAndEmptyLists() throws Exception {
        String t = tag();
        String email = PREFIX.toLowerCase() + "-freshstudent" + t + "@example.com";
        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"" + email + "\",\"password\":\"" + RAW_PASSWORD
                                                + "\",\"fullName\":\"Fresh Student\"}"))
                .andExpect(status().isCreated());
        String token = login(email, RAW_PASSWORD);

        AnalyticsStudentResponse response = myAnalytics(token);

        assertThat(response.attendancePercentage()).isNull();
        assertThat(response.marksPercentage()).isNull();
        assertThat(response.gpa()).isNull();
        assertThat(response.cgpa()).isNull();
        assertThat(response.classification().category()).isNull();
        assertThat(response.classification().reason()).isNotBlank();
        assertThat(response.subjects()).isEmpty();
        assertThat(response.attendanceTrend()).isEmpty();
        assertThat(response.marksTrend()).isEmpty();
        assertThat(response.gpaTrend()).isEmpty();
        // Grade distribution still lists every configured band, all at count 0 -- the
        // chart's category axis stays stable even with no data.
        assertThat(response.gradeDistribution()).isNotEmpty();
        response.gradeDistribution().forEach(slice -> assertThat(slice.count()).isEqualTo(0L));
    }

    // ------------------------------------------------------------------
    // (8) ADMIN overview and filters return real, non-crashing aggregates.
    // ------------------------------------------------------------------

    @Test
    void adminOverviewAndFilters_returnRealAggregates() throws Exception {
        String admin = adminToken();

        String overviewBody =
                mockMvc.perform(get("/api/analytics/overview").header("Authorization", "Bearer " + admin))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        AnalyticsAdminResponse overview = objectMapper.readValue(overviewBody, AnalyticsAdminResponse.class);
        assertThat(overview.totalStudents()).isGreaterThanOrEqualTo(0);

        String filtersBody =
                mockMvc.perform(get("/api/analytics/filters").header("Authorization", "Bearer " + admin))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        AnalyticsFilterOptionsResponse filters = objectMapper.readValue(filtersBody, AnalyticsFilterOptionsResponse.class);
        assertThat(filters).isNotNull();
    }
}
