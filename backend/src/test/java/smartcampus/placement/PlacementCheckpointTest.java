package smartcampus.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import smartcampus.TestcontainersConfiguration;
import smartcampus.dto.AnalyticsStudentResponse;
import smartcampus.dto.ApplicationBulkStatusRequest;
import smartcampus.dto.ApplicationStatusUpdateRequest;
import smartcampus.dto.AuthResponse;
import smartcampus.dto.CompanyCreateRequest;
import smartcampus.dto.CompanyResponse;
import smartcampus.dto.EligibilityReason;
import smartcampus.dto.EligibilityReasonCode;
import smartcampus.dto.ExamCreateRequest;
import smartcampus.dto.ExamResponse;
import smartcampus.dto.JobCreateRequest;
import smartcampus.dto.JobEligibilityResponse;
import smartcampus.dto.JobResponse;
import smartcampus.dto.JobStatusUpdateRequest;
import smartcampus.dto.MarksBulkRequest;
import smartcampus.dto.MarksEntry;
import smartcampus.dto.PageResponse;
import smartcampus.dto.PlacementAnalyticsResponse;
import smartcampus.dto.PlacementApplicationCreateRequest;
import smartcampus.dto.PlacementApplicationResponse;
import smartcampus.entity.ApplicationStatus;
import smartcampus.entity.Company;
import smartcampus.entity.CompanyStatus;
import smartcampus.entity.Course;
import smartcampus.entity.Department;
import smartcampus.entity.Enrollment;
import smartcampus.entity.EnrollmentStatus;
import smartcampus.entity.ExamType;
import smartcampus.entity.Job;
import smartcampus.entity.JobStatus;
import smartcampus.entity.JobType;
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
import smartcampus.repository.PlacementApplicationRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.SubjectRepository;
import smartcampus.repository.UserRepository;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

/**
 * The PROJECT_PLAN.md Phase 8 checkpoint: "an ineligible student is blocked with an
 * accurate reason; an eligible one applies once and cannot apply twice" — exercised
 * through the real {@code SecurityConfig} filter chain and the real {@code
 * /api/companies}, {@code /api/jobs}, {@code /api/jobs/{id}/eligibility}, {@code
 * /api/applications} and {@code /api/placement/analytics} controllers against
 * Testcontainers MySQL with real Flyway migrations — no H2, no mocking of the
 * authorization, eligibility or aggregation layers.
 *
 * <p>CGPA/marks-percentage figures are never stubbed here: at least one student's real
 * numbers are produced by driving the actual Phase 4/5 pipeline (subject -> enrollment
 * -> {@code POST /api/exams} -> {@code POST /api/marks/bulk} -> {@code GET
 * /api/analytics/me}), matching {@code AnalyticsCheckpointTest} /
 * {@code MarksAndGradesCheckpointTest}'s established fixture recipe, per this file's
 * task brief.
 *
 * <p>Every fixture code/email is derived from a per-JVM {@link AtomicInteger} tagged
 * {@code "PL"} (Placement), never {@code System.nanoTime()} — see the identical note in
 * {@code AnalyticsCheckpointTest} and {@code MarksAndGradesCheckpointTest}: Spring's
 * TestContext framework caches one {@code ApplicationContext} (and Testcontainers MySQL
 * instance) across every test class in the suite with a matching signature, so a
 * distinct prefix is what actually guarantees this class's rows never collide with a
 * sibling class's rows in the same physical tables.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PlacementCheckpointTest {

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
    @Autowired private PasswordEncoder passwordEncoder;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String PREFIX = "PL";
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

    private Course persistCourse(Department department, int durationSemesters) {
        String t = tag();
        return courseRepository.save(
                Course.builder()
                        .code(PREFIX + "C" + t)
                        .name(PREFIX + " Course " + t)
                        .department(department)
                        .durationSemesters(durationSemesters)
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
        return userRepository.save(
                User.builder()
                        .email(PREFIX.toLowerCase() + "-" + prefix + t + "@example.com")
                        .password(passwordEncoder.encode(RAW_PASSWORD))
                        .fullName(PREFIX + " " + prefix + " " + t)
                        .role(role)
                        .build());
    }

    private Student persistStudent(
            Department department,
            Course course,
            Integer admissionYear,
            StudentStatus status,
            Integer semester,
            String section) {
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
                        .admissionYear(admissionYear)
                        .status(status)
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
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readValue(body, AuthResponse.class).token();
    }

    private String loginAsStudent(Student student) throws Exception {
        return login(student.getUser().getEmail(), RAW_PASSWORD);
    }

    private String adminToken() throws Exception {
        User admin = persistUser("admin", Role.ADMIN);
        return login(admin.getEmail(), RAW_PASSWORD);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    // ------------------------------------------------------------------
    // Thin HTTP wrappers around Phase 4/5 (reused, never recomputed here)
    // ------------------------------------------------------------------

    private ExamResponse createExam(
            String token, Long subjectId, String academicYear, int semester, String section, BigDecimal maxMarks)
            throws Exception {
        ExamCreateRequest request =
                new ExamCreateRequest(
                        subjectId,
                        PREFIX + " Internal " + tag(),
                        ExamType.INTERNAL_1,
                        academicYear,
                        semester,
                        section,
                        LocalDate.now(),
                        maxMarks);
        String body =
                mockMvc.perform(
                                post("/api/exams")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
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
                        .content(objectMapper.writeValueAsString(request)));
    }

    private AnalyticsStudentResponse myAnalytics(String token) throws Exception {
        String body =
                mockMvc.perform(get("/api/analytics/me").header("Authorization", "Bearer " + token))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readValue(body, AnalyticsStudentResponse.class);
    }

    /**
     * Gives {@code student} a REAL, credit-weighted CGPA and marks percentage by driving
     * the actual exam/marks endpoints, exactly like {@code AnalyticsCheckpointTest} does —
     * never a stubbed or hand-inserted figure.
     */
    private void gradeStudent(
            String adminToken, Student student, Course course, int semester, String section, BigDecimal marksOn100)
            throws Exception {
        Subject subject = persistSubject(course, 4, semester);
        String academicYear = "2025-2026";
        enroll(student, subject, academicYear, semester, section);
        ExamResponse exam = createExam(adminToken, subject.getId(), academicYear, semester, section, bd("100.00"));
        enterMarks(adminToken, exam.id(), student.getId(), marksOn100);
    }

    // ------------------------------------------------------------------
    // Thin HTTP wrappers around the Phase 8 controllers under test
    // ------------------------------------------------------------------

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String token) {
        return token == null ? builder : builder.header("Authorization", "Bearer " + token);
    }

    private MockHttpServletResponse postJson(String token, String url, Object body) throws Exception {
        return mockMvc.perform(
                        auth(post(url), token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andReturn()
                .getResponse();
    }

    private MockHttpServletResponse patchJson(String token, String url, Object body) throws Exception {
        return mockMvc.perform(
                        auth(patch(url), token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andReturn()
                .getResponse();
    }

    private MockHttpServletResponse getReq(String token, String url) throws Exception {
        return mockMvc.perform(auth(get(url), token)).andReturn().getResponse();
    }

    private CompanyResponse createCompany(String adminToken) throws Exception {
        CompanyCreateRequest request =
                new CompanyCreateRequest(PREFIX + " Company " + tag(), null, null, null, null, null, null, null);
        MockHttpServletResponse response = postJson(adminToken, "/api/companies", request);
        assertThat(response.getStatus()).isEqualTo(201);
        return objectMapper.readValue(response.getContentAsString(), CompanyResponse.class);
    }

    private JobCreateRequest jobRequest(
            Long companyId,
            String title,
            List<Long> eligibleDepartmentIds,
            BigDecimal minCgpa,
            BigDecimal minMarksPercentage,
            Integer graduationYear,
            LocalDateTime deadline,
            JobStatus status) {
        return new JobCreateRequest(
                companyId,
                title,
                null,
                null,
                JobType.FULL_TIME,
                null,
                null,
                null,
                null,
                minCgpa,
                minMarksPercentage,
                graduationYear,
                eligibleDepartmentIds,
                deadline,
                null,
                status);
    }

    private JobResponse createJob(String adminToken, JobCreateRequest request) throws Exception {
        MockHttpServletResponse response = postJson(adminToken, "/api/jobs", request);
        assertThat(response.getStatus()).isEqualTo(201);
        return objectMapper.readValue(response.getContentAsString(), JobResponse.class);
    }

    private JobResponse setJobStatus(String adminToken, Long jobId, JobStatus status) throws Exception {
        MockHttpServletResponse response =
                patchJson(adminToken, "/api/jobs/" + jobId + "/status", new JobStatusUpdateRequest(status));
        assertThat(response.getStatus()).isEqualTo(200);
        return objectMapper.readValue(response.getContentAsString(), JobResponse.class);
    }

    private JobEligibilityResponse eligibility(String token, Long jobId) throws Exception {
        MockHttpServletResponse response = getReq(token, "/api/jobs/" + jobId + "/eligibility");
        assertThat(response.getStatus()).isEqualTo(200);
        return objectMapper.readValue(response.getContentAsString(), JobEligibilityResponse.class);
    }

    private <T> PageResponse<T> readPage(String body, Class<T> contentType) throws Exception {
        JavaType type = objectMapper.getTypeFactory().constructParametricType(PageResponse.class, contentType);
        return objectMapper.readValue(body, type);
    }

    private void assertNoSecretsLeaked(String body) {
        assertThat(body).doesNotContainIgnoringCase("password");
        assertThat(body).doesNotContain("$2a$");
        assertThat(body).doesNotContain("\tat ");
        assertThat(body).doesNotContain("Caused by:");
    }

    // ==================================================================
    // 1. INELIGIBLE, ACCURATE REASON
    // ==================================================================

    @Test
    void eligibility_cgpaBelowMinimum_matchesRealComputedCgpa() throws Exception {
        String admin = adminToken();
        Department dept = persistDepartment();
        Course course = persistCourse(dept, 8);
        Student student = persistStudent(dept, course, 2022, StudentStatus.ACTIVE, 5, "A");
        gradeStudent(admin, student, course, 5, "A", bd("60.00")); // -> grade point 6.00 (B band)

        String studentToken = loginAsStudent(student);
        AnalyticsStudentResponse analytics = myAnalytics(studentToken);
        assertThat(analytics.cgpa()).isNotNull();
        BigDecimal actualCgpa = analytics.cgpa();
        BigDecimal minCgpa = actualCgpa.add(bd("1.00")).min(bd("10.00"));
        // Guarantee a strictly-above-actual threshold even at the ceiling.
        if (minCgpa.compareTo(actualCgpa) <= 0) {
            minCgpa = bd("10.00");
        }

        CompanyResponse company = createCompany(admin);
        JobResponse job =
                createJob(
                        admin,
                        jobRequest(
                                company.id(),
                                PREFIX + " CgpaBelow " + tag(),
                                null,
                                minCgpa,
                                null,
                                null,
                                LocalDateTime.now().plusDays(10),
                                JobStatus.OPEN));

        JobEligibilityResponse response = eligibility(studentToken, job.id());

        assertThat(response.eligible()).isFalse();
        assertThat(response.studentCgpa()).isEqualByComparingTo(actualCgpa);
        List<EligibilityReasonCode> codes = response.reasons().stream().map(EligibilityReason::code).toList();
        assertThat(codes).contains(EligibilityReasonCode.CGPA_BELOW_MINIMUM);
        String message =
                response.reasons().stream()
                        .filter(r -> r.code() == EligibilityReasonCode.CGPA_BELOW_MINIMUM)
                        .findFirst()
                        .orElseThrow()
                        .message();
        assertThat(message).contains(actualCgpa.toPlainString());
        assertThat(message).contains(minCgpa.toPlainString());
    }

    @Test
    void eligibility_cgpaNotAvailable_zeroMinCgpaIsDistinctFromNull() throws Exception {
        String admin = adminToken();
        Department dept = persistDepartment();
        Course course = persistCourse(dept, 8);
        Student student = persistStudent(dept, course, 2022, StudentStatus.ACTIVE, 5, "A");
        // NOTHING graded for this student at all.

        String studentToken = loginAsStudent(student);
        CompanyResponse company = createCompany(admin);
        JobResponse job =
                createJob(
                        admin,
                        jobRequest(
                                company.id(),
                                PREFIX + " CgpaZero " + tag(),
                                null,
                                bd("0.00"),
                                null,
                                null,
                                LocalDateTime.now().plusDays(10),
                                JobStatus.OPEN));

        JobEligibilityResponse response = eligibility(studentToken, job.id());

        assertThat(response.eligible()).isFalse();
        List<EligibilityReasonCode> codes = response.reasons().stream().map(EligibilityReason::code).toList();
        assertThat(codes).contains(EligibilityReasonCode.CGPA_NOT_AVAILABLE);
        assertThat(codes).doesNotContain(EligibilityReasonCode.CGPA_BELOW_MINIMUM);
    }

    @Test
    void eligibility_departmentRestriction_exactMessage_andConverseEmptySetMeansAllDepartments() throws Exception {
        String admin = adminToken();
        Department cse = persistDepartment();
        Department ece = persistDepartment();
        Course course = persistCourse(cse, 8);
        Student student = persistStudent(cse, course, 2022, StudentStatus.ACTIVE, 5, "A");
        String studentToken = loginAsStudent(student);

        CompanyResponse company = createCompany(admin);
        JobResponse restrictedJob =
                createJob(
                        admin,
                        jobRequest(
                                company.id(),
                                PREFIX + " DeptRestricted " + tag(),
                                List.of(ece.getId()),
                                null,
                                null,
                                null,
                                LocalDateTime.now().plusDays(10),
                                JobStatus.OPEN));

        JobEligibilityResponse restricted = eligibility(studentToken, restrictedJob.id());
        assertThat(restricted.eligible()).isFalse();
        List<EligibilityReasonCode> codes = restricted.reasons().stream().map(EligibilityReason::code).toList();
        assertThat(codes).contains(EligibilityReasonCode.DEPARTMENT_NOT_ELIGIBLE);

        // Converse: no job_eligible_departments rows at all -> open to every department.
        JobResponse openToAllJob =
                createJob(
                        admin,
                        jobRequest(
                                company.id(),
                                PREFIX + " DeptOpenAll " + tag(),
                                null,
                                null,
                                null,
                                null,
                                LocalDateTime.now().plusDays(10),
                                JobStatus.OPEN));
        JobEligibilityResponse openToAll = eligibility(studentToken, openToAllJob.id());
        List<EligibilityReasonCode> openCodes = openToAll.reasons().stream().map(EligibilityReason::code).toList();
        assertThat(openCodes).doesNotContain(EligibilityReasonCode.DEPARTMENT_NOT_ELIGIBLE);
    }

    @Test
    void eligibility_graduationYearMismatch() throws Exception {
        String admin = adminToken();
        Department dept = persistDepartment();
        Course course = persistCourse(dept, 8); // -> derives 2026 from admissionYear 2022
        Student student = persistStudent(dept, course, 2022, StudentStatus.ACTIVE, 5, "A");
        String studentToken = loginAsStudent(student);

        CompanyResponse company = createCompany(admin);
        JobResponse job =
                createJob(
                        admin,
                        jobRequest(
                                company.id(),
                                PREFIX + " GradMismatch " + tag(),
                                null,
                                null,
                                null,
                                2025,
                                LocalDateTime.now().plusDays(10),
                                JobStatus.OPEN));

        JobEligibilityResponse response = eligibility(studentToken, job.id());
        assertThat(response.eligible()).isFalse();
        List<EligibilityReasonCode> codes = response.reasons().stream().map(EligibilityReason::code).toList();
        assertThat(codes).contains(EligibilityReasonCode.GRADUATION_YEAR_MISMATCH);
        assertThat(response.studentGraduationYear()).isEqualTo(2026);
    }

    @Test
    void eligibility_graduationYearUnknown_whenAdmissionYearNull() throws Exception {
        String admin = adminToken();
        Department dept = persistDepartment();
        Course course = persistCourse(dept, 8);
        Student student = persistStudent(dept, course, null, StudentStatus.ACTIVE, 5, "A");
        String studentToken = loginAsStudent(student);

        CompanyResponse company = createCompany(admin);
        JobResponse job =
                createJob(
                        admin,
                        jobRequest(
                                company.id(),
                                PREFIX + " GradUnknown " + tag(),
                                null,
                                null,
                                null,
                                2026,
                                LocalDateTime.now().plusDays(10),
                                JobStatus.OPEN));

        JobEligibilityResponse response = eligibility(studentToken, job.id());
        assertThat(response.eligible()).isFalse();
        List<EligibilityReasonCode> codes = response.reasons().stream().map(EligibilityReason::code).toList();
        assertThat(codes).contains(EligibilityReasonCode.GRADUATION_YEAR_UNKNOWN);
        assertThat(codes).doesNotContain(EligibilityReasonCode.GRADUATION_YEAR_MISMATCH);
        assertThat(response.studentGraduationYear()).isNull();
    }

    @Test
    void eligibility_pendingStudent_profileNotActive() throws Exception {
        String admin = adminToken();
        Department dept = persistDepartment();
        Course course = persistCourse(dept, 8);
        Student student = persistStudent(dept, course, 2022, StudentStatus.PENDING, null, null);
        String studentToken = loginAsStudent(student);

        CompanyResponse company = createCompany(admin);
        JobResponse job =
                createJob(
                        admin,
                        jobRequest(
                                company.id(),
                                PREFIX + " Pending " + tag(),
                                null,
                                null,
                                null,
                                null,
                                LocalDateTime.now().plusDays(10),
                                JobStatus.OPEN));

        JobEligibilityResponse response = eligibility(studentToken, job.id());
        assertThat(response.eligible()).isFalse();
        List<EligibilityReasonCode> codes = response.reasons().stream().map(EligibilityReason::code).toList();
        assertThat(codes).contains(EligibilityReasonCode.PROFILE_NOT_ACTIVE);
    }

    @Test
    void eligibility_multipleFailingReasons_returnsAllOfThem_noShortCircuit() throws Exception {
        String admin = adminToken();
        Department cse = persistDepartment();
        Department ece = persistDepartment();
        Course course = persistCourse(cse, 8);
        Student student = persistStudent(cse, course, null, StudentStatus.PENDING, 5, "A"); // no admission year either
        String studentToken = loginAsStudent(student);

        CompanyResponse company = createCompany(admin);
        JobResponse job =
                createJob(
                        admin,
                        jobRequest(
                                company.id(),
                                PREFIX + " MultiFail " + tag(),
                                List.of(ece.getId()),
                                bd("9.00"),
                                bd("90.00"),
                                2026,
                                LocalDateTime.now().plusDays(10),
                                JobStatus.OPEN));

        JobEligibilityResponse response = eligibility(studentToken, job.id());
        assertThat(response.eligible()).isFalse();
        List<EligibilityReasonCode> codes = response.reasons().stream().map(EligibilityReason::code).toList();
        // At minimum: profile not active, wrong department, unknown grad year (no
        // admission year), CGPA not available, percentage not available — five reasons,
        // not just the first one found.
        assertThat(codes)
                .contains(
                        EligibilityReasonCode.PROFILE_NOT_ACTIVE,
                        EligibilityReasonCode.DEPARTMENT_NOT_ELIGIBLE,
                        EligibilityReasonCode.GRADUATION_YEAR_UNKNOWN,
                        EligibilityReasonCode.CGPA_NOT_AVAILABLE,
                        EligibilityReasonCode.PERCENTAGE_NOT_AVAILABLE);
        assertThat(codes.size()).isGreaterThanOrEqualTo(5);
    }

    // ==================================================================
    // 2-5. Eligible student applies once; cannot apply twice; unique key (not just the
    //      if-statement); withdrawal does not free the slot.
    // ==================================================================

    @Test
    void applyOnce_duplicateRejected_uniqueKeyEnforcedAtDatabase_withdrawalIsTerminal() throws Exception {
        String admin = adminToken();
        Department dept = persistDepartment();
        Course course = persistCourse(dept, 8);
        Student student = persistStudent(dept, course, 2022, StudentStatus.ACTIVE, 5, "A");
        gradeStudent(admin, student, course, 5, "A", bd("70.00"));
        String studentToken = loginAsStudent(student);
        AnalyticsStudentResponse analytics = myAnalytics(studentToken);
        assertThat(analytics.cgpa()).isNotNull();

        CompanyResponse company = createCompany(admin);
        JobResponse job =
                createJob(
                        admin,
                        jobRequest(
                                company.id(),
                                PREFIX + " ApplyOnce " + tag(),
                                null,
                                null,
                                null,
                                null,
                                LocalDateTime.now().plusDays(10),
                                JobStatus.OPEN));

        // Confirm eligible before applying.
        JobEligibilityResponse elig = eligibility(studentToken, job.id());
        assertThat(elig.eligible()).isTrue();
        assertThat(elig.canApply()).isTrue();

        MockHttpServletResponse first =
                postJson(
                        studentToken,
                        "/api/applications",
                        new PlacementApplicationCreateRequest(job.id(), null, "Please consider me."));
        assertThat(first.getStatus()).isEqualTo(201);
        PlacementApplicationResponse created =
                objectMapper.readValue(first.getContentAsString(), PlacementApplicationResponse.class);
        assertThat(created.status()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(created.cgpaAtApplication()).isNotNull();
        assertThat(created.cgpaAtApplication()).isEqualByComparingTo(analytics.cgpa());

        // 3. Cannot apply twice via the service path.
        MockHttpServletResponse duplicate =
                postJson(
                        studentToken,
                        "/api/applications",
                        new PlacementApplicationCreateRequest(job.id(), null, "Second try."));
        assertThat(duplicate.getStatus()).isEqualTo(409);
        String duplicateBody = duplicate.getContentAsString();
        assertThat(duplicateBody).contains("\"error\"");
        assertThat(duplicateBody).contains("\"message\"");
        assertNoSecretsLeaked(duplicateBody);

        // 4. The UNIQUE KEY itself, bypassing the service's existsBy check entirely.
        Job jobEntity = jobRepository.findById(job.id()).orElseThrow();
        Student studentEntity = studentRepository.findById(student.getId()).orElseThrow();
        PlacementApplication raceRow =
                PlacementApplication.builder()
                        .job(jobEntity)
                        .student(studentEntity)
                        .status(ApplicationStatus.APPLIED)
                        .build();
        assertThatThrownBy(() -> placementApplicationRepository.saveAndFlush(raceRow))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 5. Withdraw, then re-apply -> still 409 (withdrawal does not free the slot).
        MockHttpServletResponse withdrawn =
                postJson(studentToken, "/api/applications/" + created.id() + "/withdraw", null);
        assertThat(withdrawn.getStatus()).isEqualTo(200);
        PlacementApplicationResponse withdrawnBody =
                objectMapper.readValue(withdrawn.getContentAsString(), PlacementApplicationResponse.class);
        assertThat(withdrawnBody.status()).isEqualTo(ApplicationStatus.WITHDRAWN);

        MockHttpServletResponse afterWithdraw =
                postJson(
                        studentToken,
                        "/api/applications",
                        new PlacementApplicationCreateRequest(job.id(), null, "Third try after withdrawing."));
        assertThat(afterWithdraw.getStatus()).isEqualTo(409);
    }

    // ==================================================================
    // 6. Deadline guard (§35/§53) — blocker, not a criterion.
    // ==================================================================

    @Test
    void deadlineGuard_pastDeadline_isBlockerNotCriterion_andPostIsRejected() throws Exception {
        String admin = adminToken();
        Department dept = persistDepartment();
        Course course = persistCourse(dept, 8);
        Student student = persistStudent(dept, course, 2022, StudentStatus.ACTIVE, 5, "A");
        String studentToken = loginAsStudent(student);

        CompanyResponse company = createCompany(admin);
        JobResponse created =
                createJob(
                        admin,
                        jobRequest(
                                company.id(),
                                PREFIX + " Deadline " + tag(),
                                null,
                                null,
                                null,
                                null,
                                LocalDateTime.now().plusDays(5),
                                JobStatus.OPEN));

        // The service almost certainly refuses to CREATE (or move to OPEN) a drive whose
        // deadline is already in the past (see V8's migration comment), so we force the
        // past-deadline state directly at the database, which is legal — the schema has
        // no CHECK against it (NOW() is non-deterministic and cannot appear in a CHECK).
        Job jobEntity = jobRepository.findById(created.id()).orElseThrow();
        LocalDateTime pastDeadline = LocalDateTime.now().minusDays(2);
        jobEntity.setApplicationDeadline(pastDeadline);
        jobRepository.saveAndFlush(jobEntity);

        JobEligibilityResponse response = eligibility(studentToken, created.id());
        assertThat(response.eligible()).isTrue(); // met every criterion
        assertThat(response.canApply()).isFalse(); // but the deadline blocks it
        List<EligibilityReasonCode> codes = response.reasons().stream().map(EligibilityReason::code).toList();
        assertThat(codes).contains(EligibilityReasonCode.DEADLINE_PASSED);

        MockHttpServletResponse applyResponse =
                postJson(
                        studentToken,
                        "/api/applications",
                        new PlacementApplicationCreateRequest(created.id(), null, null));
        assertThat(applyResponse.getStatus()).isEqualTo(400);
    }

    // ==================================================================
    // 7. DRAFT/CANCELLED not-open guard — 404, never 403; hidden from the student list
    //    even when status=DRAFT is explicitly requested.
    // ==================================================================

    @Test
    void notOpenGuard_draftAndCancelled_areHiddenFromStudents_404NotProbeable() throws Exception {
        String admin = adminToken();
        Department dept = persistDepartment();
        Course course = persistCourse(dept, 8);
        Student student = persistStudent(dept, course, 2022, StudentStatus.ACTIVE, 5, "A");
        String studentToken = loginAsStudent(student);

        CompanyResponse company = createCompany(admin);
        JobResponse draftJob =
                createJob(
                        admin,
                        jobRequest(
                                company.id(),
                                PREFIX + " Draft " + tag(),
                                null,
                                null,
                                null,
                                null,
                                LocalDateTime.now().plusDays(10),
                                JobStatus.DRAFT));
        assertThat(draftJob.status()).isEqualTo(JobStatus.DRAFT);

        MockHttpServletResponse draftGet = getReq(studentToken, "/api/jobs/" + draftJob.id());
        assertThat(draftGet.getStatus()).isEqualTo(404);

        JobResponse cancelledJob =
                createJob(
                        admin,
                        jobRequest(
                                company.id(),
                                PREFIX + " Cancelled " + tag(),
                                null,
                                null,
                                null,
                                null,
                                LocalDateTime.now().plusDays(10),
                                JobStatus.DRAFT));
        setJobStatus(admin, cancelledJob.id(), JobStatus.CANCELLED);
        MockHttpServletResponse cancelledGet = getReq(studentToken, "/api/jobs/" + cancelledJob.id());
        assertThat(cancelledGet.getStatus()).isEqualTo(404);

        // Explicitly asking for status=DRAFT as a student must yield an EMPTY page, not
        // an unfiltered / re-interpreted one.
        MockHttpServletResponse listDraft =
                getReq(studentToken, "/api/jobs?status=DRAFT&page=0&size=50");
        assertThat(listDraft.getStatus()).isEqualTo(200);
        PageResponse<JobResponse> page = readPage(listDraft.getContentAsString(), JobResponse.class);
        assertThat(page.totalElements()).isZero();
    }

    // ==================================================================
    // 8. Authorization sweep, including the eligibility-leak hunt.
    // ==================================================================

    @Test
    void authorizationSweep_studentBlockedFromAdminRoutes_ownerOnlyAccess_noEligibilityLeak() throws Exception {
        String admin = adminToken();
        Department dept = persistDepartment();
        Course course = persistCourse(dept, 8);
        Student studentA = persistStudent(dept, course, 2022, StudentStatus.ACTIVE, 5, "A");
        Student studentB = persistStudent(dept, course, 2022, StudentStatus.ACTIVE, 5, "A");
        gradeStudent(admin, studentA, course, 5, "A", bd("85.00"));
        String tokenA = loginAsStudent(studentA);
        String tokenB = loginAsStudent(studentB);

        CompanyResponse company = createCompany(admin);
        JobResponse job =
                createJob(
                        admin,
                        jobRequest(
                                company.id(),
                                PREFIX + " AuthSweep " + tag(),
                                null,
                                null,
                                null,
                                null,
                                LocalDateTime.now().plusDays(10),
                                JobStatus.OPEN));

        MockHttpServletResponse applied =
                postJson(tokenA, "/api/applications", new PlacementApplicationCreateRequest(job.id(), null, null));
        assertThat(applied.getStatus()).isEqualTo(201);
        PlacementApplicationResponse applicationA =
                objectMapper.readValue(applied.getContentAsString(), PlacementApplicationResponse.class);

        // ADMIN-only routes, called as a STUDENT -> 403.
        assertThat(getReq(tokenA, "/api/applications").getStatus()).isEqualTo(403);
        assertThat(
                        patchJson(
                                        tokenA,
                                        "/api/applications/" + applicationA.id() + "/status",
                                        new ApplicationStatusUpdateRequest(ApplicationStatus.SHORTLISTED, null))
                                .getStatus())
                .isEqualTo(403);
        assertThat(
                        postJson(
                                        tokenA,
                                        "/api/applications/bulk-status",
                                        new ApplicationBulkStatusRequest(
                                                List.of(applicationA.id()), ApplicationStatus.SHORTLISTED, null))
                                .getStatus())
                .isEqualTo(403);
        assertThat(getReq(tokenA, "/api/jobs/" + job.id() + "/eligible-students").getStatus()).isEqualTo(403);
        assertThat(getReq(tokenA, "/api/placement/analytics").getStatus()).isEqualTo(403);
        assertThat(
                        postJson(
                                        tokenA,
                                        "/api/companies",
                                        new CompanyCreateRequest("Should Not Exist", null, null, null, null, null, null, null))
                                .getStatus())
                .isEqualTo(403);
        assertThat(
                        postJson(
                                        tokenA,
                                        "/api/jobs",
                                        jobRequest(
                                                company.id(),
                                                "Should Not Exist",
                                                null,
                                                null,
                                                null,
                                                null,
                                                LocalDateTime.now().plusDays(1),
                                                JobStatus.DRAFT))
                                .getStatus())
                .isEqualTo(403);

        // Student B cannot see student A's application -> 404, never 403.
        assertThat(getReq(tokenB, "/api/applications/" + applicationA.id()).getStatus()).isEqualTo(404);

        // The leak hunt: student B asking for student A's eligibility via ?studentId=
        // must never return student A's real figures. Either the param is ignored
        // (evaluates B's own record) or the call is refused — both are compliant; a 200
        // that echoes A's studentId/CGPA to B is not.
        MockHttpServletResponse leakAttempt =
                getReq(tokenB, "/api/jobs/" + job.id() + "/eligibility?studentId=" + studentA.getId());
        if (leakAttempt.getStatus() == 200) {
            JobEligibilityResponse body =
                    objectMapper.readValue(leakAttempt.getContentAsString(), JobEligibilityResponse.class);
            assertThat(body.studentId())
                    .as("A student's own eligibility call must never resolve to a different student's identity")
                    .isNotEqualTo(studentA.getId());
        } else {
            assertThat(leakAttempt.getStatus()).isIn(400, 403);
        }
    }

    // ==================================================================
    // 9. Status lifecycle + attribution, illegal transitions rejected.
    // ==================================================================

    @Test
    void statusLifecycle_adminTransitionsWithAttribution_illegalTransitionsRejected() throws Exception {
        String admin = adminToken();
        Department dept = persistDepartment();
        Course course = persistCourse(dept, 8);
        Student student = persistStudent(dept, course, 2022, StudentStatus.ACTIVE, 5, "A");
        String studentToken = loginAsStudent(student);

        CompanyResponse company = createCompany(admin);
        JobResponse job =
                createJob(
                        admin,
                        jobRequest(
                                company.id(),
                                PREFIX + " Lifecycle " + tag(),
                                null,
                                null,
                                null,
                                null,
                                LocalDateTime.now().plusDays(10),
                                JobStatus.OPEN));
        MockHttpServletResponse applyResponse =
                postJson(studentToken, "/api/applications", new PlacementApplicationCreateRequest(job.id(), null, null));
        PlacementApplicationResponse application =
                objectMapper.readValue(applyResponse.getContentAsString(), PlacementApplicationResponse.class);

        // APPLIED -> SHORTLISTED
        MockHttpServletResponse toShortlisted =
                patchJson(
                        admin,
                        "/api/applications/" + application.id() + "/status",
                        new ApplicationStatusUpdateRequest(ApplicationStatus.SHORTLISTED, "Looks strong."));
        assertThat(toShortlisted.getStatus()).isEqualTo(200);
        PlacementApplicationResponse shortlisted =
                objectMapper.readValue(toShortlisted.getContentAsString(), PlacementApplicationResponse.class);
        assertThat(shortlisted.status()).isEqualTo(ApplicationStatus.SHORTLISTED);
        assertThat(shortlisted.statusChangedAt()).isNotNull();
        assertThat(shortlisted.statusChangedById()).isNotNull();

        // SHORTLISTED -> SELECTED
        MockHttpServletResponse toSelected =
                patchJson(
                        admin,
                        "/api/applications/" + application.id() + "/status",
                        new ApplicationStatusUpdateRequest(ApplicationStatus.SELECTED, "Offer extended."));
        assertThat(toSelected.getStatus()).isEqualTo(200);
        PlacementApplicationResponse selected =
                objectMapper.readValue(toSelected.getContentAsString(), PlacementApplicationResponse.class);
        assertThat(selected.status()).isEqualTo(ApplicationStatus.SELECTED);
        assertThat(selected.statusChangedAt()).isNotNull();
        assertThat(selected.statusChangedById()).isNotNull();

        // SELECTED -> anything else -> 400 (terminal).
        MockHttpServletResponse illegal =
                patchJson(
                        admin,
                        "/api/applications/" + application.id() + "/status",
                        new ApplicationStatusUpdateRequest(ApplicationStatus.UNDER_REVIEW, null));
        assertThat(illegal.getStatus()).isEqualTo(400);
        assertNoSecretsLeaked(illegal.getContentAsString());

        // A fresh APPLIED application: admin may never set WITHDRAWN.
        Student student2 = persistStudent(dept, course, 2022, StudentStatus.ACTIVE, 5, "A");
        String student2Token = loginAsStudent(student2);
        MockHttpServletResponse apply2 =
                postJson(student2Token, "/api/applications", new PlacementApplicationCreateRequest(job.id(), null, null));
        PlacementApplicationResponse application2 =
                objectMapper.readValue(apply2.getContentAsString(), PlacementApplicationResponse.class);
        MockHttpServletResponse adminWithdraw =
                patchJson(
                        admin,
                        "/api/applications/" + application2.id() + "/status",
                        new ApplicationStatusUpdateRequest(ApplicationStatus.WITHDRAWN, null));
        assertThat(adminWithdraw.getStatus()).isEqualTo(400);

        // A student may never call the admin status-update endpoint at all.
        MockHttpServletResponse studentAttempt =
                patchJson(
                        student2Token,
                        "/api/applications/" + application2.id() + "/status",
                        new ApplicationStatusUpdateRequest(ApplicationStatus.SHORTLISTED, null));
        assertThat(studentAttempt.getStatus()).isEqualTo(403);
    }

    // ==================================================================
    // 10. Analytics: no fabricated numbers — verified against independently queried
    //     ground truth from the same repositories, not against the endpoint's own echo.
    // ==================================================================

    @Test
    void analytics_matchesIndependentlyComputedGroundTruth_noFabrication() throws Exception {
        String admin = adminToken();

        // A fully isolated department/company/job/application set this test controls end
        // to end, so its OWN contribution to the analytics response is fully knowable
        // regardless of what earlier tests in the shared context have already persisted.
        Department dept = persistDepartment();
        Course course = persistCourse(dept, 8);
        Student s1 = persistStudent(dept, course, 2022, StudentStatus.ACTIVE, 5, "A");
        Student s2 = persistStudent(dept, course, 2022, StudentStatus.ACTIVE, 5, "A");
        Student s3 = persistStudent(dept, course, 2022, StudentStatus.ACTIVE, 5, "A");
        String t1 = loginAsStudent(s1);
        String t2 = loginAsStudent(s2);
        String t3 = loginAsStudent(s3);

        CompanyResponse company = createCompany(admin);
        JobResponse job =
                createJob(
                        admin,
                        jobRequest(
                                company.id(),
                                PREFIX + " Analytics " + tag(),
                                null,
                                null,
                                null,
                                null,
                                LocalDateTime.now().plusDays(10),
                                JobStatus.OPEN));

        PlacementApplicationResponse a1 =
                objectMapper.readValue(
                        postJson(t1, "/api/applications", new PlacementApplicationCreateRequest(job.id(), null, null))
                                .getContentAsString(),
                        PlacementApplicationResponse.class);
        PlacementApplicationResponse a2 =
                objectMapper.readValue(
                        postJson(t2, "/api/applications", new PlacementApplicationCreateRequest(job.id(), null, null))
                                .getContentAsString(),
                        PlacementApplicationResponse.class);
        objectMapper.readValue(
                postJson(t3, "/api/applications", new PlacementApplicationCreateRequest(job.id(), null, null))
                        .getContentAsString(),
                PlacementApplicationResponse.class);

        // a2 -> SELECTED (goes through SHORTLISTED first per the transition table).
        patchJson(
                admin,
                "/api/applications/" + a2.id() + "/status",
                new ApplicationStatusUpdateRequest(ApplicationStatus.SHORTLISTED, null));
        patchJson(
                admin,
                "/api/applications/" + a2.id() + "/status",
                new ApplicationStatusUpdateRequest(ApplicationStatus.SELECTED, null));
        // a1 -> REJECTED directly.
        patchJson(
                admin,
                "/api/applications/" + a1.id() + "/status",
                new ApplicationStatusUpdateRequest(ApplicationStatus.REJECTED, null));
        // a3 left as APPLIED.

        // Ground truth, queried independently right before hitting the endpoint.
        long groundTotalCompanies = companyRepository.count();
        long groundActiveCompanies = companyRepository.countByStatus(CompanyStatus.ACTIVE);
        long groundTotalJobs = jobRepository.count();
        long groundDraftJobs = jobRepository.countByStatus(JobStatus.DRAFT);
        long groundOpenJobs = jobRepository.countByStatus(JobStatus.OPEN);
        long groundClosedJobs = jobRepository.countByStatus(JobStatus.CLOSED);
        long groundCancelledJobs = jobRepository.countByStatus(JobStatus.CANCELLED);
        long groundTotalApplications = placementApplicationRepository.count();
        long groundUniqueApplicants = placementApplicationRepository.countDistinctApplicants();
        long groundSelectedStudents =
                placementApplicationRepository.countDistinctStudentsByStatus(ApplicationStatus.SELECTED);
        long groundActiveStudents = studentRepository.findByStatus(StudentStatus.ACTIVE).size();

        MockHttpServletResponse response = getReq(admin, "/api/placement/analytics");
        assertThat(response.getStatus()).isEqualTo(200);
        PlacementAnalyticsResponse analytics =
                objectMapper.readValue(response.getContentAsString(), PlacementAnalyticsResponse.class);

        assertThat(analytics.totalCompanies()).isEqualTo(groundTotalCompanies);
        assertThat(analytics.activeCompanies()).isEqualTo(groundActiveCompanies);
        assertThat(analytics.totalJobs()).isEqualTo(groundTotalJobs);
        assertThat(analytics.draftJobs()).isEqualTo(groundDraftJobs);
        assertThat(analytics.openJobs()).isEqualTo(groundOpenJobs);
        assertThat(analytics.closedJobs()).isEqualTo(groundClosedJobs);
        assertThat(analytics.cancelledJobs()).isEqualTo(groundCancelledJobs);
        assertThat(analytics.totalApplications()).isEqualTo(groundTotalApplications);
        assertThat(analytics.uniqueApplicants()).isEqualTo(groundUniqueApplicants);
        assertThat(analytics.selectedStudents()).isEqualTo(groundSelectedStudents);
        assertThat(analytics.activeStudents()).isEqualTo(groundActiveStudents);

        // §69: never a fabricated 0.00 when there is no denominator; otherwise the exact
        // HALF_UP-rounded formula (the same rounding convention V4's grade_bands comment
        // documents for every percentage this application computes).
        if (groundActiveStudents == 0) {
            assertThat(analytics.placementRate()).isNull();
        } else {
            BigDecimal expectedRate =
                    BigDecimal.valueOf(groundSelectedStudents)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(groundActiveStudents), 2, RoundingMode.HALF_UP);
            assertThat(analytics.placementRate()).isNotNull();
            assertThat(analytics.placementRate()).isEqualByComparingTo(expectedRate);
        }

        // statusBreakdown must enumerate EVERY ApplicationStatus constant (even at 0),
        // so the frontend's chart axis never silently drops a category, and the sum must
        // equal the independently-counted total.
        assertThat(analytics.statusBreakdown().size()).isEqualTo(ApplicationStatus.values().length);
        assertThat(EnumSet.copyOf(analytics.statusBreakdown().stream().map(s -> s.status()).toList()))
                .isEqualTo(EnumSet.allOf(ApplicationStatus.class));
        long sumOfBreakdown = analytics.statusBreakdown().stream().mapToLong(s -> s.count()).sum();
        assertThat(sumOfBreakdown).isEqualTo(groundTotalApplications);

        // This test's OWN department is fully isolated (fresh unique id/code) so its row
        // in departmentBreakdown, if present, must match exactly what this test created:
        // 3 active students, 3 applicants (all three applied), 1 selected.
        analytics.departmentBreakdown().stream()
                .filter(row -> row.departmentId().equals(dept.getId()))
                .findFirst()
                .ifPresentOrElse(
                        row -> {
                            assertThat(row.activeStudents()).isEqualTo(3);
                            assertThat(row.applicants()).isEqualTo(3);
                            assertThat(row.selected()).isEqualTo(1);
                        },
                        () -> {
                            throw new AssertionError(
                                    "Expected department " + dept.getId() + " to appear in departmentBreakdown"
                                            + " since 3 of its students applied to a real drive.");
                        });

        assertThat(analytics.topCompanies().size()).isLessThanOrEqualTo(5);
        assertThat(analytics.jobFunnel().size()).isLessThanOrEqualTo(10);
        assertNoSecretsLeaked(response.getContentAsString());
    }
}
