package smartcampus.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import smartcampus.dto.AnalyticsAdminResponse;
import smartcampus.dto.AttendanceBulkRequest;
import smartcampus.dto.AttendanceMarkEntry;
import smartcampus.dto.AuthResponse;
import smartcampus.dto.CohortStudentRow;
import smartcampus.dto.ExamCreateRequest;
import smartcampus.dto.ExamResponse;
import smartcampus.dto.MarksBulkRequest;
import smartcampus.dto.MarksEntry;
import smartcampus.entity.AttendanceStatus;
import smartcampus.entity.Course;
import smartcampus.entity.Department;
import smartcampus.entity.Enrollment;
import smartcampus.entity.EnrollmentStatus;
import smartcampus.entity.ExamType;
import smartcampus.entity.PerformanceCategory;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.Subject;
import smartcampus.entity.User;
import smartcampus.repository.CourseRepository;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.EnrollmentRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.SubjectRepository;
import smartcampus.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Adversarial verification pass, additional to {@link AnalyticsCheckpointTest}: exercises the
 * {@code /api/analytics/overview} COHORT rollup (not just a single student) across TWO students
 * enrolled in TWO subjects carrying DIFFERENT credit weights, entering marks that produce
 * different letter grades per subject. Every cohort figure asserted here is independently
 * hand-computed in the test itself (not copied from production code), specifically to catch the
 * PROJECT_PLAN.md warning that credit-weighted GPA/CGPA is "the easiest to get subtly wrong" —
 * e.g. accidentally averaging per-subject percentages instead of weighting by credits, or
 * weighting the cohort average by student instead of by subject-credit.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CohortCreditWeightedGpaCheckpointTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String PREFIX = "CWG";
    private static final String RAW_PASSWORD = "CheckpointPass1!";

    private static String tag() {
        return String.valueOf(SEQUENCE.incrementAndGet());
    }

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
        return userRepository.save(
                User.builder()
                        .email(PREFIX.toLowerCase() + "-" + prefix + t + "@example.com")
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

    private String adminToken() throws Exception {
        User admin = persistUser("admin", Role.ADMIN);
        return login(admin.getEmail(), RAW_PASSWORD);
    }

    private void markAttendance(
            String token, Long subjectId, String academicYear, int semester, String section,
            LocalDate date, int period, Long studentId, AttendanceStatus status) throws Exception {
        AttendanceBulkRequest request =
                new AttendanceBulkRequest(
                        subjectId, academicYear, semester, section, date, period,
                        List.of(new AttendanceMarkEntry(studentId, status, null)));
        mockMvc.perform(
                        post("/api/attendance/bulk")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private ExamResponse createExam(
            String token, Long subjectId, String academicYear, int semester, String section,
            String title, BigDecimal maximumMarks) throws Exception {
        ExamCreateRequest request =
                new ExamCreateRequest(
                        subjectId, title, ExamType.INTERNAL_1, academicYear, semester, section,
                        LocalDate.now(), maximumMarks);
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

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    /**
     * Two students, two subjects (credits 4 and 2), real marks entered through the real API.
     * Every cohort figure the admin overview returns is independently hand-computed here from
     * the raw inputs, including the credit-weighted GPA (never a plain average of grade points
     * or of subject percentages).
     */
    @Test
    void adminOverview_cohortFigures_matchIndependentHandComputation_creditWeightedNotAveraged() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        String academicYear = "2025-2026";
        int semester = 3;
        String section = "A";

        Subject subjectA = persistSubject(course, 4, semester); // 4 credits
        Subject subjectB = persistSubject(course, 2, semester); // 2 credits

        Student alice = persistActiveStudent(department, course, semester, section);
        Student bob = persistActiveStudent(department, course, semester, section);
        enroll(alice, subjectA, academicYear, semester, section);
        enroll(alice, subjectB, academicYear, semester, section);
        enroll(bob, subjectA, academicYear, semester, section);
        enroll(bob, subjectB, academicYear, semester, section);

        String admin = adminToken();

        // Attendance: Alice 9/10 in A (credits irrelevant to attendance), 5/5 in B.
        for (int period = 1; period <= 10; period++) {
            AttendanceStatus status = period == 5 ? AttendanceStatus.ABSENT : AttendanceStatus.PRESENT;
            markAttendance(admin, subjectA.getId(), academicYear, semester, section, LocalDate.now(), period, alice.getId(), status);
        }
        for (int period = 1; period <= 5; period++) {
            markAttendance(admin, subjectB.getId(), academicYear, semester, section, LocalDate.now(), period, alice.getId(), AttendanceStatus.PRESENT);
        }
        // Bob: 7/10 in A, 4/5 in B (one absent).
        int[] bobAbsentDaysA = {3, 6, 9};
        outer:
        for (int period = 1; period <= 10; period++) {
            for (int ab : bobAbsentDaysA) {
                if (ab == period) {
                    markAttendance(admin, subjectA.getId(), academicYear, semester, section, LocalDate.now(), period, bob.getId(), AttendanceStatus.ABSENT);
                    continue outer;
                }
            }
            markAttendance(admin, subjectA.getId(), academicYear, semester, section, LocalDate.now(), period, bob.getId(), AttendanceStatus.PRESENT);
        }
        for (int period = 1; period <= 5; period++) {
            AttendanceStatus status = period == 1 ? AttendanceStatus.ABSENT : AttendanceStatus.PRESENT;
            markAttendance(admin, subjectB.getId(), academicYear, semester, section, LocalDate.now(), period, bob.getId(), status);
        }

        // Marks: exam A max 100, exam B max 50.
        ExamResponse examA = createExam(admin, subjectA.getId(), academicYear, semester, section, "Internal A", bd("100.00"));
        ExamResponse examB = createExam(admin, subjectB.getId(), academicYear, semester, section, "Internal B", bd("50.00"));

        // Alice: A=88/100 (A+ -> 9.0 gp), B=46/50 (O -> 10.0 gp).
        enterMarks(admin, examA.id(), alice.getId(), bd("88.00"));
        enterMarks(admin, examB.id(), alice.getId(), bd("46.00"));
        // Bob: A=65/100 (B+ -> 7.0 gp), B=30/50 (B -> 6.0 gp).
        enterMarks(admin, examA.id(), bob.getId(), bd("65.00"));
        enterMarks(admin, examB.id(), bob.getId(), bd("30.00"));

        // --- Independent hand computation -------------------------------------------------
        // Alice GPA = (4*9.0 + 2*10.0) / 6 = 56/6 = 9.3333... -> 9.33
        BigDecimal aliceGpa = bd("56").divide(bd("6"), 2, RoundingMode.HALF_UP);
        assertThat(aliceGpa).isEqualByComparingTo(bd("9.33"));
        // Bob GPA = (4*7.0 + 2*6.0) / 6 = 40/6 = 6.6666... -> 6.67
        BigDecimal bobGpa = bd("40").divide(bd("6"), 2, RoundingMode.HALF_UP);
        assertThat(bobGpa).isEqualByComparingTo(bd("6.67"));
        // Cohort average GPA (mean of per-student GPAs, NOT credit-weighted across students) =
        // (9.33 + 6.67) / 2 = 8.00
        BigDecimal cohortAverageGpa = aliceGpa.add(bobGpa).divide(bd("2"), 2, RoundingMode.HALF_UP);
        assertThat(cohortAverageGpa).isEqualByComparingTo(bd("8.00"));

        // Cohort marksPercentage = sum(obtained)/sum(maximum) across BOTH students and BOTH
        // subjects = (88+46+65+30) / (100+50+100+50) = 229/300 = 76.3333... -> 76.33
        BigDecimal cohortMarksPct = bd("229").multiply(bd("100")).divide(bd("300"), 2, RoundingMode.HALF_UP);
        assertThat(cohortMarksPct).isEqualByComparingTo(bd("76.33"));

        // Cohort attendancePercentage = sum(attended)/sum(held) = (14+11)/(15+15) = 25/30 = 83.33
        BigDecimal cohortAttendancePct = bd("25").multiply(bd("100")).divide(bd("30"), 2, RoundingMode.HALF_UP);
        assertThat(cohortAttendancePct).isEqualByComparingTo(bd("83.33"));

        // --- Now hit the real HTTP endpoint and confirm the server agrees -------------------
        String overviewBody =
                mockMvc.perform(
                                get("/api/analytics/overview")
                                        .header("Authorization", "Bearer " + admin)
                                        .param("courseId", String.valueOf(course.getId()))
                                        .param("academicYear", academicYear)
                                        .param("semester", String.valueOf(semester)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        AnalyticsAdminResponse overview = objectMapper.readValue(overviewBody, AnalyticsAdminResponse.class);

        assertThat(overview.studentCount()).isEqualTo(2);
        assertThat(overview.marksPercentage()).isEqualByComparingTo(cohortMarksPct);
        assertThat(overview.attendancePercentage()).isEqualByComparingTo(cohortAttendancePct);
        assertThat(overview.averageGpa()).isEqualByComparingTo(cohortAverageGpa);

        // Per-student rows: AnalyticsAdminResponse only carries the AT_RISK subset, so pull the
        // full per-student breakdown from /api/analytics/class (same cohort, same tuple scope,
        // ADMIN unrestricted) to check Alice's and Bob's individual GPA/classification.
        String classBody =
                mockMvc.perform(
                                get("/api/analytics/class")
                                        .header("Authorization", "Bearer " + admin)
                                        .param("courseId", String.valueOf(course.getId()))
                                        .param("academicYear", academicYear)
                                        .param("semester", String.valueOf(semester)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        smartcampus.dto.AnalyticsClassResponse classResponse =
                objectMapper.readValue(classBody, smartcampus.dto.AnalyticsClassResponse.class);

        CohortStudentRow aliceRow =
                classResponse.students().stream().filter(s -> s.studentId().equals(alice.getId())).findFirst().orElseThrow();
        CohortStudentRow bobRow =
                classResponse.students().stream().filter(s -> s.studentId().equals(bob.getId())).findFirst().orElseThrow();
        assertThat(aliceRow.gpa()).isEqualByComparingTo(aliceGpa);
        assertThat(bobRow.gpa()).isEqualByComparingTo(bobGpa);
        assertThat(aliceRow.classification()).isEqualTo(PerformanceCategory.EXCELLENT);
        // Bob: 63.33% marks / 73.33% attendance fails EXCELLENT, GOOD and the seeded AVERAGE
        // band (needs >=75% attendance) -> AT_RISK under the shipped default thresholds.
        assertThat(bobRow.classification()).isEqualTo(PerformanceCategory.AT_RISK);

        // And the admin overview's own atRiskStudents list must independently agree that Bob
        // (and only Bob) is AT_RISK in this scope.
        List<Long> atRiskIds = overview.atRiskStudents().stream().map(CohortStudentRow::studentId).toList();
        assertThat(atRiskIds).containsExactly(bob.getId());
    }
}
