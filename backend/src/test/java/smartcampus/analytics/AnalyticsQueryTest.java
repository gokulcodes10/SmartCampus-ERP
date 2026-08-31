package smartcampus.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import smartcampus.TestcontainersConfiguration;
import smartcampus.entity.Attendance;
import smartcampus.entity.AttendanceStatus;
import smartcampus.entity.Course;
import smartcampus.entity.Department;
import smartcampus.entity.Enrollment;
import smartcampus.entity.EnrollmentStatus;
import smartcampus.entity.Exam;
import smartcampus.entity.ExamStatus;
import smartcampus.entity.ExamType;
import smartcampus.entity.Faculty;
import smartcampus.entity.FacultyStatus;
import smartcampus.entity.Marks;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.Subject;
import smartcampus.entity.User;
import smartcampus.repository.AnalyticsAttendanceRepository;
import smartcampus.repository.AnalyticsCohortRepository;
import smartcampus.repository.AnalyticsMarksRepository;
import smartcampus.repository.AttendanceRepository;
import smartcampus.repository.CourseRepository;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.EnrollmentRepository;
import smartcampus.repository.ExamRepository;
import smartcampus.repository.FacultyRepository;
import smartcampus.repository.MarksRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.SubjectRepository;
import smartcampus.repository.UserRepository;
import smartcampus.repository.projection.AttendanceTrendTotals;
import smartcampus.repository.projection.ExamMarksTotals;
import smartcampus.repository.projection.MarksTrendTotals;
import smartcampus.repository.projection.StudentSubjectAttendanceTotals;
import smartcampus.repository.projection.StudentSubjectMarksTotals;

/**
 * Proves that every JPQL string in {@link AnalyticsAttendanceRepository}, {@link
 * AnalyticsMarksRepository} and {@link AnalyticsCohortRepository} actually PARSES
 * against a real MySQL 8.4 schema (Testcontainers, real Flyway migrations, no H2 — a
 * broken {@code @Query} only fails at context startup or at first call) and computes the
 * correct numbers, in particular the G6 (held/attended) and G7 (cancelled-exam)
 * exclusion rules that must come from {@link AttendanceStatus} / {@link ExamStatus}
 * alone.
 *
 * <p>This is a repository-level test: it persists fixtures directly through the
 * existing {@code JpaRepository}s (no HTTP, no auth, no service layer) because the
 * classes under test expose only reads.
 *
 * <p>Every fixture code/email is derived from a per-JVM {@link AtomicInteger} prefixed
 * with {@code "AN"} (Analytics), never {@code System.nanoTime()} — PROJECT_PLAN.md
 * documents a real duplicate-key flake caused by exactly that pattern, and Spring's
 * TestContext framework caches and reuses a single ApplicationContext (and so a single
 * Testcontainers MySQL instance) across every test class in the suite with a matching
 * {@code @Import(TestcontainersConfiguration.class) @SpringBootTest} signature — so the
 * prefix also keeps this class's rows distinguishable from sibling checkpoint classes'
 * rows in the SAME physical tables. Because of that sharing, whole-table aggregates
 * ({@code countStudents()}, {@code countFaculty()}, ...) are asserted as a DELTA over a
 * baseline captured before this class's fixtures are inserted, never as an absolute
 * value.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AnalyticsQueryTest {

    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private FacultyRepository facultyRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private ExamRepository examRepository;
    @Autowired private MarksRepository marksRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Autowired private AnalyticsAttendanceRepository analyticsAttendanceRepository;
    @Autowired private AnalyticsMarksRepository analyticsMarksRepository;
    @Autowired private AnalyticsCohortRepository analyticsCohortRepository;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String PREFIX = "AN";
    private static final String RAW_PASSWORD = "AnalyticsPass1!";

    private static String tag() {
        return PREFIX + SEQUENCE.incrementAndGet();
    }

    // ------------------------------------------------------------------
    // Fixture helpers
    // ------------------------------------------------------------------

    private Department persistDepartment() {
        String t = tag();
        return departmentRepository.save(Department.builder().code(t).name(PREFIX + " Dept " + t).build());
    }

    private Course persistCourse(Department department) {
        String t = tag();
        return courseRepository.save(
                Course.builder().code(t).name(PREFIX + " Course " + t).department(department).build());
    }

    private Subject persistSubject(Course course, int credits, int syllabusSemester) {
        String t = tag();
        return subjectRepository.save(
                Subject.builder()
                        .code(t)
                        .name(PREFIX + " Subject " + t)
                        .credits(credits)
                        .semester(syllabusSemester)
                        .course(course)
                        .build());
    }

    private User persistUser(String kind, Role role) {
        String t = tag();
        return userRepository.save(
                User.builder()
                        .email((kind + t).toLowerCase() + "@example.com")
                        .password(passwordEncoder.encode(RAW_PASSWORD))
                        .fullName(PREFIX + " " + kind + " " + t)
                        .role(role)
                        .build());
    }

    private Student persistActiveStudent(Department department, Course course, int semester, String section) {
        String t = tag();
        User user = persistUser("student", Role.STUDENT);
        return studentRepository.save(
                Student.builder()
                        .user(user)
                        .registerNumber(t)
                        .department(department)
                        .course(course)
                        .currentSemester(semester)
                        .section(section)
                        .admissionYear(2025)
                        .status(StudentStatus.ACTIVE)
                        .build());
    }

    /**
     * A student with NO department — proves the analytics repositories' left join. The
     * {@code chk_students_active_requires_assignment} CHECK constraint (V3__academic.sql,
     * clarification G1) forbids an ACTIVE student from having a null department, so this
     * uses PENDING with every admin-assigned field null, exactly the shape
     * self-registration leaves a student in before an admin activates them. The subject's
     * OWN course (via {@code a.subject.course}), not the student's, is what the analytics
     * queries project as courseId, so a null student.course does not blank that column.
     */
    private Student persistPendingStudentWithoutDepartment() {
        String t = tag();
        User user = persistUser("student", Role.STUDENT);
        return studentRepository.save(
                Student.builder()
                        .user(user)
                        .registerNumber(null)
                        .department(null)
                        .course(null)
                        .currentSemester(null)
                        .section(null)
                        .admissionYear(2025)
                        .status(StudentStatus.PENDING)
                        .build());
    }

    private Faculty persistActiveFaculty(Department department) {
        String t = tag();
        User user = persistUser("faculty", Role.FACULTY);
        return facultyRepository.save(
                Faculty.builder()
                        .user(user)
                        .employeeCode(t)
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

    private Exam persistExam(
            Subject subject,
            String academicYear,
            int semester,
            String section,
            ExamType type,
            LocalDate date,
            BigDecimal maximumMarks,
            ExamStatus status) {
        String t = tag();
        return examRepository.save(
                Exam.builder()
                        .subject(subject)
                        .title(PREFIX + " Exam " + t)
                        .examType(type)
                        .academicYear(academicYear)
                        .semester(semester)
                        .section(section)
                        .examDate(date)
                        .maximumMarks(maximumMarks)
                        .status(status)
                        .build());
    }

    private void awardMarks(Exam exam, Student student, BigDecimal obtained) {
        marksRepository.save(Marks.builder().exam(exam).student(student).marksObtained(obtained).build());
    }

    // ------------------------------------------------------------------
    // Attendance: trendByStudent — month bucketing + the G6 rule, proven through the
    // default overload (so the assertion proves the rule comes from AttendanceStatus).
    // ------------------------------------------------------------------

    @Test
    void attendanceTrendByStudent_bucketsByMonthAscending_andAppliesG6ThroughDefaultOverload() {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subject = persistSubject(course, 4, 1);
        Student student = persistActiveStudent(department, course, 1, "A");
        String year = "2025-2026";
        int semester = 1;
        String section = "A";
        enroll(student, subject, year, semester, section);

        // August 2025: PRESENT, ABSENT, LATE, ON_DUTY, CANCELLED -> held=4, attended=3
        mark(student, subject, year, semester, section, LocalDate.of(2025, 8, 1), 1, AttendanceStatus.PRESENT);
        mark(student, subject, year, semester, section, LocalDate.of(2025, 8, 2), 1, AttendanceStatus.ABSENT);
        mark(student, subject, year, semester, section, LocalDate.of(2025, 8, 3), 1, AttendanceStatus.LATE);
        mark(student, subject, year, semester, section, LocalDate.of(2025, 8, 4), 1, AttendanceStatus.ON_DUTY);
        mark(student, subject, year, semester, section, LocalDate.of(2025, 8, 5), 1, AttendanceStatus.CANCELLED);

        // September 2025: PRESENT, ABSENT -> held=2, attended=1
        mark(student, subject, year, semester, section, LocalDate.of(2025, 9, 1), 1, AttendanceStatus.PRESENT);
        mark(student, subject, year, semester, section, LocalDate.of(2025, 9, 2), 1, AttendanceStatus.ABSENT);

        List<AttendanceTrendTotals> trend =
                analyticsAttendanceRepository.trendByStudent(
                        student.getId(), LocalDate.of(2025, 8, 1), null, null);

        assertThat(trend).hasSize(2);
        AttendanceTrendTotals august = trend.get(0);
        assertThat(august.getPeriodYear()).isEqualTo(2025);
        assertThat(august.getPeriodMonth()).isEqualTo(8);
        assertThat(august.getHeldClasses()).isEqualTo(4L);
        assertThat(august.getAttendedClasses()).isEqualTo(3L);

        AttendanceTrendTotals september = trend.get(1);
        assertThat(september.getPeriodYear()).isEqualTo(2025);
        assertThat(september.getPeriodMonth()).isEqualTo(9);
        assertThat(september.getHeldClasses()).isEqualTo(2L);
        assertThat(september.getAttendedClasses()).isEqualTo(1L);

        // Optional academicYear/semester filters narrow correctly.
        List<AttendanceTrendTotals> filtered =
                analyticsAttendanceRepository.trendByStudent(
                        student.getId(), LocalDate.of(2025, 8, 1), year, semester);
        assertThat(filtered).hasSize(2);

        List<AttendanceTrendTotals> wrongYear =
                analyticsAttendanceRepository.trendByStudent(
                        student.getId(), LocalDate.of(2025, 8, 1), "2099-2100", null);
        assertThat(wrongYear).isEmpty();
    }

    // ------------------------------------------------------------------
    // Attendance: trendByScope — scope-filtered, optional filters null, section filter.
    // ------------------------------------------------------------------

    @Test
    void attendanceTrendByScope_aggregatesAcrossSubjectScope_withOptionalFilters() {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subject = persistSubject(course, 4, 1);
        Student studentA = persistActiveStudent(department, course, 1, "A");
        Student studentB = persistActiveStudent(department, course, 1, "A");
        String year = "2025-2026";
        int semester = 1;
        String section = "A";
        enroll(studentA, subject, year, semester, section);
        enroll(studentB, subject, year, semester, section);

        mark(studentA, subject, year, semester, section, LocalDate.of(2025, 8, 1), 1, AttendanceStatus.PRESENT);
        mark(studentB, subject, year, semester, section, LocalDate.of(2025, 8, 1), 2, AttendanceStatus.ABSENT);

        List<AttendanceTrendTotals> allFiltersNull =
                analyticsAttendanceRepository.trendByScope(
                        List.of(subject.getId()), LocalDate.of(2025, 8, 1), null, null, null);
        assertThat(allFiltersNull).hasSize(1);
        assertThat(allFiltersNull.get(0).getHeldClasses()).isEqualTo(2L);
        assertThat(allFiltersNull.get(0).getAttendedClasses()).isEqualTo(1L);

        List<AttendanceTrendTotals> wrongSection =
                analyticsAttendanceRepository.trendByScope(
                        List.of(subject.getId()), LocalDate.of(2025, 8, 1), year, semester, "Z");
        assertThat(wrongSection).isEmpty();
    }

    // ------------------------------------------------------------------
    // Attendance: cohortSubjectTotals — all-cancelled subject still appears with
    // held=0/attended=0 (never a fabricated denominator) and cancelledClasses > 0; a
    // student with a NULL department still appears via the left join.
    // ------------------------------------------------------------------

    @Test
    void attendanceCohortSubjectTotals_allCancelledSubjectAndNullDepartment() {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subject = persistSubject(course, 3, 1);
        String year = "2025-2026";
        int semester = 1;
        String section = "A";

        Student withDept = persistActiveStudent(department, course, 1, section);
        Student withoutDept = persistPendingStudentWithoutDepartment();
        enroll(withDept, subject, year, semester, section);
        enroll(withoutDept, subject, year, semester, section);

        // withDept: all three sessions CANCELLED.
        mark(withDept, subject, year, semester, section, LocalDate.of(2025, 8, 1), 1, AttendanceStatus.CANCELLED);
        mark(withDept, subject, year, semester, section, LocalDate.of(2025, 8, 2), 1, AttendanceStatus.CANCELLED);
        mark(withDept, subject, year, semester, section, LocalDate.of(2025, 8, 3), 1, AttendanceStatus.CANCELLED);

        // withoutDept: one PRESENT, department must come back null.
        mark(withoutDept, subject, year, semester, section, LocalDate.of(2025, 8, 1), 2, AttendanceStatus.PRESENT);

        List<StudentSubjectAttendanceTotals> rows =
                analyticsAttendanceRepository.cohortSubjectTotals(
                        List.of(subject.getId()), year, semester, section);

        assertThat(rows).hasSize(2);

        StudentSubjectAttendanceTotals cancelledRow =
                rows.stream().filter(r -> r.getStudentId().equals(withDept.getId())).findFirst().orElseThrow();
        assertThat(cancelledRow.getHeldClasses()).isEqualTo(0L);
        assertThat(cancelledRow.getAttendedClasses()).isEqualTo(0L);
        assertThat(cancelledRow.getCancelledClasses()).isEqualTo(3L);
        assertThat(cancelledRow.getTotalRecords()).isEqualTo(3L);
        assertThat(cancelledRow.getDepartmentId()).isEqualTo(department.getId());

        StudentSubjectAttendanceTotals noDeptRow =
                rows.stream().filter(r -> r.getStudentId().equals(withoutDept.getId())).findFirst().orElseThrow();
        assertThat(noDeptRow.getDepartmentId()).isNull();
        assertThat(noDeptRow.getDepartmentCode()).isNull();
        assertThat(noDeptRow.getDepartmentName()).isNull();
        assertThat(noDeptRow.getHeldClasses()).isEqualTo(1L);
        assertThat(noDeptRow.getAttendedClasses()).isEqualTo(1L);
        assertThat(noDeptRow.getCourseId()).isEqualTo(course.getId());
        assertThat(noDeptRow.getSubjectId()).isEqualTo(subject.getId());
    }

    // ------------------------------------------------------------------
    // Marks: trendByStudent / trendByScope — month bucketing, and a CANCELLED exam
    // contributes nothing.
    // ------------------------------------------------------------------

    @Test
    void marksTrend_bucketsByMonth_andExcludesCancelledExam() {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subject = persistSubject(course, 4, 1);
        Student student = persistActiveStudent(department, course, 1, "A");
        String year = "2025-2026";
        int semester = 1;
        String section = "A";
        enroll(student, subject, year, semester, section);

        Exam augustExam =
                persistExam(
                        subject,
                        year,
                        semester,
                        section,
                        ExamType.INTERNAL_1,
                        LocalDate.of(2025, 8, 10),
                        new BigDecimal("100.00"),
                        ExamStatus.COMPLETED);
        Exam septemberExam =
                persistExam(
                        subject,
                        year,
                        semester,
                        section,
                        ExamType.INTERNAL_2,
                        LocalDate.of(2025, 9, 10),
                        new BigDecimal("50.00"),
                        ExamStatus.COMPLETED);
        Exam cancelledExam =
                persistExam(
                        subject,
                        year,
                        semester,
                        section,
                        ExamType.INTERNAL_3,
                        LocalDate.of(2025, 8, 20),
                        new BigDecimal("100.00"),
                        ExamStatus.CANCELLED);

        awardMarks(augustExam, student, new BigDecimal("80.00"));
        awardMarks(septemberExam, student, new BigDecimal("40.00"));
        awardMarks(cancelledExam, student, new BigDecimal("90.00"));

        List<MarksTrendTotals> trend =
                analyticsMarksRepository.trendByStudent(
                        student.getId(), LocalDate.of(2025, 8, 1), null, null);

        assertThat(trend).hasSize(2);
        MarksTrendTotals august = trend.get(0);
        assertThat(august.getPeriodYear()).isEqualTo(2025);
        assertThat(august.getPeriodMonth()).isEqualTo(8);
        assertThat(august.getExamCount()).isEqualTo(1L);
        assertThat(august.getTotalObtained()).isEqualByComparingTo("80.00");
        assertThat(august.getTotalMaximum()).isEqualByComparingTo("100.00");

        MarksTrendTotals september = trend.get(1);
        assertThat(september.getPeriodMonth()).isEqualTo(9);
        assertThat(september.getExamCount()).isEqualTo(1L);
        assertThat(september.getTotalObtained()).isEqualByComparingTo("40.00");
        assertThat(september.getTotalMaximum()).isEqualByComparingTo("50.00");

        List<MarksTrendTotals> byScope =
                analyticsMarksRepository.trendByScope(
                        List.of(subject.getId()), LocalDate.of(2025, 8, 1), null, null, null);
        assertThat(byScope).hasSize(2);
        assertThat(byScope.stream().mapToLong(MarksTrendTotals::getExamCount).sum()).isEqualTo(2L);
    }

    // ------------------------------------------------------------------
    // Marks: cohortSubjectTotals + examTotals — a CANCELLED exam contributes nothing to
    // either; totals sum correctly across students; highest/lowest are correct.
    // ------------------------------------------------------------------

    @Test
    void marksCohortSubjectTotalsAndExamTotals_excludeCancelledExam() {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subject = persistSubject(course, 4, 1);
        String year = "2025-2026";
        int semester = 1;
        String section = "A";
        Student studentA = persistActiveStudent(department, course, 1, section);
        Student studentB = persistActiveStudent(department, course, 1, section);
        enroll(studentA, subject, year, semester, section);
        enroll(studentB, subject, year, semester, section);

        Exam completedExam =
                persistExam(
                        subject,
                        year,
                        semester,
                        section,
                        ExamType.INTERNAL_1,
                        LocalDate.of(2025, 8, 10),
                        new BigDecimal("100.00"),
                        ExamStatus.COMPLETED);
        Exam cancelledExam =
                persistExam(
                        subject,
                        year,
                        semester,
                        section,
                        ExamType.INTERNAL_2,
                        LocalDate.of(2025, 8, 15),
                        new BigDecimal("100.00"),
                        ExamStatus.CANCELLED);

        awardMarks(completedExam, studentA, new BigDecimal("80.00"));
        awardMarks(completedExam, studentB, new BigDecimal("60.00"));
        awardMarks(cancelledExam, studentA, new BigDecimal("99.00"));

        List<StudentSubjectMarksTotals> cohort =
                analyticsMarksRepository.cohortSubjectTotals(List.of(subject.getId()), year, semester, section);
        assertThat(cohort).hasSize(2);

        StudentSubjectMarksTotals rowA =
                cohort.stream().filter(r -> r.getStudentId().equals(studentA.getId())).findFirst().orElseThrow();
        assertThat(rowA.getExamCount()).isEqualTo(1L);
        assertThat(rowA.getTotalObtained()).isEqualByComparingTo("80.00");
        assertThat(rowA.getTotalMaximum()).isEqualByComparingTo("100.00");

        List<ExamMarksTotals> exams =
                analyticsMarksRepository.examTotals(List.of(subject.getId()), year, semester, section);
        assertThat(exams).hasSize(1); // the cancelled exam never appears
        ExamMarksTotals examRow = exams.get(0);
        assertThat(examRow.getExamId()).isEqualTo(completedExam.getId());
        assertThat(examRow.getMarksEnteredCount()).isEqualTo(2L);
        assertThat(examRow.getTotalObtained()).isEqualByComparingTo("140.00");
        assertThat(examRow.getHighestObtained()).isEqualByComparingTo("80.00");
        assertThat(examRow.getLowestObtained()).isEqualByComparingTo("60.00");
    }

    // ------------------------------------------------------------------
    // AnalyticsCohortRepository: subject-id resolution and distinct filter lists.
    // ------------------------------------------------------------------

    @Test
    void cohortRepository_findSubjectIdsAndDistinctFilterLists() {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subjectOne = persistSubject(course, 4, 1);
        Subject subjectTwo = persistSubject(course, 3, 2);

        Department otherDepartment = persistDepartment();
        Course otherCourse = persistCourse(otherDepartment);
        Subject otherSubject = persistSubject(otherCourse, 4, 1);

        List<Long> byCourse = analyticsCohortRepository.findSubjectIds(course.getId(), null);
        assertThat(byCourse).containsExactlyInAnyOrder(subjectOne.getId(), subjectTwo.getId());
        assertThat(byCourse).doesNotContain(otherSubject.getId());

        List<Long> byDepartment = analyticsCohortRepository.findSubjectIds(null, department.getId());
        assertThat(byDepartment).containsExactlyInAnyOrder(subjectOne.getId(), subjectTwo.getId());

        List<Long> unfiltered = analyticsCohortRepository.findSubjectIds(null, null);
        assertThat(unfiltered)
                .contains(subjectOne.getId(), subjectTwo.getId(), otherSubject.getId());

        Student student = persistActiveStudent(department, course, 1, "AN-SEC");
        String year = "2031-2032"; // improbable year, unlikely to collide with other tests
        enroll(student, subjectOne, year, 5, "AN-SEC");

        assertThat(analyticsCohortRepository.findDistinctAcademicYears()).contains(year);
        assertThat(analyticsCohortRepository.findDistinctSemesters()).contains(5);
        assertThat(analyticsCohortRepository.findDistinctSections()).contains("AN-SEC");
    }

    // ------------------------------------------------------------------
    // AnalyticsCohortRepository: counts, asserted as a delta over a baseline since the
    // Testcontainers database is shared across the whole test suite.
    // ------------------------------------------------------------------

    @Test
    void cohortRepository_countsReflectNewlyInsertedRows() {
        long studentsBefore = analyticsCohortRepository.countStudents();
        long activeBefore = analyticsCohortRepository.countByStatus(StudentStatus.ACTIVE);
        long facultyBefore = analyticsCohortRepository.countFaculty();
        long activeFacultyBefore = analyticsCohortRepository.countFacultyByStatus(FacultyStatus.ACTIVE);
        long departmentsBefore = analyticsCohortRepository.countDepartments();
        long coursesBefore = analyticsCohortRepository.countCourses();
        long subjectsBefore = analyticsCohortRepository.countSubjects();
        long examsBefore = analyticsCohortRepository.countExams(ExamStatus.CANCELLED);

        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subject = persistSubject(course, 4, 1);
        persistActiveStudent(department, course, 1, "A");
        persistActiveFaculty(department);
        persistExam(
                subject,
                "2025-2026",
                1,
                "A",
                ExamType.QUIZ,
                LocalDate.of(2025, 8, 1),
                new BigDecimal("20.00"),
                ExamStatus.COMPLETED);
        // A CANCELLED exam must NOT move countExams (it is excluded via the parameter,
        // same as every other Phase 5 grading aggregate).
        persistExam(
                subject,
                "2025-2026",
                1,
                "A",
                ExamType.QUIZ,
                LocalDate.of(2025, 8, 2),
                new BigDecimal("20.00"),
                ExamStatus.CANCELLED);

        assertThat(analyticsCohortRepository.countStudents()).isEqualTo(studentsBefore + 1);
        assertThat(analyticsCohortRepository.countByStatus(StudentStatus.ACTIVE)).isEqualTo(activeBefore + 1);
        assertThat(analyticsCohortRepository.countFaculty()).isEqualTo(facultyBefore + 1);
        assertThat(analyticsCohortRepository.countFacultyByStatus(FacultyStatus.ACTIVE))
                .isEqualTo(activeFacultyBefore + 1);
        assertThat(analyticsCohortRepository.countDepartments()).isEqualTo(departmentsBefore + 1);
        assertThat(analyticsCohortRepository.countCourses()).isEqualTo(coursesBefore + 1);
        assertThat(analyticsCohortRepository.countSubjects()).isEqualTo(subjectsBefore + 1);
        assertThat(analyticsCohortRepository.countExams(ExamStatus.CANCELLED)).isEqualTo(examsBefore + 1);
    }
}
