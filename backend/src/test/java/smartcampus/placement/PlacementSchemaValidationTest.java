package smartcampus.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import smartcampus.TestcontainersConfiguration;
import smartcampus.entity.ApplicationStatus;
import smartcampus.entity.Company;
import smartcampus.entity.CompanyStatus;
import smartcampus.entity.Course;
import smartcampus.entity.Department;
import smartcampus.entity.Job;
import smartcampus.entity.JobEligibleDepartment;
import smartcampus.entity.JobStatus;
import smartcampus.entity.JobType;
import smartcampus.entity.PlacementApplication;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.User;
import smartcampus.repository.CompanyRepository;
import smartcampus.repository.CourseRepository;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.JobEligibleDepartmentRepository;
import smartcampus.repository.JobRepository;
import smartcampus.repository.PlacementApplicationRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.UserRepository;

/**
 * Real-MySQL, no-MockMvc proof that every Phase 8 entity mapping matches {@code
 * V8__placement.sql} exactly (the {@code ddl-auto=validate} check — trap 2 in the
 * shared agent brief, "the single most common way this build breaks") and that every
 * CHECK / UNIQUE / FK constraint the migration declares is actually reachable and
 * enforced from the JPA layer, not merely present in the SQL file.
 *
 * <p>{@code @SpringBootTest} alone is the {@code ddl-auto=validate} proof: Hibernate
 * validates every {@code @Entity} mapping against the live schema while the context
 * starts, and refuses to boot on any drift. If this class's context fails to start,
 * that IS a schema/entity mismatch — read the boot failure for the offending column.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PlacementSchemaValidationTest {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private JobEligibleDepartmentRepository jobEligibleDepartmentRepository;
    @Autowired private PlacementApplicationRepository placementApplicationRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String PREFIX = "PLS"; // Placement Schema
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
                Course.builder()
                        .code(PREFIX + "C" + t)
                        .name(PREFIX + " Course " + t)
                        .department(department)
                        .durationSemesters(8)
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

    private Student persistActiveStudent(Department department, Course course) {
        String t = tag();
        User user = persistUser("student", Role.STUDENT);
        return studentRepository.save(
                Student.builder()
                        .user(user)
                        .registerNumber(PREFIX + "REG" + t)
                        .department(department)
                        .course(course)
                        .currentSemester(5)
                        .section("A")
                        .admissionYear(2022)
                        .status(StudentStatus.ACTIVE)
                        .build());
    }

    private Company persistCompany() {
        String t = tag();
        return companyRepository.save(
                Company.builder().name(PREFIX + " Company " + t).status(CompanyStatus.ACTIVE).build());
    }

    /** A fully valid job — every constraint-testing method mutates ONE field off of this baseline. */
    private Job validJobBuilder(Company company, User postedBy, String title, LocalDateTime deadline) {
        return Job.builder()
                .company(company)
                .title(title)
                .jobType(JobType.FULL_TIME)
                .salaryCurrency("INR")
                .status(JobStatus.DRAFT)
                .applicationDeadline(deadline)
                .postedBy(postedBy)
                .build();
    }

    // ------------------------------------------------------------------
    // 1. Context loads at all == every Phase 8 entity mapping matches V8's DDL.
    // ------------------------------------------------------------------

    @Test
    void contextLoads_everyPlacementRepositoryIsWired() {
        assertThat(companyRepository).isNotNull();
        assertThat(jobRepository).isNotNull();
        assertThat(jobEligibleDepartmentRepository).isNotNull();
        assertThat(placementApplicationRepository).isNotNull();
    }

    @Test
    void validJob_savesCleanly_provingTheBaselineMappingItself_isCorrect() {
        Company company = persistCompany();
        User admin = persistUser("admin", Role.ADMIN);
        Job job =
                validJobBuilder(company, admin, PREFIX + " Valid Role " + tag(), LocalDateTime.now().plusDays(30));
        Job saved = jobRepository.saveAndFlush(job);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSalaryCurrency()).isEqualTo("INR");
        assertThat(saved.getStatus()).isEqualTo(JobStatus.DRAFT);
    }

    // ------------------------------------------------------------------
    // 2. chk_jobs_min_cgpa_range
    // ------------------------------------------------------------------

    @Test
    void job_minCgpaAboveTen_violatesCheckConstraint() {
        Company company = persistCompany();
        User admin = persistUser("admin", Role.ADMIN);
        Job job = validJobBuilder(company, admin, PREFIX + " MinCgpa " + tag(), LocalDateTime.now().plusDays(30));
        job.setMinCgpa(new BigDecimal("11.00"));

        assertThatThrownBy(() -> jobRepository.saveAndFlush(job))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // 3. chk_jobs_salary_range
    // ------------------------------------------------------------------

    @Test
    void job_salaryMaxBelowSalaryMin_violatesCheckConstraint() {
        Company company = persistCompany();
        User admin = persistUser("admin", Role.ADMIN);
        Job job = validJobBuilder(company, admin, PREFIX + " SalaryRange " + tag(), LocalDateTime.now().plusDays(30));
        job.setSalaryMin(new BigDecimal("800000.00"));
        job.setSalaryMax(new BigDecimal("400000.00"));

        assertThatThrownBy(() -> jobRepository.saveAndFlush(job))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // 4. chk_jobs_salary_currency — deliberately case-sensitive (COLLATE utf8mb4_bin)
    // ------------------------------------------------------------------

    @Test
    void job_lowercaseSalaryCurrency_violatesCaseSensitiveCheckConstraint() {
        Company company = persistCompany();
        User admin = persistUser("admin", Role.ADMIN);
        Job job = validJobBuilder(company, admin, PREFIX + " Currency " + tag(), LocalDateTime.now().plusDays(30));
        job.setSalaryCurrency("inr");

        assertThatThrownBy(() -> jobRepository.saveAndFlush(job))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // 5. chk_jobs_drive_after_deadline
    // ------------------------------------------------------------------

    @Test
    void job_driveDateBeforeDeadlineDate_violatesCheckConstraint() {
        Company company = persistCompany();
        User admin = persistUser("admin", Role.ADMIN);
        LocalDateTime deadline = LocalDateTime.of(2027, 3, 10, 10, 0);
        Job job = validJobBuilder(company, admin, PREFIX + " DriveDate " + tag(), deadline);
        job.setDriveDate(LocalDate.of(2027, 3, 5)); // before deadline's date

        assertThatThrownBy(() -> jobRepository.saveAndFlush(job))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // 6. uk_job_eligible_departments_job_department
    // ------------------------------------------------------------------

    @Test
    void duplicateJobDepartmentRow_violatesUniqueConstraint() {
        Company company = persistCompany();
        User admin = persistUser("admin", Role.ADMIN);
        Job job =
                jobRepository.saveAndFlush(
                        validJobBuilder(company, admin, PREFIX + " Dupe Dept " + tag(), LocalDateTime.now().plusDays(30)));
        Department department = persistDepartment();

        jobEligibleDepartmentRepository.saveAndFlush(
                JobEligibleDepartment.builder().job(job).department(department).build());

        assertThatThrownBy(
                        () ->
                                jobEligibleDepartmentRepository.saveAndFlush(
                                        JobEligibleDepartment.builder().job(job).department(department).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // 7. uk_jobs_company_title_deadline
    // ------------------------------------------------------------------

    @Test
    void duplicateCompanyTitleDeadline_violatesUniqueConstraint() {
        Company company = persistCompany();
        User admin = persistUser("admin", Role.ADMIN);
        String title = PREFIX + " Dupe Job " + tag();
        LocalDateTime deadline = LocalDateTime.now().plusDays(30);

        jobRepository.saveAndFlush(validJobBuilder(company, admin, title, deadline));

        Job duplicate = validJobBuilder(company, admin, title, deadline);
        assertThatThrownBy(() -> jobRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // 8. chk_placement_applications_status_change_attributed
    // ------------------------------------------------------------------

    @Test
    void applicationMovedOffApplied_withoutAttribution_violatesCheckConstraint() {
        Company company = persistCompany();
        User admin = persistUser("admin", Role.ADMIN);
        Job job =
                jobRepository.saveAndFlush(
                        validJobBuilder(company, admin, PREFIX + " Attribution " + tag(), LocalDateTime.now().plusDays(30)));
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Student student = persistActiveStudent(department, course);

        PlacementApplication application =
                placementApplicationRepository.saveAndFlush(
                        PlacementApplication.builder()
                                .job(job)
                                .student(student)
                                .status(ApplicationStatus.APPLIED)
                                .build());

        // Move to SHORTLISTED without setting statusChangedAt/statusChangedBy.
        application.setStatus(ApplicationStatus.SHORTLISTED);

        assertThatThrownBy(() -> placementApplicationRepository.saveAndFlush(application))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void applicationMovedOffApplied_withAttribution_succeeds() {
        Company company = persistCompany();
        User admin = persistUser("admin", Role.ADMIN);
        Job job =
                jobRepository.saveAndFlush(
                        validJobBuilder(
                                company, admin, PREFIX + " AttributionOk " + tag(), LocalDateTime.now().plusDays(30)));
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Student student = persistActiveStudent(department, course);

        PlacementApplication application =
                placementApplicationRepository.saveAndFlush(
                        PlacementApplication.builder()
                                .job(job)
                                .student(student)
                                .status(ApplicationStatus.APPLIED)
                                .build());

        application.setStatus(ApplicationStatus.SHORTLISTED);
        application.setStatusChangedAt(LocalDateTime.now());
        application.setStatusChangedBy(admin);

        PlacementApplication saved = placementApplicationRepository.saveAndFlush(application);
        assertThat(saved.getStatus()).isEqualTo(ApplicationStatus.SHORTLISTED);
        assertThat(saved.getStatusChangedAt()).isNotNull();
        assertThat(saved.getStatusChangedBy().getId()).isEqualTo(admin.getId());
    }

    // ------------------------------------------------------------------
    // 9a. Deleting a Job that has applications fails (FK has no cascade).
    // ------------------------------------------------------------------

    @Test
    void deletingJobWithApplications_fails_noCascade() {
        Company company = persistCompany();
        User admin = persistUser("admin", Role.ADMIN);
        Job job =
                jobRepository.saveAndFlush(
                        validJobBuilder(
                                company, admin, PREFIX + " DeleteBlocked " + tag(), LocalDateTime.now().plusDays(30)));
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Student student = persistActiveStudent(department, course);
        placementApplicationRepository.saveAndFlush(
                PlacementApplication.builder().job(job).student(student).status(ApplicationStatus.APPLIED).build());

        assertThatThrownBy(
                        () -> {
                            jobRepository.delete(job);
                            jobRepository.flush();
                        })
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // 9b. Deleting a Job with only job_eligible_departments rows succeeds and cascades.
    // ------------------------------------------------------------------

    @Test
    void deletingJobWithOnlyEligibleDepartmentRows_succeeds_andCascadesTheChildRows() {
        Company company = persistCompany();
        User admin = persistUser("admin", Role.ADMIN);
        Job job =
                jobRepository.saveAndFlush(
                        validJobBuilder(
                                company, admin, PREFIX + " DeleteCascade " + tag(), LocalDateTime.now().plusDays(30)));
        Department department = persistDepartment();
        jobEligibleDepartmentRepository.saveAndFlush(
                JobEligibleDepartment.builder().job(job).department(department).build());
        Long jobId = job.getId();

        jobRepository.delete(job);
        jobRepository.flush();

        assertThat(jobRepository.findById(jobId)).isEmpty();
        List<JobEligibleDepartment> remaining = jobEligibleDepartmentRepository.findByJobId(jobId);
        assertThat(remaining).isEmpty();
    }
}
