package smartcampus.academic;

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
import smartcampus.entity.Attendance;
import smartcampus.entity.AttendanceStatus;
import smartcampus.entity.Course;
import smartcampus.entity.Department;
import smartcampus.entity.Enrollment;
import smartcampus.entity.EnrollmentStatus;
import smartcampus.entity.Faculty;
import smartcampus.entity.FacultyStatus;
import smartcampus.entity.FacultySubjectAssignment;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.Subject;
import smartcampus.entity.User;
import smartcampus.repository.AttendanceRepository;
import smartcampus.repository.CourseRepository;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.EnrollmentRepository;
import smartcampus.repository.FacultyRepository;
import smartcampus.repository.FacultySubjectAssignmentRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.SubjectRepository;
import smartcampus.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The Phase 4 attendance checkpoint (PROJECT_PLAN.md, clarification G6): "attendance
 * percentage computed correctly across multiple subjects, semesters and academic
 * years, including the zero-records and all-cancelled edge cases" — plus the security
 * property that makes attendance the first faculty-facing write endpoint in this
 * codebase: being assigned to a subject in one section/year/semester must never
 * authorize another, and a STUDENT can never write or read another student's rows.
 *
 * <p>Fixture uniqueness is derived from a per-JVM {@link AtomicInteger}, never {@code
 * System.nanoTime()} — that exact pattern produced a real duplicate-key flake in
 * Phase 3 because {@code nanoTime()}'s resolution is only about 1 microsecond here.
 *
 * <p>Numeric/decimal response assertions go through Jackson's {@link JsonNode}
 * ({@link JsonNode#decimalValue()} compared with {@link BigDecimal#compareTo}) rather
 * than string-matching the raw response body, so the assertions are exact regardless
 * of how many fraction digits Jackson chooses to print.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AttendanceCheckpointTest {

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
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private static int next() {
        return COUNTER.incrementAndGet();
    }

    // ------------------------------------------------------------------
    // Fixture helpers
    // ------------------------------------------------------------------

    private String uniqueEmail(String prefix) {
        return prefix + next() + "@example.com";
    }

    private User persistUser(String email, String rawPassword, Role role) {
        User user =
                User.builder()
                        .email(email)
                        .password(passwordEncoder.encode(rawPassword))
                        .fullName("Checkpoint " + role.name() + " " + email)
                        .role(role)
                        .build();
        return userRepository.save(user);
    }

    private String login(String email, String password) throws Exception {
        String body =
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        String responseBody =
                mockMvc.perform(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(responseBody).get("token").asText();
    }

    private Department persistDepartment() {
        int n = next();
        return departmentRepository.save(
                Department.builder().code("D" + n).name("Department " + n).build());
    }

    private Course persistCourse(Department department) {
        int n = next();
        return courseRepository.save(
                Course.builder().code("C" + n).name("Course " + n).department(department).build());
    }

    private Subject persistSubject(Course course, int semester, int credits) {
        int n = next();
        return subjectRepository.save(
                Subject.builder()
                        .code("S" + n)
                        .name("Subject " + n)
                        .credits(credits)
                        .semester(semester)
                        .course(course)
                        .build());
    }

    private Student persistActiveStudent(Department department, Course course, int semester, String section) {
        int n = next();
        User user = persistUser(uniqueEmail("student" + n + "-"), "StudentPass1!", Role.STUDENT);
        return studentRepository.save(
                Student.builder()
                        .user(user)
                        .registerNumber("REG" + n)
                        .department(department)
                        .course(course)
                        .currentSemester(semester)
                        .section(section)
                        .admissionYear(2024)
                        .status(StudentStatus.ACTIVE)
                        .build());
    }

    private Faculty persistActiveFaculty(Department department) {
        int n = next();
        User user = persistUser(uniqueEmail("faculty" + n + "-"), "FacultyPass1!", Role.FACULTY);
        return facultyRepository.save(
                Faculty.builder()
                        .user(user)
                        .employeeCode("EMP" + n)
                        .department(department)
                        .status(FacultyStatus.ACTIVE)
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

    private void mark(
            Student student,
            Subject subject,
            String academicYear,
            int semester,
            String section,
            LocalDate date,
            int period,
            AttendanceStatus status) {
        attendanceRepository.save(
                Attendance.builder()
                        .student(student)
                        .subject(subject)
                        .academicYear(academicYear)
                        .semester(semester)
                        .section(section)
                        .attendanceDate(date)
                        .period(period)
                        .status(status)
                        .build());
    }

    private record MarkFixture(long studentId, String status) {}

    private String bulkRequestBody(
            Long subjectId,
            String academicYear,
            int semester,
            String section,
            LocalDate date,
            int period,
            List<MarkFixture> entries) {
        StringBuilder entriesJson = new StringBuilder("[");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                entriesJson.append(",");
            }
            MarkFixture entry = entries.get(i);
            entriesJson
                    .append("{\"studentId\":")
                    .append(entry.studentId())
                    .append(",\"status\":\"")
                    .append(entry.status())
                    .append("\"}");
        }
        entriesJson.append("]");
        return "{"
                + "\"subjectId\":" + subjectId + ","
                + "\"academicYear\":\"" + academicYear + "\","
                + "\"semester\":" + semester + ","
                + "\"section\":\"" + section + "\","
                + "\"date\":\"" + date + "\","
                + "\"period\":" + period + ","
                + "\"entries\":" + entriesJson
                + "}";
    }

    private JsonNode getJson(String url, String token) throws Exception {
        String body =
                mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(body);
    }

    private static void assertPercentage(JsonNode node, String field, String expected) {
        JsonNode value = node.get(field);
        assertThat(value.isNull()).as(field + " should not be null").isFalse();
        assertThat(value.decimalValue()).isEqualByComparingTo(new BigDecimal(expected));
    }

    private static void assertNullPercentage(JsonNode node, String field) {
        assertThat(node.get(field).isNull()).as(field + " should be null").isTrue();
    }

    // ------------------------------------------------------------------
    // Cases 1 & 5: per-subject/year/semester buckets are independent, and the
    // overall percentage is the credit-blind aggregate, never the mean of the
    // per-subject percentages.
    // ------------------------------------------------------------------

    @Test
    void perSubjectBucketsAreIndependent_andOverallIsAggregateNotMean() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Student student = persistActiveStudent(department, course, 1, "A");

        Subject subjectA = persistSubject(course, 1, 4);
        Subject subjectB = persistSubject(course, 2, 3);

        String yearA = "2024-2025";
        int semA = 1;
        String yearB = "2025-2026";
        int semB = 2;
        String section = "A";

        enroll(student, subjectA, yearA, semA, section);
        enroll(student, subjectB, yearB, semB, section);

        // subjectA: 4 held (3 PRESENT, 1 ABSENT) -> 3/4 = 75.00%
        mark(student, subjectA, yearA, semA, section, LocalDate.of(2024, 8, 1), 1, AttendanceStatus.PRESENT);
        mark(student, subjectA, yearA, semA, section, LocalDate.of(2024, 8, 2), 1, AttendanceStatus.PRESENT);
        mark(student, subjectA, yearA, semA, section, LocalDate.of(2024, 8, 3), 1, AttendanceStatus.PRESENT);
        mark(student, subjectA, yearA, semA, section, LocalDate.of(2024, 8, 4), 1, AttendanceStatus.ABSENT);

        // subjectB: 10 held (2 PRESENT, 8 ABSENT) -> 2/10 = 20.00%
        for (int i = 1; i <= 2; i++) {
            mark(student, subjectB, yearB, semB, section, LocalDate.of(2025, 9, i), 1, AttendanceStatus.PRESENT);
        }
        for (int i = 3; i <= 10; i++) {
            mark(student, subjectB, yearB, semB, section, LocalDate.of(2025, 9, i), 1, AttendanceStatus.ABSENT);
        }

        String token = login(student.getUser().getEmail(), "StudentPass1!");

        // Per-bucket isolation: filtering to subjectA's year/semester shows ONLY
        // subjectA's 75.00%, never subjectB's rows bleeding in.
        JsonNode summaryA =
                getJson(
                        "/api/attendance/me/summary?academicYear=" + yearA + "&semester=" + semA, token);
        assertThat(summaryA.get("subjects")).hasSize(1);
        assertThat(summaryA.get("subjects").get(0).get("subjectId").asLong()).isEqualTo(subjectA.getId());
        assertThat(summaryA.get("subjects").get(0).get("heldClasses").asLong()).isEqualTo(4);
        assertThat(summaryA.get("subjects").get(0).get("attendedClasses").asLong()).isEqualTo(3);
        assertPercentage(summaryA.get("subjects").get(0), "attendancePercentage", "75.00");
        assertPercentage(summaryA, "overallPercentage", "75.00");

        JsonNode summaryB =
                getJson(
                        "/api/attendance/me/summary?academicYear=" + yearB + "&semester=" + semB, token);
        assertThat(summaryB.get("subjects")).hasSize(1);
        assertThat(summaryB.get("subjects").get(0).get("subjectId").asLong()).isEqualTo(subjectB.getId());
        assertThat(summaryB.get("subjects").get(0).get("heldClasses").asLong()).isEqualTo(10);
        assertThat(summaryB.get("subjects").get(0).get("attendedClasses").asLong()).isEqualTo(2);
        assertPercentage(summaryB.get("subjects").get(0), "attendancePercentage", "20.00");
        assertPercentage(summaryB, "overallPercentage", "20.00");

        // Overall (no filter) sees both subjects; the overall percentage is the
        // credit-blind aggregate sum(attended)/sum(held) = 5/14 = 35.71%, NOT the
        // arithmetic mean of 75.00% and 20.00% (which would be 47.50%).
        JsonNode summaryAll = getJson("/api/attendance/me/summary", token);
        assertThat(summaryAll.get("subjects")).hasSize(2);
        assertThat(summaryAll.get("heldClasses").asLong()).isEqualTo(14);
        assertThat(summaryAll.get("attendedClasses").asLong()).isEqualTo(5);
        assertPercentage(summaryAll, "overallPercentage", "35.71");
    }

    // ------------------------------------------------------------------
    // Case 2: zero records anywhere.
    // ------------------------------------------------------------------

    @Test
    void zeroRecords_givesNullPercentage_notZero_andNoLowAttendanceWarning() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Student student = persistActiveStudent(department, course, 1, "A");

        String token = login(student.getUser().getEmail(), "StudentPass1!");

        JsonNode summary = getJson("/api/attendance/me/summary", token);
        assertThat(summary.get("totalRecords").asLong()).isEqualTo(0);
        assertThat(summary.get("heldClasses").asLong()).isEqualTo(0);
        assertNullPercentage(summary, "overallPercentage");
        assertThat(summary.get("lowAttendance").asBoolean()).isFalse();
        assertThat(summary.get("subjects")).isEmpty();
    }

    // ------------------------------------------------------------------
    // Case 3: every session for a subject CANCELLED.
    // ------------------------------------------------------------------

    @Test
    void allCancelled_givesNullPercentage_zeroHeld_andNoLowAttendanceWarning() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Student student = persistActiveStudent(department, course, 1, "A");
        Subject subject = persistSubject(course, 1, 4);
        String year = "2025-2026";
        int semester = 1;
        String section = "A";
        enroll(student, subject, year, semester, section);

        for (int i = 1; i <= 5; i++) {
            mark(student, subject, year, semester, section, LocalDate.of(2025, 8, i), 1, AttendanceStatus.CANCELLED);
        }

        String token = login(student.getUser().getEmail(), "StudentPass1!");
        JsonNode summary = getJson("/api/attendance/me/summary", token);

        assertThat(summary.get("subjects")).hasSize(1);
        JsonNode subjectSummary = summary.get("subjects").get(0);
        assertThat(subjectSummary.get("heldClasses").asLong()).isEqualTo(0);
        assertThat(subjectSummary.get("cancelledClasses").asLong()).isEqualTo(5);
        assertNullPercentage(subjectSummary, "attendancePercentage");
        assertThat(subjectSummary.get("lowAttendance").asBoolean()).isFalse();

        assertThat(summary.get("heldClasses").asLong()).isEqualTo(0);
        assertNullPercentage(summary, "overallPercentage");
        assertThat(summary.get("lowAttendance").asBoolean()).isFalse();
    }

    // ------------------------------------------------------------------
    // Case 4: a CANCELLED row is excluded from the denominator, not counted against
    // the student — 3 attended of 4 HELD (with 1 CANCELLED on top) is 75.00%, not
    // 3/5 = 60.00%.
    // ------------------------------------------------------------------

    @Test
    void cancelledRow_isExcludedFromDenominator_notCountedAsAbsence() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Student student = persistActiveStudent(department, course, 1, "A");
        Subject subject = persistSubject(course, 1, 4);
        String year = "2025-2026";
        int semester = 1;
        String section = "A";
        enroll(student, subject, year, semester, section);

        mark(student, subject, year, semester, section, LocalDate.of(2025, 9, 1), 1, AttendanceStatus.PRESENT);
        mark(student, subject, year, semester, section, LocalDate.of(2025, 9, 2), 1, AttendanceStatus.PRESENT);
        mark(student, subject, year, semester, section, LocalDate.of(2025, 9, 3), 1, AttendanceStatus.PRESENT);
        mark(student, subject, year, semester, section, LocalDate.of(2025, 9, 4), 1, AttendanceStatus.ABSENT);
        mark(student, subject, year, semester, section, LocalDate.of(2025, 9, 5), 1, AttendanceStatus.CANCELLED);

        String token = login(student.getUser().getEmail(), "StudentPass1!");
        JsonNode summary = getJson("/api/attendance/me/summary", token);

        JsonNode subjectSummary = summary.get("subjects").get(0);
        assertThat(subjectSummary.get("totalRecords").asLong()).isEqualTo(5);
        assertThat(subjectSummary.get("heldClasses").asLong()).isEqualTo(4);
        assertThat(subjectSummary.get("attendedClasses").asLong()).isEqualTo(3);
        assertThat(subjectSummary.get("cancelledClasses").asLong()).isEqualTo(1);
        assertPercentage(subjectSummary, "attendancePercentage", "75.00");
    }

    // ------------------------------------------------------------------
    // /me only ever returns the caller's own rows.
    // ------------------------------------------------------------------

    @Test
    void myAttendance_returnsOnlyCallersOwnRows() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subject = persistSubject(course, 1, 4);
        String year = "2025-2026";
        int semester = 1;
        String section = "A";

        Student studentA = persistActiveStudent(department, course, semester, section);
        Student studentB = persistActiveStudent(department, course, semester, section);
        enroll(studentA, subject, year, semester, section);
        enroll(studentB, subject, year, semester, section);

        mark(studentA, subject, year, semester, section, LocalDate.of(2025, 9, 1), 1, AttendanceStatus.PRESENT);
        mark(studentB, subject, year, semester, section, LocalDate.of(2025, 9, 1), 2, AttendanceStatus.PRESENT);

        String tokenA = login(studentA.getUser().getEmail(), "StudentPass1!");
        JsonNode page = getJson("/api/attendance/me", tokenA);

        assertThat(page.get("content")).hasSize(1);
        assertThat(page.get("content").get(0).get("studentId").asLong()).isEqualTo(studentA.getId());
    }

    // ------------------------------------------------------------------
    // Case 6 (security): an assignment to one tuple never authorizes another.
    // ------------------------------------------------------------------

    @Test
    void facultyBulkMark_deniedOutsideTheirAssignedTuple() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subject = persistSubject(course, 1, 4);
        Faculty faculty = persistActiveFaculty(department);

        String assignedYear = "2025-2026";
        int assignedSemester = 1;
        String assignedSection = "A";
        facultySubjectAssignmentRepository.save(
                FacultySubjectAssignment.builder()
                        .faculty(faculty)
                        .subject(subject)
                        .academicYear(assignedYear)
                        .semester(assignedSemester)
                        .section(assignedSection)
                        .build());

        String facultyToken = login(faculty.getUser().getEmail(), "FacultyPass1!");
        List<MarkFixture> entries = List.of(new MarkFixture(1L, "PRESENT"));

        // Same year/semester, WRONG section -> 403. Bean-validation-valid body, so
        // the 403 can only come from the authorization gate.
        String wrongSectionBody =
                bulkRequestBody(
                        subject.getId(),
                        assignedYear,
                        assignedSemester,
                        "B",
                        LocalDate.of(2025, 9, 1),
                        1,
                        entries);
        mockMvc.perform(
                        post("/api/attendance/bulk")
                                .header("Authorization", "Bearer " + facultyToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(wrongSectionBody))
                .andExpect(status().isForbidden());

        // Same section, WRONG academic year -> 403.
        String wrongYearBody =
                bulkRequestBody(
                        subject.getId(),
                        "2026-2027",
                        assignedSemester,
                        assignedSection,
                        LocalDate.of(2025, 9, 1),
                        1,
                        entries);
        mockMvc.perform(
                        post("/api/attendance/bulk")
                                .header("Authorization", "Bearer " + facultyToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(wrongYearBody))
                .andExpect(status().isForbidden());

        // The exact assigned tuple, however, is allowed and actually writes.
        Student enrolledStudent = persistActiveStudent(department, course, assignedSemester, assignedSection);
        enroll(enrolledStudent, subject, assignedYear, assignedSemester, assignedSection);
        String correctBodyWithStudent =
                bulkRequestBody(
                        subject.getId(),
                        assignedYear,
                        assignedSemester,
                        assignedSection,
                        LocalDate.of(2025, 9, 1),
                        1,
                        List.of(new MarkFixture(enrolledStudent.getId(), "PRESENT")));
        mockMvc.perform(
                        post("/api/attendance/bulk")
                                .header("Authorization", "Bearer " + facultyToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(correctBodyWithStudent))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Case 7 (security): a STUDENT can never write attendance, nor read another
    // student's rows.
    // ------------------------------------------------------------------

    @Test
    void studentCannotBulkMark_orReadAnotherStudentsSummary() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subject = persistSubject(course, 1, 4);
        Student studentA = persistActiveStudent(department, course, 1, "A");
        Student studentB = persistActiveStudent(department, course, 1, "A");

        String tokenA = login(studentA.getUser().getEmail(), "StudentPass1!");

        String body =
                bulkRequestBody(
                        subject.getId(),
                        "2025-2026",
                        1,
                        "A",
                        LocalDate.of(2025, 9, 1),
                        1,
                        List.of(new MarkFixture(studentA.getId(), "PRESENT")));

        mockMvc.perform(
                        post("/api/attendance/bulk")
                                .header("Authorization", "Bearer " + tokenA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isForbidden());

        // The only route that accepts an arbitrary studentId is ADMIN-only; a
        // STUDENT caller is rejected outright, for another student's id and even
        // for their own (that path is not how a student reads their own data).
        mockMvc.perform(
                        get("/api/attendance/summary/" + studentB.getId())
                                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        get("/api/attendance/summary/" + studentA.getId())
                                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Case 8 (security, full tuple matrix): {@link
    // #facultyBulkMark_deniedOutsideTheirAssignedTuple} already covers wrong-section
    // and wrong-year. This closes the other two dimensions of the (subject, year,
    // semester, section) tuple — wrong SUBJECT entirely and wrong SEMESTER of the
    // same subject/year/section — and then proves the assigned tuple is allowed,
    // so every denial above is the guard working rather than the endpoint being
    // broken for everyone.
    // ------------------------------------------------------------------

    @Test
    void facultyBulkMark_deniedForWrongSubjectAndWrongSemester_thenAllowedForExactTuple() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject assignedSubject = persistSubject(course, 1, 4);
        Subject otherSubject = persistSubject(course, 1, 3);
        Faculty faculty = persistActiveFaculty(department);

        String academicYear = "2025-2026";
        int assignedSemester = 1;
        String section = "A";
        facultySubjectAssignmentRepository.save(
                FacultySubjectAssignment.builder()
                        .faculty(faculty)
                        .subject(assignedSubject)
                        .academicYear(academicYear)
                        .semester(assignedSemester)
                        .section(section)
                        .build());

        String facultyToken = login(faculty.getUser().getEmail(), "FacultyPass1!");
        List<MarkFixture> entries = List.of(new MarkFixture(1L, "PRESENT"));

        // A DIFFERENT SUBJECT entirely, same year/semester/section -> 403. Being
        // assigned to one subject must not authorize any other subject.
        String wrongSubjectBody =
                bulkRequestBody(
                        otherSubject.getId(),
                        academicYear,
                        assignedSemester,
                        section,
                        LocalDate.of(2025, 9, 2),
                        1,
                        entries);
        mockMvc.perform(
                        post("/api/attendance/bulk")
                                .header("Authorization", "Bearer " + facultyToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(wrongSubjectBody))
                .andExpect(status().isForbidden());

        // Same subject/year/section, WRONG SEMESTER -> 403.
        String wrongSemesterBody =
                bulkRequestBody(
                        assignedSubject.getId(),
                        academicYear,
                        assignedSemester + 1,
                        section,
                        LocalDate.of(2025, 9, 2),
                        1,
                        entries);
        mockMvc.perform(
                        post("/api/attendance/bulk")
                                .header("Authorization", "Bearer " + facultyToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(wrongSemesterBody))
                .andExpect(status().isForbidden());

        // The exact assigned (subject, year, semester, section) tuple is allowed and
        // actually writes — proving the two denials above are the guard discriminating
        // correctly, not the endpoint rejecting every faculty caller.
        Student enrolledStudent = persistActiveStudent(department, course, assignedSemester, section);
        enroll(enrolledStudent, assignedSubject, academicYear, assignedSemester, section);
        String correctBody =
                bulkRequestBody(
                        assignedSubject.getId(),
                        academicYear,
                        assignedSemester,
                        section,
                        LocalDate.of(2025, 9, 2),
                        1,
                        List.of(new MarkFixture(enrolledStudent.getId(), "PRESENT")));
        mockMvc.perform(
                        post("/api/attendance/bulk")
                                .header("Authorization", "Bearer " + facultyToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(correctBody))
                .andExpect(status().isOk());
    }
}
