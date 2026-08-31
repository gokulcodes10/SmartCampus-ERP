package smartcampus.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import smartcampus.dto.AnalyticsStudentResponse;
import smartcampus.dto.EligibilityReason;
import smartcampus.dto.EligibilityReasonCode;
import smartcampus.dto.JobEligibilityResponse;
import smartcampus.entity.Company;
import smartcampus.entity.CompanyStatus;
import smartcampus.entity.Course;
import smartcampus.entity.Department;
import smartcampus.entity.Job;
import smartcampus.entity.JobEligibleDepartment;
import smartcampus.entity.JobStatus;
import smartcampus.entity.JobType;
import smartcampus.entity.PlacementApplication;
import smartcampus.entity.ApplicationStatus;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.User;
import smartcampus.repository.JobEligibleDepartmentRepository;
import smartcampus.repository.JobRepository;
import smartcampus.repository.PlacementApplicationRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.service.AnalyticsService;
import smartcampus.service.JobService;
import smartcampus.service.PlacementEligibilityService;

/**
 * Fast, no-database pinning of {@link PlacementEligibilityService}'s pure §34 decision
 * logic against CONTRACT section 5 verbatim: the graduation-year derivation, the
 * null-vs-0.00 {@code minCgpa} distinction, boundary equality ({@code >=} not {@code
 * >}), the criterion-vs-blocker split, the exact reason ORDER, and the exact message
 * text for all eleven {@link EligibilityReasonCode} constants.
 *
 * <p><b>IMPORTANT — read before trusting a failure here.</b> {@code
 * PlacementEligibilityService} is owned and written concurrently by a sibling build
 * task; this file was authored strictly against the CONTRACT, which fixes the class's
 * public methods ({@code evaluate}, {@code eligibleStudents}) but does NOT fix its
 * constructor. Rather than guess a parameter order that is certain to be wrong the
 * moment the real class exists, {@link #newService()} below builds the service
 * reflectively: it finds the (single, widest) declared constructor, supplies the
 * Mockito mocks below for every parameter type this test recognizes ({@link JobService},
 * {@link StudentRepository}, {@link JobEligibleDepartmentRepository}, {@link
 * PlacementApplicationRepository}, {@link AnalyticsService}), and falls back to a bare
 * {@code Mockito.mock(type)} (which answers empty {@code Optional}/collections by
 * default) for anything else it does not recognize. If the real constructor needs a
 * collaborator this test cannot stub meaningfully (e.g. it loads the {@link Job} via a
 * repository this file never anticipated), tests here will compile but some may fail
 * with a null/empty result rather than the crafted fixture — that is a test-harness gap
 * to fix by adding the missing type to {@link #newService()}, not a production defect.
 *
 * <p>The eligibility inputs (CGPA, marks percentage) are Mockito stubs on {@link
 * AnalyticsService}, matching the contract note "Stub AnalyticsService to return a
 * crafted AnalyticsStudentResponse" — this file never recomputes CGPA itself.
 */
@ExtendWith(MockitoExtension.class)
class PlacementEligibilityRulesTest {

    @Mock private JobService jobService;
    @Mock private StudentRepository studentRepository;
    @Mock private JobEligibleDepartmentRepository jobEligibleDepartmentRepository;
    @Mock private PlacementApplicationRepository placementApplicationRepository;
    @Mock private AnalyticsService analyticsService;
    @Mock private smartcampus.service.ScopedWriteAuthorizer scopedWriteAuthorizer;

    // ------------------------------------------------------------------
    // Reflective construction — see class javadoc.
    // ------------------------------------------------------------------

    private PlacementEligibilityService newService() {
        Constructor<?>[] ctors = PlacementEligibilityService.class.getDeclaredConstructors();
        Constructor<?> chosen = ctors[0];
        for (Constructor<?> c : ctors) {
            if (c.getParameterCount() > chosen.getParameterCount()) {
                chosen = c;
            }
        }
        chosen.setAccessible(true);
        Class<?>[] types = chosen.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i] == JobService.class) {
                args[i] = jobService;
            } else if (types[i] == StudentRepository.class) {
                args[i] = studentRepository;
            } else if (types[i] == JobEligibleDepartmentRepository.class) {
                args[i] = jobEligibleDepartmentRepository;
            } else if (types[i] == PlacementApplicationRepository.class) {
                args[i] = placementApplicationRepository;
            } else if (types[i] == AnalyticsService.class) {
                args[i] = analyticsService;
            } else if (types[i] == smartcampus.service.ScopedWriteAuthorizer.class) {
                args[i] = scopedWriteAuthorizer;
            } else if (types[i] == JobRepository.class) {
                // Defensive: some designs load the Job directly rather than through
                // JobService.loadVisibleJob. Not stubbed by default — individual tests
                // that need it must stub jobRepository.findById themselves via this mock.
                args[i] = mock(JobRepository.class);
            } else {
                args[i] = mock(types[i]);
            }
        }
        try {
            return (PlacementEligibilityService) chosen.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not reflectively construct PlacementEligibilityService", e);
        }
    }

    // ------------------------------------------------------------------
    // Fixture builders — plain in-memory entities, never persisted.
    // ------------------------------------------------------------------

    private static final String RAW_PASSWORD = "irrelevant";

    private Department department(long id, String code) {
        return Department.builder().id(id).code(code).name(code + " Dept").build();
    }

    private Course course(long id, Department dept, int durationSemesters) {
        return Course.builder().id(id).code("CRS" + id).name("Course " + id).department(dept)
                .durationSemesters(durationSemesters).build();
    }

    private User adminUser(long id) {
        return User.builder().id(id).email("admin" + id + "@example.com").password(RAW_PASSWORD)
                .fullName("Admin " + id).role(Role.ADMIN).build();
    }

    private User studentUser(long id) {
        return User.builder().id(id).email("stu" + id + "@example.com").password(RAW_PASSWORD)
                .fullName("Student " + id).role(Role.STUDENT).build();
    }

    private Student student(
            long id, Department dept, Course course, Integer admissionYear, StudentStatus status) {
        return Student.builder()
                .id(id)
                .user(studentUser(id))
                .registerNumber("REG" + id)
                .department(dept)
                .course(course)
                .currentSemester(5)
                .section("A")
                .admissionYear(admissionYear)
                .status(status)
                .build();
    }

    private Company company(long id) {
        return Company.builder().id(id).name("Company " + id).status(CompanyStatus.ACTIVE).build();
    }

    private Job job(long id, Company c, User postedBy, JobStatus status, LocalDateTime deadline) {
        return Job.builder()
                .id(id)
                .company(c)
                .title("Role " + id)
                .jobType(JobType.FULL_TIME)
                .salaryCurrency("INR")
                .status(status)
                .applicationDeadline(deadline)
                .postedBy(postedBy)
                .build();
    }

    private AnalyticsStudentResponse analyticsFor(Student s, BigDecimal cgpa, BigDecimal marksPercentage) {
        Long deptId = s.getDepartment() != null ? s.getDepartment().getId() : null;
        String deptName = s.getDepartment() != null ? s.getDepartment().getName() : null;
        Long courseId = s.getCourse() != null ? s.getCourse().getId() : null;
        String courseName = s.getCourse() != null ? s.getCourse().getName() : null;
        return new AnalyticsStudentResponse(
                s.getId(),
                s.getRegisterNumber(),
                s.getUser().getFullName(),
                deptId,
                deptName,
                courseId,
                courseName,
                s.getCurrentSemester(),
                s.getSection(),
                null,
                null,
                0,
                null,
                null,
                marksPercentage,
                null,
                null,
                cgpa,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    /** Wires the self-path: evaluate(jobId, null, studentCaller) -> analyticsService.myAnalytics(...). */
    private JobEligibilityResponse evaluateSelf(
            Job job, Student student, BigDecimal cgpa, BigDecimal marksPercentage, PlacementEligibilityService svc) {
        when(jobService.loadVisibleJob(any(), any())).thenReturn(job);
        when(scopedWriteAuthorizer.requireOwnStudent(any())).thenReturn(student);
        when(analyticsService.myAnalytics(any(), any(), any(), any()))
                .thenReturn(analyticsFor(student, cgpa, marksPercentage));
        return svc.evaluate(job.getId(), null, student.getUser());
    }

    private List<EligibilityReasonCode> codesOf(JobEligibilityResponse r) {
        return r.reasons().stream().map(EligibilityReason::code).toList();
    }

    private boolean hasCode(JobEligibilityResponse r, EligibilityReasonCode code) {
        return codesOf(r).contains(code);
    }

    private String messageFor(JobEligibilityResponse r, EligibilityReasonCode code) {
        return r.reasons().stream()
                .filter(reason -> reason.code() == code)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No reason with code " + code + " in " + r.reasons()))
                .message();
    }

    // ------------------------------------------------------------------
    // 1. Graduation-year derivation
    // ------------------------------------------------------------------

    @Test
    void graduationYear_eightSemesters_admitted2022_derivesTo2026() {
        Department dept = department(1, "CSE");
        Course course = course(1, dept, 8);
        Student student = student(1, dept, course, 2022, StudentStatus.ACTIVE);
        Job job = job(1, company(1), adminUser(99), JobStatus.OPEN, LocalDateTime.now().plusDays(10));
        job.setGraduationYear(2026); // matches -> no mismatch reason, and studentGraduationYear must read 2026

        when(jobEligibleDepartmentRepository.findByJobId(any())).thenReturn(List.of());
        when(placementApplicationRepository.findByJobIdAndStudentId(any(), any())).thenReturn(java.util.Optional.empty());

        JobEligibilityResponse r = evaluateSelf(job, student, null, null, newService());

        assertThat(r.studentGraduationYear()).isEqualTo(2026);
        assertThat(hasCode(r, EligibilityReasonCode.GRADUATION_YEAR_MISMATCH)).isFalse();
        assertThat(hasCode(r, EligibilityReasonCode.GRADUATION_YEAR_UNKNOWN)).isFalse();
    }

    @Test
    void graduationYear_sixSemesters_admitted2022_derivesTo2025() {
        Department dept = department(2, "ECE");
        Course course = course(2, dept, 6);
        Student student = student(2, dept, course, 2022, StudentStatus.ACTIVE);
        Job job = job(2, company(2), adminUser(99), JobStatus.OPEN, LocalDateTime.now().plusDays(10));
        job.setGraduationYear(2025);

        when(jobEligibleDepartmentRepository.findByJobId(any())).thenReturn(List.of());
        when(placementApplicationRepository.findByJobIdAndStudentId(any(), any())).thenReturn(java.util.Optional.empty());

        JobEligibilityResponse r = evaluateSelf(job, student, null, null, newService());

        assertThat(r.studentGraduationYear()).isEqualTo(2025);
        assertThat(hasCode(r, EligibilityReasonCode.GRADUATION_YEAR_MISMATCH)).isFalse();
    }

    @Test
    void graduationYear_nullAdmissionYear_isUnknown_reportsExactMessage() {
        Department dept = department(3, "CSE");
        Course course = course(3, dept, 8);
        Student student = student(3, dept, course, null, StudentStatus.ACTIVE);
        Job job = job(3, company(3), adminUser(99), JobStatus.OPEN, LocalDateTime.now().plusDays(10));
        job.setGraduationYear(2026);

        when(jobEligibleDepartmentRepository.findByJobId(any())).thenReturn(List.of());
        when(placementApplicationRepository.findByJobIdAndStudentId(any(), any())).thenReturn(java.util.Optional.empty());

        JobEligibilityResponse r = evaluateSelf(job, student, null, null, newService());

        assertThat(r.studentGraduationYear()).isNull();
        assertThat(hasCode(r, EligibilityReasonCode.GRADUATION_YEAR_UNKNOWN)).isTrue();
        assertThat(messageFor(r, EligibilityReasonCode.GRADUATION_YEAR_UNKNOWN))
                .isEqualTo(
                        "Your graduation year cannot be determined because your admission year or course is not set"
                                + " on your profile. Contact the administration office.");
        assertThat(hasCode(r, EligibilityReasonCode.GRADUATION_YEAR_MISMATCH)).isFalse();
    }

    @Test
    void graduationYear_nullCourse_isUnknown() {
        Department dept = department(4, "CSE");
        Student student = student(4, dept, null, 2022, StudentStatus.ACTIVE);
        Job job = job(4, company(4), adminUser(99), JobStatus.OPEN, LocalDateTime.now().plusDays(10));
        job.setGraduationYear(2026);

        when(jobEligibleDepartmentRepository.findByJobId(any())).thenReturn(List.of());
        when(placementApplicationRepository.findByJobIdAndStudentId(any(), any())).thenReturn(java.util.Optional.empty());

        JobEligibilityResponse r = evaluateSelf(job, student, null, null, newService());

        assertThat(r.studentGraduationYear()).isNull();
        assertThat(hasCode(r, EligibilityReasonCode.GRADUATION_YEAR_UNKNOWN)).isTrue();
    }

    @Test
    void graduationYear_mismatch_exactMessage() {
        Department dept = department(5, "CSE");
        Course course = course(5, dept, 8);
        Student student = student(5, dept, course, 2022, StudentStatus.ACTIVE);
        Job job = job(5, company(5), adminUser(99), JobStatus.OPEN, LocalDateTime.now().plusDays(10));
        job.setGraduationYear(2025); // student derives 2026 -> mismatch

        when(jobEligibleDepartmentRepository.findByJobId(any())).thenReturn(List.of());
        when(placementApplicationRepository.findByJobIdAndStudentId(any(), any())).thenReturn(java.util.Optional.empty());

        JobEligibilityResponse r = evaluateSelf(job, student, null, null, newService());

        assertThat(hasCode(r, EligibilityReasonCode.GRADUATION_YEAR_MISMATCH)).isTrue();
        assertThat(messageFor(r, EligibilityReasonCode.GRADUATION_YEAR_MISMATCH))
                .isEqualTo("This drive is open to the 2025 graduating batch. You graduate in 2026.");
    }

    // ------------------------------------------------------------------
    // 2. null-vs-0.00 minCgpa distinction, and boundary equality (>=, not >)
    // ------------------------------------------------------------------

    @Test
    void minCgpaZero_studentWithNoGradedSubjects_isNotEligible_cgpaNotAvailable() {
        Department dept = department(6, "CSE");
        Course course = course(6, dept, 8);
        Student student = student(6, dept, course, 2022, StudentStatus.ACTIVE);
        Job job = job(6, company(6), adminUser(99), JobStatus.OPEN, LocalDateTime.now().plusDays(10));
        job.setMinCgpa(new BigDecimal("0.00"));

        when(jobEligibleDepartmentRepository.findByJobId(any())).thenReturn(List.of());
        when(placementApplicationRepository.findByJobIdAndStudentId(any(), any())).thenReturn(java.util.Optional.empty());

        JobEligibilityResponse r = evaluateSelf(job, student, null, null, newService());

        assertThat(r.eligible()).isFalse();
        assertThat(hasCode(r, EligibilityReasonCode.CGPA_NOT_AVAILABLE)).isTrue();
        assertThat(messageFor(r, EligibilityReasonCode.CGPA_NOT_AVAILABLE))
                .isEqualTo(
                        "This drive requires a minimum CGPA of 0.00, but you have no graded subjects yet, so your"
                                + " CGPA cannot be computed.");
    }

    @Test
    void minCgpaNull_studentWithNoGradedSubjects_hasNoCgpaReasonAtAll() {
        Department dept = department(7, "CSE");
        Course course = course(7, dept, 8);
        Student student = student(7, dept, course, 2022, StudentStatus.ACTIVE);
        Job job = job(7, company(7), adminUser(99), JobStatus.OPEN, LocalDateTime.now().plusDays(10));
        // minCgpa left null -> "no requirement", must NOT be treated as CGPA_NOT_AVAILABLE.

        when(jobEligibleDepartmentRepository.findByJobId(any())).thenReturn(List.of());
        when(placementApplicationRepository.findByJobIdAndStudentId(any(), any())).thenReturn(java.util.Optional.empty());

        JobEligibilityResponse r = evaluateSelf(job, student, null, null, newService());

        assertThat(hasCode(r, EligibilityReasonCode.CGPA_NOT_AVAILABLE)).isFalse();
        assertThat(hasCode(r, EligibilityReasonCode.CGPA_BELOW_MINIMUM)).isFalse();
        assertThat(r.eligible()).isTrue();
    }

    @Test
    void cgpaExactlyEqualToMinimum_isEligible_notBelowMinimum() {
        Department dept = department(8, "CSE");
        Course course = course(8, dept, 8);
        Student student = student(8, dept, course, 2022, StudentStatus.ACTIVE);
        Job job = job(8, company(8), adminUser(99), JobStatus.OPEN, LocalDateTime.now().plusDays(10));
        job.setMinCgpa(new BigDecimal("7.00"));

        when(jobEligibleDepartmentRepository.findByJobId(any())).thenReturn(List.of());
        when(placementApplicationRepository.findByJobIdAndStudentId(any(), any())).thenReturn(java.util.Optional.empty());

        JobEligibilityResponse r = evaluateSelf(job, student, new BigDecimal("7.00"), null, newService());

        assertThat(hasCode(r, EligibilityReasonCode.CGPA_BELOW_MINIMUM)).isFalse();
        assertThat(r.eligible()).isTrue();
    }

    @Test
    void cgpaBelowMinimum_exactMessage() {
        Department dept = department(9, "CSE");
        Course course = course(9, dept, 8);
        Student student = student(9, dept, course, 2022, StudentStatus.ACTIVE);
        Job job = job(9, company(9), adminUser(99), JobStatus.OPEN, LocalDateTime.now().plusDays(10));
        job.setMinCgpa(new BigDecimal("9.00"));

        when(jobEligibleDepartmentRepository.findByJobId(any())).thenReturn(List.of());
        when(placementApplicationRepository.findByJobIdAndStudentId(any(), any())).thenReturn(java.util.Optional.empty());

        JobEligibilityResponse r = evaluateSelf(job, student, new BigDecimal("5.00"), null, newService());

        assertThat(hasCode(r, EligibilityReasonCode.CGPA_BELOW_MINIMUM)).isTrue();
        assertThat(messageFor(r, EligibilityReasonCode.CGPA_BELOW_MINIMUM))
                .isEqualTo("This drive requires a minimum CGPA of 9.00. Yours is 5.00.");
        assertThat(r.eligible()).isFalse();
    }

    @Test
    void percentageExactlyEqualToMinimum_isEligible_andBelowMinimumExactMessage() {
        Department dept = department(10, "CSE");
        Course course = course(10, dept, 8);
        Student atBoundary = student(10, dept, course, 2022, StudentStatus.ACTIVE);
        Job jobBoundary = job(10, company(10), adminUser(99), JobStatus.OPEN, LocalDateTime.now().plusDays(10));
        jobBoundary.setMinMarksPercentage(new BigDecimal("75.00"));

        when(jobEligibleDepartmentRepository.findByJobId(any())).thenReturn(List.of());
        when(placementApplicationRepository.findByJobIdAndStudentId(any(), any())).thenReturn(java.util.Optional.empty());

        JobEligibilityResponse rBoundary =
                evaluateSelf(jobBoundary, atBoundary, null, new BigDecimal("75.00"), newService());
        assertThat(hasCode(rBoundary, EligibilityReasonCode.PERCENTAGE_BELOW_MINIMUM)).isFalse();

        Department dept2 = department(11, "CSE");
        Course course2 = course(11, dept2, 8);
        Student below = student(11, dept2, course2, 2022, StudentStatus.ACTIVE);
        Job jobBelow = job(11, company(11), adminUser(99), JobStatus.OPEN, LocalDateTime.now().plusDays(10));
        jobBelow.setMinMarksPercentage(new BigDecimal("75.00"));

        JobEligibilityResponse rBelow = evaluateSelf(jobBelow, below, null, new BigDecimal("60.00"), newService());
        assertThat(hasCode(rBelow, EligibilityReasonCode.PERCENTAGE_BELOW_MINIMUM)).isTrue();
        assertThat(messageFor(rBelow, EligibilityReasonCode.PERCENTAGE_BELOW_MINIMUM))
                .isEqualTo("This drive requires a minimum aggregate of 75.00%. Yours is 60.00%.");
    }

    @Test
    void percentageNotAvailable_exactMessage() {
        Department dept = department(12, "CSE");
        Course course = course(12, dept, 8);
        Student student = student(12, dept, course, 2022, StudentStatus.ACTIVE);
        Job job = job(12, company(12), adminUser(99), JobStatus.OPEN, LocalDateTime.now().plusDays(10));
        job.setMinMarksPercentage(new BigDecimal("75.00"));

        when(jobEligibleDepartmentRepository.findByJobId(any())).thenReturn(List.of());
        when(placementApplicationRepository.findByJobIdAndStudentId(any(), any())).thenReturn(java.util.Optional.empty());

        JobEligibilityResponse r = evaluateSelf(job, student, null, null, newService());

        assertThat(hasCode(r, EligibilityReasonCode.PERCENTAGE_NOT_AVAILABLE)).isTrue();
        assertThat(messageFor(r, EligibilityReasonCode.PERCENTAGE_NOT_AVAILABLE))
                .isEqualTo(
                        "This drive requires a minimum aggregate of 75.00%, but you have no marks recorded yet, so"
                                + " your percentage cannot be computed.");
    }

    // ------------------------------------------------------------------
    // 3. Department restriction: exact message + "not set" wording + empty-set-means-all
    // ------------------------------------------------------------------

    @Test
    void departmentRestricted_studentOutside_exactMessage_andEmptySetMeansAllDepartments() {
        Department cse = department(13, "CSE");
        Department ece = department(14, "ECE");
        Course course = course(13, cse, 8);
        Student student = student(13, cse, course, 2022, StudentStatus.ACTIVE);
        Job job = job(13, company(13), adminUser(99), JobStatus.OPEN, LocalDateTime.now().plusDays(10));

        JobEligibleDepartment restriction = JobEligibleDepartment.builder().id(1L).job(job).department(ece).build();
        when(jobEligibleDepartmentRepository.findByJobId(any())).thenReturn(List.of(restriction));
        when(placementApplicationRepository.findByJobIdAndStudentId(any(), any())).thenReturn(java.util.Optional.empty());

        JobEligibilityResponse r = evaluateSelf(job, student, null, null, newService());

        assertThat(hasCode(r, EligibilityReasonCode.DEPARTMENT_NOT_ELIGIBLE)).isTrue();
        assertThat(messageFor(r, EligibilityReasonCode.DEPARTMENT_NOT_ELIGIBLE))
                .isEqualTo("This drive is open to ECE Dept only. Your department is CSE Dept.");
        assertThat(r.eligible()).isFalse();

        // Converse: zero rows -> every department, including this same student's, is eligible.
        Job openJob = job(14, company(14), adminUser(99), JobStatus.OPEN, LocalDateTime.now().plusDays(10));
        when(jobEligibleDepartmentRepository.findByJobId(any())).thenReturn(List.of());
        JobEligibilityResponse r2 = evaluateSelf(openJob, student, null, null, newService());
        assertThat(hasCode(r2, EligibilityReasonCode.DEPARTMENT_NOT_ELIGIBLE)).isFalse();
    }

    @Test
    void departmentRestricted_studentWithNoDepartment_reportsNotSet() {
        Department ece = department(15, "ECE");
        Course course = course(15, ece, 8);
        Student student = student(16, null, course, 2022, StudentStatus.ACTIVE);
        Job job = job(15, company(15), adminUser(99), JobStatus.OPEN, LocalDateTime.now().plusDays(10));

        JobEligibleDepartment restriction = JobEligibleDepartment.builder().id(2L).job(job).department(ece).build();
        when(jobEligibleDepartmentRepository.findByJobId(any())).thenReturn(List.of(restriction));
        when(placementApplicationRepository.findByJobIdAndStudentId(any(), any())).thenReturn(java.util.Optional.empty());

        JobEligibilityResponse r = evaluateSelf(job, student, null, null, newService());

        assertThat(messageFor(r, EligibilityReasonCode.DEPARTMENT_NOT_ELIGIBLE))
                .isEqualTo("This drive is open to ECE Dept only. Your department is not set.");
    }

    // ------------------------------------------------------------------
    // 4. PROFILE_NOT_ACTIVE exact message
    // ------------------------------------------------------------------

    @Test
    void pendingStudent_profileNotActive_exactMessage() {
        Department dept = department(17, "CSE");
        Course course = course(17, dept, 8);
        Student student = student(17, dept, course, 2022, StudentStatus.PENDING);
        Job job = job(16, company(16), adminUser(99), JobStatus.OPEN, LocalDateTime.now().plusDays(10));

        when(jobEligibleDepartmentRepository.findByJobId(any())).thenReturn(List.of());
        when(placementApplicationRepository.findByJobIdAndStudentId(any(), any())).thenReturn(java.util.Optional.empty());

        JobEligibilityResponse r = evaluateSelf(job, student, null, null, newService());

        assertThat(hasCode(r, EligibilityReasonCode.PROFILE_NOT_ACTIVE)).isTrue();
        assertThat(messageFor(r, EligibilityReasonCode.PROFILE_NOT_ACTIVE))
                .isEqualTo("Your student profile is PENDING, not ACTIVE. Only active students can apply to placement drives.");
        assertThat(r.eligible()).isFalse();
    }

    // ------------------------------------------------------------------
    // 5. Blockers (DRIVE_NOT_OPEN, DEADLINE_PASSED, ALREADY_APPLIED) never flip `eligible`
    // ------------------------------------------------------------------

    @Test
    void driveNotOpen_isBlockerOnly_eligibleStaysTrue_canApplyFalse() {
        Department dept = department(18, "CSE");
        Course course = course(18, dept, 8);
        Student student = student(18, dept, course, 2022, StudentStatus.ACTIVE);
        Job job = job(17, company(17), adminUser(99), JobStatus.CLOSED, LocalDateTime.now().plusDays(10));

        when(jobEligibleDepartmentRepository.findByJobId(any())).thenReturn(List.of());
        when(placementApplicationRepository.findByJobIdAndStudentId(any(), any())).thenReturn(java.util.Optional.empty());

        JobEligibilityResponse r = evaluateSelf(job, student, null, null, newService());

        assertThat(r.eligible()).isTrue();
        assertThat(r.canApply()).isFalse();
        assertThat(hasCode(r, EligibilityReasonCode.DRIVE_NOT_OPEN)).isTrue();
        assertThat(messageFor(r, EligibilityReasonCode.DRIVE_NOT_OPEN))
                .isEqualTo("This drive is CLOSED and is not accepting applications.");
    }

    @Test
    void deadlinePassed_isBlockerOnly_exactMessage_andInclusiveBoundary() {
        Department dept = department(19, "CSE");
        Course course = course(19, dept, 8);
        Student student = student(19, dept, course, 2022, StudentStatus.ACTIVE);
        LocalDateTime pastDeadline = LocalDateTime.now().minusDays(3);
        Job job = job(18, company(18), adminUser(99), JobStatus.OPEN, pastDeadline);

        when(jobEligibleDepartmentRepository.findByJobId(any())).thenReturn(List.of());
        when(placementApplicationRepository.findByJobIdAndStudentId(any(), any())).thenReturn(java.util.Optional.empty());

        JobEligibilityResponse r = evaluateSelf(job, student, null, null, newService());

        assertThat(r.eligible()).isTrue();
        assertThat(r.canApply()).isFalse();
        assertThat(hasCode(r, EligibilityReasonCode.DEADLINE_PASSED)).isTrue();
        assertThat(messageFor(r, EligibilityReasonCode.DEADLINE_PASSED))
                .isEqualTo(
                        "The application deadline for this drive passed on "
                                + pastDeadline.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                + ".");

        // Boundary: deadline == now is still open (inclusive), never DEADLINE_PASSED.
        // We cannot pin "now" exactly against the service's own LocalDateTime.now() call,
        // so instead we assert the more permissive, still-meaningful half of the boundary:
        // a deadline a few seconds in the FUTURE must never be reported as passed.
        Department dept2 = department(20, "CSE");
        Course course2 = course(20, dept2, 8);
        Student student2 = student(20, dept2, course2, 2022, StudentStatus.ACTIVE);
        Job openJob = job(19, company(19), adminUser(99), JobStatus.OPEN, LocalDateTime.now().plusSeconds(30));
        JobEligibilityResponse rOpen = evaluateSelf(openJob, student2, null, null, newService());
        assertThat(hasCode(rOpen, EligibilityReasonCode.DEADLINE_PASSED)).isFalse();
        assertThat(rOpen.canApply()).isTrue();
    }

    @Test
    void alreadyApplied_isBlockerOnly_exactMessage() {
        Department dept = department(21, "CSE");
        Course course = course(21, dept, 8);
        Student student = student(21, dept, course, 2022, StudentStatus.ACTIVE);
        Job job = job(20, company(20), adminUser(99), JobStatus.OPEN, LocalDateTime.now().plusDays(10));

        LocalDateTime appliedAt = LocalDateTime.of(2026, 1, 15, 9, 30);
        PlacementApplication existing =
                PlacementApplication.builder()
                        .id(500L)
                        .job(job)
                        .student(student)
                        .status(ApplicationStatus.APPLIED)
                        .appliedAt(appliedAt)
                        .build();

        when(jobEligibleDepartmentRepository.findByJobId(any())).thenReturn(List.of());
        when(placementApplicationRepository.findByJobIdAndStudentId(any(), any())).thenReturn(java.util.Optional.of(existing));

        JobEligibilityResponse r = evaluateSelf(job, student, null, null, newService());

        assertThat(r.eligible()).isTrue();
        assertThat(r.canApply()).isFalse();
        assertThat(hasCode(r, EligibilityReasonCode.ALREADY_APPLIED)).isTrue();
        assertThat(messageFor(r, EligibilityReasonCode.ALREADY_APPLIED))
                .isEqualTo("You have already applied to this drive on " + LocalDate.of(2026, 1, 15) + ".");
    }

    // ------------------------------------------------------------------
    // 6. Multiple reasons at once, in the CONTRACT's exact order — no short-circuiting.
    // ------------------------------------------------------------------

    @Test
    void multipleFailingCriteria_reportsAllOfThem_inContractOrder() {
        Department cse = department(22, "CSE");
        Department ece = department(23, "ECE");
        Course course = course(22, cse, 8);
        // Fails: PROFILE_NOT_ACTIVE, DEPARTMENT_NOT_ELIGIBLE, GRADUATION_YEAR_MISMATCH,
        // CGPA_BELOW_MINIMUM, DRIVE_NOT_OPEN, DEADLINE_PASSED, ALREADY_APPLIED.
        Student student = student(22, cse, course, 2022, StudentStatus.PENDING); // -> 2026 derived
        LocalDateTime pastDeadline = LocalDateTime.now().minusDays(1);
        Job job = job(21, company(21), adminUser(99), JobStatus.DRAFT, pastDeadline);
        job.setGraduationYear(2025); // mismatch vs derived 2026
        job.setMinCgpa(new BigDecimal("9.00"));

        JobEligibleDepartment restriction = JobEligibleDepartment.builder().id(3L).job(job).department(ece).build();
        when(jobEligibleDepartmentRepository.findByJobId(any())).thenReturn(List.of(restriction));

        PlacementApplication existing =
                PlacementApplication.builder()
                        .id(600L)
                        .job(job)
                        .student(student)
                        .status(ApplicationStatus.APPLIED)
                        .appliedAt(LocalDateTime.now().minusDays(5))
                        .build();
        when(placementApplicationRepository.findByJobIdAndStudentId(any(), any())).thenReturn(java.util.Optional.of(existing));

        JobEligibilityResponse r = evaluateSelf(job, student, new BigDecimal("5.00"), null, newService());

        assertThat(codesOf(r))
                .containsExactly(
                        EligibilityReasonCode.PROFILE_NOT_ACTIVE,
                        EligibilityReasonCode.DEPARTMENT_NOT_ELIGIBLE,
                        EligibilityReasonCode.GRADUATION_YEAR_MISMATCH,
                        EligibilityReasonCode.CGPA_BELOW_MINIMUM,
                        EligibilityReasonCode.DRIVE_NOT_OPEN,
                        EligibilityReasonCode.DEADLINE_PASSED,
                        EligibilityReasonCode.ALREADY_APPLIED);
        assertThat(r.eligible()).isFalse(); // criterion codes present
        assertThat(r.canApply()).isFalse();
    }
}
