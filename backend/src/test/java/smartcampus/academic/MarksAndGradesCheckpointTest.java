package smartcampus.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
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
import smartcampus.dto.AuthResponse;
import smartcampus.dto.ExamCreateRequest;
import smartcampus.dto.ExamResponse;
import smartcampus.dto.ExamUpdateRequest;
import smartcampus.dto.GradeBandRequest;
import smartcampus.dto.GradeBandResponse;
import smartcampus.dto.MarksBulkRequest;
import smartcampus.dto.MarksBulkResponse;
import smartcampus.dto.MarksEntry;
import smartcampus.dto.SemesterGradeSummary;
import smartcampus.dto.SubjectGradeSummary;
import smartcampus.entity.Course;
import smartcampus.entity.Department;
import smartcampus.entity.Enrollment;
import smartcampus.entity.EnrollmentStatus;
import smartcampus.entity.ExamStatus;
import smartcampus.entity.ExamType;
import smartcampus.entity.Faculty;
import smartcampus.entity.FacultyStatus;
import smartcampus.entity.FacultySubjectAssignment;
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
import smartcampus.repository.StudentRepository;
import smartcampus.repository.SubjectRepository;
import smartcampus.repository.UserRepository;

/**
 * The PROJECT_PLAN.md Phase 4 checkpoint: "grades computed correctly across multiple
 * subjects, semesters and academic years, including the zero-records edge case,"
 * exercised through the real {@code SecurityConfig} filter chain and the real {@code
 * /api/exams}, {@code /api/marks} and {@code /api/grade-bands} controllers against
 * Testcontainers MySQL with real Flyway migrations — no H2, no mocking of the
 * authorization or grading layers.
 *
 * <p>Every fixture code/email is derived from a per-JVM {@link AtomicInteger}, never
 * from {@code System.nanoTime()} — PROJECT_PLAN.md documents a real duplicate-key flake
 * in an earlier phase caused by exactly that pattern (nanoTime's ~1µs resolution on this
 * platform let two fixtures land on the same tick).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MarksAndGradesCheckpointTest {

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
    @Autowired private PasswordEncoder passwordEncoder;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String RAW_PASSWORD = "CheckpointPass1!";

    // ------------------------------------------------------------------
    // Fixture builders — every code/email below is tag()-derived, never nanoTime-derived.
    // ------------------------------------------------------------------

    private static String tag() {
        return String.valueOf(SEQUENCE.incrementAndGet());
    }

    // "MG" (Marks/Grades) tags every fixture code/email this class creates. Spring's
    // TestContext framework caches and REUSES a single ApplicationContext (and so a
    // single Testcontainers MySQL instance) across every test class in the suite whose
    // configuration signature matches — which every Phase 4 checkpoint test's
    // @Import(TestcontainersConfiguration.class) @SpringBootTest @AutoConfigureMockMvc
    // trio does. A bare per-class AtomicInteger starting at 1 (e.g. "D1", "REG1") is
    // therefore NOT enough to guarantee uniqueness on its own: a sibling test class
    // with its own AtomicInteger starting at 1 and the same generic naming scheme
    // ("D" + n) collides in the SAME physical `departments` table. This prefix, plus
    // the per-JVM AtomicInteger for the numeric suffix, is what actually guarantees
    // global uniqueness across every test class sharing the cached context.
    private static final String PREFIX = "MG";

    private Department persistDepartment() {
        String t = tag();
        return departmentRepository.save(
                Department.builder().code(PREFIX + "D" + t).name(PREFIX + " Dept " + t).build());
    }

    private Course persistCourse(Department department) {
        String t = tag();
        return courseRepository.save(
                Course.builder()
                        .code(PREFIX + "C" + t)
                        .name(PREFIX + " Course " + t)
                        .department(department)
                        .build());
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
    // Thin HTTP wrappers around the real controllers, using the real request/response DTOs.
    // ------------------------------------------------------------------

    private ExamResponse createExam(
            String token,
            Long subjectId,
            String academicYear,
            int semester,
            String section,
            ExamType type,
            String title,
            BigDecimal maximumMarks)
            throws Exception {
        ExamCreateRequest request =
                new ExamCreateRequest(
                        subjectId, title, type, academicYear, semester, section, LocalDate.now(), maximumMarks);
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

    private int expectExamCreateStatus(
            String token,
            Long subjectId,
            String academicYear,
            int semester,
            String section,
            ExamType type,
            String title,
            BigDecimal maximumMarks)
            throws Exception {
        ExamCreateRequest request =
                new ExamCreateRequest(
                        subjectId, title, type, academicYear, semester, section, LocalDate.now(), maximumMarks);
        return mockMvc
                .perform(
                        post("/api/exams")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private MarksBulkResponse enterMarks(String token, Long examId, List<MarksEntry> entries) throws Exception {
        MarksBulkRequest request = new MarksBulkRequest(examId, entries);
        String body =
                mockMvc.perform(
                                post("/api/marks/bulk")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readValue(body, MarksBulkResponse.class);
    }

    private int expectMarksBulkStatus(String token, Long examId, List<MarksEntry> entries) throws Exception {
        MarksBulkRequest request = new MarksBulkRequest(examId, entries);
        return mockMvc
                .perform(
                        post("/api/marks/bulk")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private AcademicResultResponse mySummary(String token, String academicYear, Integer semester) throws Exception {
        var builder = get("/api/marks/me/summary").header("Authorization", "Bearer " + token);
        if (academicYear != null) {
            builder = builder.param("academicYear", academicYear);
        }
        if (semester != null) {
            builder = builder.param("semester", String.valueOf(semester));
        }
        String body =
                mockMvc.perform(builder).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AcademicResultResponse.class);
    }

    private SubjectGradeSummary onlySubject(AcademicResultResponse result) {
        assertThat(result.semesters()).hasSize(1);
        assertThat(result.semesters().get(0).subjects()).hasSize(1);
        return result.semesters().get(0).subjects().get(0);
    }

    private List<GradeBandResponse> listGradeBands(String token) throws Exception {
        String body =
                mockMvc.perform(get("/api/grade-bands").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readValue(
                body, objectMapper.getTypeFactory().constructCollectionType(List.class, GradeBandResponse.class));
    }

    private void putGradeBand(String token, Long id, GradeBandRequest request) throws Exception {
        mockMvc.perform(
                        put("/api/grade-bands/" + id)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private static GradeBandRequest asRequest(GradeBandResponse band) {
        return new GradeBandRequest(
                band.grade(), band.minPercentage(), band.maxPercentage(), band.gradePoint(), band.passGrade(),
                band.description());
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    // ------------------------------------------------------------------
    // (1) + (2): multiple subjects/semesters/years, buckets don't bleed, GPA is
    // genuinely credit-weighted (not an unweighted mean).
    // ------------------------------------------------------------------

    @Test
    void gradesComputeAcrossSubjectsSemestersAndYears_creditWeighted_bucketsDoNotBleed() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        // Two subjects with different credits AND different grades in the SAME
        // semester — the arithmetic-mean GPA ((10+7)/2 = 8.50) would visibly differ
        // from the credit-weighted GPA computed below, proving weighting is real.
        Subject subjectHighCredit = persistSubject(course, 4, 1); // -> grade O (10.00)
        Subject subjectLowCredit = persistSubject(course, 1, 1); // -> grade B+ (7.00)
        Subject subjectYearTwo = persistSubject(course, 3, 2); // -> grade A (8.00), different year+semester

        String academicYear1 = "2025-2026";
        String academicYear2 = "2026-2027";
        String section = "A";

        Student student = persistActiveStudent(department, course, 1, section);
        enroll(student, subjectHighCredit, academicYear1, 1, section);
        enroll(student, subjectLowCredit, academicYear1, 1, section);
        enroll(student, subjectYearTwo, academicYear2, 2, section);

        String admin = adminToken();

        ExamResponse examHigh =
                createExam(
                        admin, subjectHighCredit.getId(), academicYear1, 1, section, ExamType.SEMESTER, "Sem Exam HC",
                        bd("100.00"));
        ExamResponse examLow =
                createExam(
                        admin, subjectLowCredit.getId(), academicYear1, 1, section, ExamType.SEMESTER, "Sem Exam LC",
                        bd("100.00"));
        ExamResponse examYear2 =
                createExam(
                        admin, subjectYearTwo.getId(), academicYear2, 2, section, ExamType.SEMESTER, "Sem Exam Y2",
                        bd("50.00"));

        enterMarks(admin, examHigh.id(), List.of(new MarksEntry(student.getId(), bd("95.00"), null))); // 95% -> O
        enterMarks(admin, examLow.id(), List.of(new MarksEntry(student.getId(), bd("65.00"), null))); // 65% -> B+
        enterMarks(admin, examYear2.id(), List.of(new MarksEntry(student.getId(), bd("40.00"), null))); // 80% -> A

        String studentToken = loginAsStudent(student);

        AcademicResultResponse full = mySummary(studentToken, null, null);
        assertThat(full.semesters()).hasSize(2);

        SemesterGradeSummary sem1 =
                full.semesters().stream()
                        .filter(s -> s.academicYear().equals(academicYear1) && s.semester() == 1)
                        .findFirst()
                        .orElseThrow();
        SemesterGradeSummary sem2 =
                full.semesters().stream()
                        .filter(s -> s.academicYear().equals(academicYear2) && s.semester() == 2)
                        .findFirst()
                        .orElseThrow();

        assertThat(sem1.subjectCount()).isEqualTo(2);
        assertThat(sem1.gradedCredits()).isEqualTo(5);
        // Credit-weighted: (4*10 + 1*7) / 5 = 9.40 — NOT the unweighted mean of 8.50.
        assertThat(sem1.gpa()).isEqualByComparingTo(bd("9.40"));
        assertThat(sem1.gpa()).isNotEqualByComparingTo(bd("8.50"));

        SubjectGradeSummary highSummary =
                sem1.subjects().stream().filter(s -> s.subjectId().equals(subjectHighCredit.getId())).findFirst()
                        .orElseThrow();
        assertThat(highSummary.percentage()).isEqualByComparingTo(bd("95.00"));
        assertThat(highSummary.grade()).isEqualTo("O");
        assertThat(highSummary.gradePoint()).isEqualByComparingTo(bd("10.00"));

        SubjectGradeSummary lowSummary =
                sem1.subjects().stream().filter(s -> s.subjectId().equals(subjectLowCredit.getId())).findFirst()
                        .orElseThrow();
        assertThat(lowSummary.percentage()).isEqualByComparingTo(bd("65.00"));
        assertThat(lowSummary.grade()).isEqualTo("B+");
        assertThat(lowSummary.gradePoint()).isEqualByComparingTo(bd("7.00"));

        assertThat(sem2.subjectCount()).isEqualTo(1);
        assertThat(sem2.gradedCredits()).isEqualTo(3);
        assertThat(sem2.gpa()).isEqualByComparingTo(bd("8.00"));
        SubjectGradeSummary year2Summary = sem2.subjects().get(0);
        assertThat(year2Summary.subjectId()).isEqualTo(subjectYearTwo.getId());
        assertThat(year2Summary.percentage()).isEqualByComparingTo(bd("80.00"));
        assertThat(year2Summary.grade()).isEqualTo("A");

        // CGPA over BOTH years: (4*10 + 1*7 + 3*8) / 8 = 71/8 = 8.875 -> HALF_UP 8.88.
        assertThat(full.totalGradedCredits()).isEqualTo(8);
        assertThat(full.cgpa()).isEqualByComparingTo(bd("8.88"));

        // Buckets do not bleed: filtering to year 1 never surfaces the year-2 subject,
        // and vice versa.
        AcademicResultResponse year1Only = mySummary(studentToken, academicYear1, null);
        assertThat(year1Only.semesters()).hasSize(1);
        assertThat(year1Only.semesters().get(0).subjects())
                .noneMatch(s -> s.subjectId().equals(subjectYearTwo.getId()));

        AcademicResultResponse year2Only = mySummary(studentToken, academicYear2, null);
        assertThat(year2Only.semesters()).hasSize(1);
        assertThat(year2Only.semesters().get(0).subjects())
                .allMatch(s -> s.subjectId().equals(subjectYearTwo.getId()));
    }

    // ------------------------------------------------------------------
    // (3): zero marks rows -> null, never 0.00.
    // ------------------------------------------------------------------

    @Test
    void zeroMarksRows_producesNullPercentageGradeAndGpa_neverZero() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Student student = persistActiveStudent(department, course, 1, "A");
        // Deliberately no exam, no marks, no enrollment even attempted.

        String studentToken = loginAsStudent(student);
        AcademicResultResponse result = mySummary(studentToken, null, null);

        assertThat(result.semesters()).isEmpty();
        assertThat(result.totalGradedCredits()).isZero();
        assertThat(result.cgpa()).isNull();
    }

    // ------------------------------------------------------------------
    // (4): a boundary percentage maps to the expected band, and changing the band
    // through the admin API changes the resulting grade for the SAME marks — proof
    // nothing is hard-coded (G7).
    // ------------------------------------------------------------------

    @Test
    void boundaryPercentageMapsToExpectedBand_andAdminBandChangeChangesTheGrade() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subjectAtLowerBoundOfA = persistSubject(course, 4, 1);
        Subject subjectAtUpperBoundOfBPlus = persistSubject(course, 4, 1);
        String academicYear = "2025-2026";
        String section = "A";

        Student studentA = persistActiveStudent(department, course, 1, section);
        Student studentBPlus = persistActiveStudent(department, course, 1, section);
        enroll(studentA, subjectAtLowerBoundOfA, academicYear, 1, section);
        enroll(studentBPlus, subjectAtUpperBoundOfBPlus, academicYear, 1, section);

        String admin = adminToken();

        ExamResponse examA =
                createExam(
                        admin, subjectAtLowerBoundOfA.getId(), academicYear, 1, section, ExamType.SEMESTER,
                        "Boundary Exam A", bd("100.00"));
        ExamResponse examBPlus =
                createExam(
                        admin, subjectAtUpperBoundOfBPlus.getId(), academicYear, 1, section, ExamType.SEMESTER,
                        "Boundary Exam BPlus", bd("100.00"));

        // 71.00 is the seeded lower boundary of "A" (71.00-80.99); 70.99 is the seeded
        // upper boundary of "B+" (61.00-70.99). Both are inclusive edges.
        enterMarks(admin, examA.id(), List.of(new MarksEntry(studentA.getId(), bd("71.00"), null)));
        enterMarks(admin, examBPlus.id(), List.of(new MarksEntry(studentBPlus.getId(), bd("70.99"), null)));

        String studentAToken = loginAsStudent(studentA);
        String studentBPlusToken = loginAsStudent(studentBPlus);

        SubjectGradeSummary boundaryA = onlySubject(mySummary(studentAToken, academicYear, 1));
        assertThat(boundaryA.percentage()).isEqualByComparingTo(bd("71.00"));
        assertThat(boundaryA.grade()).isEqualTo("A");
        assertThat(boundaryA.gradePoint()).isEqualByComparingTo(bd("8.00"));

        SubjectGradeSummary boundaryBPlus = onlySubject(mySummary(studentBPlusToken, academicYear, 1));
        assertThat(boundaryBPlus.percentage()).isEqualByComparingTo(bd("70.99"));
        assertThat(boundaryBPlus.grade()).isEqualTo("B+");

        // Now prove nothing is hard-coded: an admin edits the "A" band's grade point
        // through the real admin API, and the SAME marks (never re-entered) report the
        // new grade point on the very next read.
        List<GradeBandResponse> bands = listGradeBands(admin);
        GradeBandResponse originalBandA =
                bands.stream().filter(b -> b.grade().equals("A")).findFirst().orElseThrow();
        assertThat(originalBandA.gradePoint()).isEqualByComparingTo(bd("8.00"));

        try {
            GradeBandRequest mutated =
                    new GradeBandRequest(
                            originalBandA.grade(),
                            originalBandA.minPercentage(),
                            originalBandA.maxPercentage(),
                            bd("8.50"),
                            originalBandA.passGrade(),
                            originalBandA.description());
            putGradeBand(admin, originalBandA.id(), mutated);

            SubjectGradeSummary afterMutation = onlySubject(mySummary(studentAToken, academicYear, 1));
            assertThat(afterMutation.percentage()).isEqualByComparingTo(bd("71.00"));
            assertThat(afterMutation.grade()).isEqualTo("A");
            assertThat(afterMutation.gradePoint()).isEqualByComparingTo(bd("8.50"));
            assertThat(afterMutation.gradePoint()).isNotEqualByComparingTo(bd("8.00"));
        } finally {
            // Restore the shared seed data so no other test in this class (or class run
            // in the same Spring context) observes the mutated grade point.
            putGradeBand(admin, originalBandA.id(), asRequest(originalBandA));
        }

        SubjectGradeSummary afterRestore = onlySubject(mySummary(studentAToken, academicYear, 1));
        assertThat(afterRestore.gradePoint()).isEqualByComparingTo(bd("8.00"));
    }

    // ------------------------------------------------------------------
    // (5): marks validation the database cannot enforce.
    // ------------------------------------------------------------------

    @Test
    void marksValidation_aboveMaximum400_belowZero400_loweringMaximumBelowExistingMark409() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subject = persistSubject(course, 4, 1);
        String academicYear = "2025-2026";
        String section = "A";
        Student student = persistActiveStudent(department, course, 1, section);
        enroll(student, subject, academicYear, 1, section);

        String admin = adminToken();
        ExamResponse exam =
                createExam(
                        admin, subject.getId(), academicYear, 1, section, ExamType.SEMESTER, "Validation Exam",
                        bd("100.00"));

        // marksObtained > maximumMarks -> 400 (cross-table condition the schema cannot enforce).
        assertThat(
                        expectMarksBulkStatus(
                                admin, exam.id(), List.of(new MarksEntry(student.getId(), bd("150.00"), null))))
                .isEqualTo(400);

        // marksObtained < 0 -> 400 (bean validation on MarksEntry.marksObtained).
        assertThat(
                        expectMarksBulkStatus(
                                admin, exam.id(), List.of(new MarksEntry(student.getId(), bd("-5.00"), null))))
                .isEqualTo(400);

        // A valid mark is recorded...
        enterMarks(admin, exam.id(), List.of(new MarksEntry(student.getId(), bd("90.00"), null)));

        // ...and now lowering the exam's maximum below that recorded mark is a 409, not
        // a silent corruption of the "marksObtained <= maximumMarks" invariant.
        ExamUpdateRequest lowered =
                new ExamUpdateRequest(exam.title(), exam.examType(), exam.examDate(), bd("50.00"), exam.status());
        mockMvc.perform(
                        put("/api/exams/" + exam.id())
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(lowered)))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------
    // (6): a CANCELLED exam's marks do not contribute to the subject grade.
    // ------------------------------------------------------------------

    @Test
    void cancelledExamMarksAreExcludedFromTheSubjectGrade() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subject = persistSubject(course, 4, 1);
        String academicYear = "2025-2026";
        String section = "A";
        Student student = persistActiveStudent(department, course, 1, section);
        enroll(student, subject, academicYear, 1, section);

        String admin = adminToken();

        ExamResponse countedExam =
                createExam(
                        admin, subject.getId(), academicYear, 1, section, ExamType.INTERNAL_1, "Counted Exam",
                        bd("100.00"));
        ExamResponse cancelledExam =
                createExam(
                        admin, subject.getId(), academicYear, 1, section, ExamType.INTERNAL_2, "Cancelled Exam",
                        bd("100.00"));

        enterMarks(admin, countedExam.id(), List.of(new MarksEntry(student.getId(), bd("60.00"), null)));
        // A very high score on the exam that will be cancelled — if it were still
        // counted, the subject percentage would jump to 80.00% (A), not stay at 60.00%
        // (B). That visible difference is the proof of exclusion.
        enterMarks(admin, cancelledExam.id(), List.of(new MarksEntry(student.getId(), bd("100.00"), null)));

        ExamUpdateRequest cancel =
                new ExamUpdateRequest(
                        cancelledExam.title(),
                        cancelledExam.examType(),
                        cancelledExam.examDate(),
                        cancelledExam.maximumMarks(),
                        ExamStatus.CANCELLED);
        mockMvc.perform(
                        put("/api/exams/" + cancelledExam.id())
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(cancel)))
                .andExpect(status().isOk());

        String studentToken = loginAsStudent(student);
        SubjectGradeSummary summary = onlySubject(mySummary(studentToken, academicYear, 1));

        assertThat(summary.examCount()).isEqualTo(1);
        assertThat(summary.totalObtained()).isEqualByComparingTo(bd("60.00"));
        assertThat(summary.totalMaximum()).isEqualByComparingTo(bd("100.00"));
        assertThat(summary.percentage()).isEqualByComparingTo(bd("60.00"));
        assertThat(summary.grade()).isEqualTo("B");
    }

    // ------------------------------------------------------------------
    // (7): security — assignment is exact-tuple, and a STUDENT can never write or read
    // another student's marks.
    // ------------------------------------------------------------------

    @Test
    void facultyAssignmentIsExactTuple_studentCanNeverWriteOrReadAnotherStudentsMarks() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subject = persistSubject(course, 4, 1);
        String academicYear = "2025-2026";
        String otherAcademicYear = "2026-2027";
        String sectionA = "A";
        String sectionB = "B";

        Faculty facultyA = persistActiveFaculty(department);
        assign(facultyA, subject, academicYear, 1, sectionA);
        String facultyAToken = loginAsFaculty(facultyA);

        // Assigned to section A only -> 403 creating an exam for section B of the same
        // subject/year/semester.
        assertThat(
                        expectExamCreateStatus(
                                facultyAToken, subject.getId(), academicYear, 1, sectionB, ExamType.QUIZ,
                                "Section B Quiz", bd("50.00")))
                .isEqualTo(403);

        // Assigned to 2025-2026 only -> 403 for the identical section in a different
        // academic year.
        assertThat(
                        expectExamCreateStatus(
                                facultyAToken, subject.getId(), otherAcademicYear, 1, sectionA, ExamType.QUIZ,
                                "Wrong Year Quiz", bd("50.00")))
                .isEqualTo(403);

        // An ADMIN creates a real exam in section B; faculty A (assigned only to
        // section A) is denied entering marks against it, even though it is the same
        // subject.
        String admin = adminToken();
        Student studentInSectionB = persistActiveStudent(department, course, 1, sectionB);
        enroll(studentInSectionB, subject, academicYear, 1, sectionB);
        ExamResponse sectionBExam =
                createExam(
                        admin, subject.getId(), academicYear, 1, sectionB, ExamType.QUIZ, "Admin Section B Quiz",
                        bd("50.00"));
        assertThat(
                        expectMarksBulkStatus(
                                facultyAToken,
                                sectionBExam.id(),
                                List.of(new MarksEntry(studentInSectionB.getId(), bd("40.00"), null))))
                .isEqualTo(403);

        // A STUDENT token is denied on every write this module exposes.
        Student plainStudent = persistActiveStudent(department, course, 1, sectionA);
        enroll(plainStudent, subject, academicYear, 1, sectionA);
        String studentToken = loginAsStudent(plainStudent);

        assertThat(
                        expectExamCreateStatus(
                                studentToken, subject.getId(), academicYear, 1, sectionA, ExamType.QUIZ,
                                "Student Attempt", bd("50.00")))
                .isEqualTo(403);

        ExamResponse sectionAExam =
                createExam(
                        admin, subject.getId(), academicYear, 1, sectionA, ExamType.QUIZ, "Admin Section A Quiz",
                        bd("50.00"));
        assertThat(
                        expectMarksBulkStatus(
                                studentToken,
                                sectionAExam.id(),
                                List.of(new MarksEntry(plainStudent.getId(), bd("40.00"), null))))
                .isEqualTo(403);

        // A STUDENT cannot read another student's graded record via the admin-only
        // by-id summary route.
        int status =
                mockMvc.perform(
                                get("/api/marks/summary/" + studentInSectionB.getId())
                                        .header("Authorization", "Bearer " + studentToken))
                        .andReturn()
                        .getResponse()
                        .getStatus();
        assertThat(status).isEqualTo(403);
    }

    // ------------------------------------------------------------------
    // Closes the other two dimensions of the (subject, year, semester, section) tuple
    // that {@link #facultyAssignmentIsExactTuple_studentCanNeverWriteOrReadAnotherStudentsMarks}
    // does not exercise: a DIFFERENT SUBJECT entirely, and the same subject/year/section
    // in a DIFFERENT SEMESTER. Then proves marks entry against the exact assigned tuple
    // succeeds, so the denials are the guard discriminating correctly rather than the
    // endpoint being broken for every faculty caller.
    // ------------------------------------------------------------------

    @Test
    void examAndMarksWrites_deniedForWrongSubjectAndWrongSemester_thenAllowedForExactTuple() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject assignedSubject = persistSubject(course, 4, 1);
        Subject otherSubject = persistSubject(course, 3, 1);
        String academicYear = "2025-2026";
        int assignedSemester = 1;
        String section = "A";

        Faculty faculty = persistActiveFaculty(department);
        assign(faculty, assignedSubject, academicYear, assignedSemester, section);
        String facultyToken = loginAsFaculty(faculty);

        // A DIFFERENT SUBJECT entirely, same year/semester/section -> 403.
        assertThat(
                        expectExamCreateStatus(
                                facultyToken,
                                otherSubject.getId(),
                                academicYear,
                                assignedSemester,
                                section,
                                ExamType.QUIZ,
                                "Wrong Subject Quiz",
                                bd("50.00")))
                .isEqualTo(403);

        // Same subject/year/section, WRONG SEMESTER -> 403.
        assertThat(
                        expectExamCreateStatus(
                                facultyToken,
                                assignedSubject.getId(),
                                academicYear,
                                assignedSemester + 1,
                                section,
                                ExamType.QUIZ,
                                "Wrong Semester Quiz",
                                bd("50.00")))
                .isEqualTo(403);

        // An ADMIN creates a real exam under the wrong-subject tuple; faculty (assigned
        // only to assignedSubject) is denied entering marks against it.
        String admin = adminToken();
        Student studentOnOtherSubject = persistActiveStudent(department, course, assignedSemester, section);
        enroll(studentOnOtherSubject, otherSubject, academicYear, assignedSemester, section);
        ExamResponse otherSubjectExam =
                createExam(
                        admin, otherSubject.getId(), academicYear, assignedSemester, section, ExamType.QUIZ,
                        "Admin Other Subject Quiz", bd("50.00"));
        assertThat(
                        expectMarksBulkStatus(
                                facultyToken,
                                otherSubjectExam.id(),
                                List.of(new MarksEntry(studentOnOtherSubject.getId(), bd("30.00"), null))))
                .isEqualTo(403);

        // The exact assigned tuple is allowed end to end: create the exam, then enter
        // marks against it.
        Student enrolledStudent = persistActiveStudent(department, course, assignedSemester, section);
        enroll(enrolledStudent, assignedSubject, academicYear, assignedSemester, section);
        ExamResponse ownExam =
                createExam(
                        facultyToken, assignedSubject.getId(), academicYear, assignedSemester, section,
                        ExamType.QUIZ, "Own Quiz", bd("50.00"));
        MarksBulkResponse response =
                enterMarks(
                        facultyToken,
                        ownExam.id(),
                        List.of(new MarksEntry(enrolledStudent.getId(), bd("42.00"), null)));
        assertThat(response.createdCount()).isEqualTo(1);
    }
}
