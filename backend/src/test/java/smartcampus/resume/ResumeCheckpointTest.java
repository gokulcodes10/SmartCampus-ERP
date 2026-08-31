package smartcampus.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

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
import smartcampus.dto.ApplicationResumeUpdateRequest;
import smartcampus.dto.AuthResponse;
import smartcampus.dto.CompanyCreateRequest;
import smartcampus.dto.CompanyResponse;
import smartcampus.dto.JobCreateRequest;
import smartcampus.dto.JobResponse;
import smartcampus.dto.PlacementApplicationCreateRequest;
import smartcampus.dto.PlacementApplicationResponse;
import smartcampus.dto.ResumeAchievementRequest;
import smartcampus.dto.ResumeCertificationRequest;
import smartcampus.dto.ResumeDuplicateRequest;
import smartcampus.dto.ResumeEducationRequest;
import smartcampus.dto.ResumeExperienceRequest;
import smartcampus.dto.ResumePrefillResponse;
import smartcampus.dto.ResumeProjectRequest;
import smartcampus.dto.ResumeResponse;
import smartcampus.dto.ResumeSaveRequest;
import smartcampus.dto.ResumeSkillRequest;
import smartcampus.entity.Course;
import smartcampus.entity.Department;
import smartcampus.entity.EmploymentType;
import smartcampus.entity.GradeScale;
import smartcampus.entity.JobStatus;
import smartcampus.entity.JobType;
import smartcampus.entity.Resume;
import smartcampus.entity.ResumeTemplate;
import smartcampus.entity.Role;
import smartcampus.entity.SkillCategory;
import smartcampus.entity.SkillProficiency;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.User;
import smartcampus.repository.CourseRepository;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.PlacementApplicationRepository;
import smartcampus.repository.ResumeRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * PROJECT_PLAN.md Phase 9 checkpoint: "a resume built in the UI downloads as a correct
 * PDF and can be selected during a real job application" — driven end to end through the
 * real {@code SecurityConfig} filter chain and the real {@code /api/resumes} and
 * {@code /api/applications} controllers against Testcontainers MySQL with real Flyway
 * migrations (V9__resume.sql). No mocking of {@code ResumeService}, {@code
 * ResumePdfService}, or the composite-FK attachment guarantee.
 *
 * <p>Every fixture code/email is derived from a per-JVM {@link AtomicInteger} tagged
 * {@code "RS"} (Resume), matching the pattern in {@code PlacementCheckpointTest} /
 * {@code AnalyticsCheckpointTest}: the TestContext framework caches one
 * {@code ApplicationContext} across every test class with a matching signature, so a
 * distinct prefix is what keeps this class's rows from colliding with a sibling class's
 * rows in the same physical tables.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ResumeCheckpointTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private ResumeRepository resumeRepository;
    @Autowired private PlacementApplicationRepository placementApplicationRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String PREFIX = "RS";
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

    private Student persistStudent(Department department, Course course) {
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

    // ------------------------------------------------------------------
    // Thin HTTP wrappers
    // ------------------------------------------------------------------

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String token) {
        return token == null ? builder : builder.header("Authorization", "Bearer " + token);
    }

    private MockHttpServletResponse postJson(String token, String url, Object body) throws Exception {
        return mockMvc.perform(
                        auth(post(url), token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andReturn()
                .getResponse();
    }

    private MockHttpServletResponse putJson(String token, String url, Object body) throws Exception {
        return mockMvc.perform(
                        auth(put(url), token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andReturn()
                .getResponse();
    }

    private MockHttpServletResponse patchJson(String token, String url, Object body) throws Exception {
        return mockMvc.perform(
                        auth(patch(url), token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andReturn()
                .getResponse();
    }

    private MockHttpServletResponse getReq(String token, String url) throws Exception {
        return mockMvc.perform(auth(get(url), token)).andReturn().getResponse();
    }

    private MockHttpServletResponse deleteReq(String token, String url) throws Exception {
        return mockMvc.perform(auth(delete(url), token)).andReturn().getResponse();
    }

    private byte[] getBytes(String token, String url) throws Exception {
        return mockMvc.perform(auth(get(url), token)).andReturn().getResponse().getContentAsByteArray();
    }

    private CompanyResponse createCompany(String adminToken) throws Exception {
        CompanyCreateRequest request =
                new CompanyCreateRequest(PREFIX + " Company " + tag(), null, null, null, null, null, null, null);
        MockHttpServletResponse response = postJson(adminToken, "/api/companies", request);
        assertThat(response.getStatus()).isEqualTo(201);
        return objectMapper.readValue(response.getContentAsString(), CompanyResponse.class);
    }

    private JobResponse createOpenJobNoRequirements(String adminToken, Long companyId) throws Exception {
        JobCreateRequest request =
                new JobCreateRequest(
                        companyId,
                        PREFIX + " Job " + tag(),
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
                        null,
                        LocalDateTime.now().plusDays(10),
                        null,
                        JobStatus.OPEN);
        MockHttpServletResponse response = postJson(adminToken, "/api/jobs", request);
        assertThat(response.getStatus()).isEqualTo(201);
        return objectMapper.readValue(response.getContentAsString(), JobResponse.class);
    }

    /** A resume request carrying real, distinctive, individually-identifiable content in every section. */
    private ResumeSaveRequest fullResumeRequest(String title, ResumeTemplate template, String uniqueMarker) {
        return new ResumeSaveRequest(
                title,
                template,
                "Ananya Rao " + uniqueMarker,
                "ananya.rao." + uniqueMarker + "@example.com",
                "+91-9000011111",
                "Chennai, India",
                "https://linkedin.com/in/ananya-" + uniqueMarker,
                "https://github.com/ananya-" + uniqueMarker,
                null,
                "Summary marker " + uniqueMarker + " describing distributed systems work.",
                List.of(
                        new ResumeEducationRequest(
                                "Institute " + uniqueMarker,
                                "B.Tech",
                                "Computer Science",
                                2022,
                                2026,
                                new BigDecimal("8.50"),
                                GradeScale.CGPA)),
                List.of(
                        new ResumeExperienceRequest(
                                "Company " + uniqueMarker,
                                "Intern " + uniqueMarker,
                                "Remote",
                                EmploymentType.INTERNSHIP,
                                LocalDate.of(2025, 5, 1),
                                LocalDate.of(2025, 7, 31),
                                false,
                                "Worked on project " + uniqueMarker + ".")),
                List.of(
                        new ResumeProjectRequest(
                                "Project " + uniqueMarker,
                                "A tool called " + uniqueMarker + ".",
                                "Go, Kafka",
                                null,
                                null,
                                null,
                                null)),
                List.of(
                        new ResumeCertificationRequest(
                                "Certification " + uniqueMarker, "Issuer " + uniqueMarker, null, null, null, null)),
                List.of(new ResumeSkillRequest("Skill" + uniqueMarker, SkillCategory.TECHNICAL, SkillProficiency.ADVANCED)),
                List.of(new ResumeAchievementRequest("Achievement " + uniqueMarker, null, null, null)));
    }

    private ResumeResponse createResume(String token, ResumeSaveRequest request) throws Exception {
        MockHttpServletResponse response = postJson(token, "/api/resumes", request);
        assertThat(response.getStatus()).isEqualTo(201);
        return objectMapper.readValue(response.getContentAsString(), ResumeResponse.class);
    }

    // ==================================================================
    // 1. PDF is a real, server-rendered document containing the student's real data
    // ==================================================================

    @Test
    void pdf_isValidNonEmptyPdf_andContainsTheStudentsRealEnteredData() throws Exception {
        Department dept = persistDepartment();
        Course course = persistCourse(dept);
        Student student = persistStudent(dept, course);
        String token = loginAsStudent(student);
        String marker = "M" + tag();

        ResumeResponse resume =
                createResume(token, fullResumeRequest(PREFIX + " Resume " + tag(), ResumeTemplate.CLASSIC, marker));

        MockHttpServletResponse response = mockMvc
                .perform(auth(get("/api/resumes/" + resume.id() + "/pdf"), token))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PDF_VALUE);
        byte[] bytes = response.getContentAsByteArray();

        // Real bytes: not zero-length, and a genuine %PDF header — never a fake artifact.
        assertThat(bytes.length).isGreaterThan(500);
        assertThat(new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        String text = extractPdfText(bytes);

        // §69: every one of these strings was typed by the "student" into the save
        // request above; none of them exists anywhere else in this test's fixtures, so
        // finding them in the PDF proves the renderer used the real saved data, not a
        // template placeholder or an empty document.
        assertThat(text).contains("Ananya Rao " + marker);
        assertThat(text).contains("Summary marker " + marker);
        assertThat(text).contains("Institute " + marker);
        assertThat(text).contains("Company " + marker);
        assertThat(text).contains("Intern " + marker);
        assertThat(text).contains("Project " + marker);
        assertThat(text).contains("Certification " + marker);
        assertThat(text).contains("Skill" + marker);
        assertThat(text).contains("Achievement " + marker);
    }

    @Test
    void pdf_threeTemplates_produceDistinctByteContent_allValidAndNonEmpty() throws Exception {
        Department dept = persistDepartment();
        Course course = persistCourse(dept);
        Student student = persistStudent(dept, course);
        String token = loginAsStudent(student);
        String marker = "T" + tag();

        ResumeResponse classic =
                createResume(token, fullResumeRequest(PREFIX + " Classic " + tag(), ResumeTemplate.CLASSIC, marker));
        ResumeResponse modern =
                createResume(token, fullResumeRequest(PREFIX + " Modern " + tag(), ResumeTemplate.MODERN, marker));
        ResumeResponse compact =
                createResume(token, fullResumeRequest(PREFIX + " Compact " + tag(), ResumeTemplate.COMPACT, marker));

        byte[] classicBytes = getBytes(token, "/api/resumes/" + classic.id() + "/pdf");
        byte[] modernBytes = getBytes(token, "/api/resumes/" + modern.id() + "/pdf");
        byte[] compactBytes = getBytes(token, "/api/resumes/" + compact.id() + "/pdf");

        for (byte[] b : List.of(classicBytes, modernBytes, compactBytes)) {
            assertThat(b.length).isGreaterThan(500);
            assertThat(new String(b, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        }
        // Three genuinely different layouts, not the same bytes with a different name.
        assertThat(classicBytes).isNotEqualTo(modernBytes);
        assertThat(modernBytes).isNotEqualTo(compactBytes);
        assertThat(classicBytes).isNotEqualTo(compactBytes);

        // All three still contain the real data regardless of layout.
        assertThat(extractPdfText(classicBytes)).contains("Ananya Rao " + marker);
        assertThat(extractPdfText(modernBytes)).contains("Ananya Rao " + marker);
        assertThat(extractPdfText(compactBytes)).contains("Ananya Rao " + marker);
    }

    /** Minimal OpenPDF-based text extractor — the same library the server renders with. */
    private String extractPdfText(byte[] pdfBytes) throws Exception {
        com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(pdfBytes);
        try {
            com.lowagie.text.pdf.parser.PdfTextExtractor extractor =
                    new com.lowagie.text.pdf.parser.PdfTextExtractor(reader);
            StringBuilder sb = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                sb.append(extractor.getTextFromPage(page));
            }
            return sb.toString();
        } finally {
            reader.close();
        }
    }

    // ==================================================================
    // 2. Attachment to a real placement application; version integrity; locking
    // ==================================================================

    @Test
    void resume_attachedToRealApplication_associationPersists_andLocksTheCorrectVersion() throws Exception {
        String admin = adminToken();
        Department dept = persistDepartment();
        Course course = persistCourse(dept);
        Student student = persistStudent(dept, course);
        String token = loginAsStudent(student);

        CompanyResponse company = createCompany(admin);
        JobResponse job = createOpenJobNoRequirements(admin, company.id());

        String markerV1 = "V1_" + tag();
        ResumeResponse resume =
                createResume(token, fullResumeRequest(PREFIX + " Attach " + tag(), ResumeTemplate.CLASSIC, markerV1));
        assertThat(resume.locked()).isFalse();
        assertThat(resume.lockedAt()).isNull();

        MockHttpServletResponse applyResponse =
                postJson(
                        token,
                        "/api/applications",
                        new PlacementApplicationCreateRequest(job.id(), resume.id(), "cover note"));
        assertThat(applyResponse.getStatus()).isEqualTo(201);
        PlacementApplicationResponse application =
                objectMapper.readValue(applyResponse.getContentAsString(), PlacementApplicationResponse.class);

        // The association persists in the DB, not just the response envelope.
        assertThat(application.resumeId()).isEqualTo(resume.id());
        var persistedApplication = placementApplicationRepository.findById(application.id()).orElseThrow();
        assertThat(persistedApplication.getResume()).isNotNull();
        assertThat(persistedApplication.getResume().getId()).isEqualTo(resume.id());

        // Attaching locks that exact version.
        Resume persistedResume = resumeRepository.findById(resume.id()).orElseThrow();
        assertThat(persistedResume.getLockedAt()).isNotNull();

        // A locked resume is read-only: update and delete are refused with 409, and the
        // rejected write must not have changed the row (re-fetch and re-check the marker).
        MockHttpServletResponse updateAttempt =
                putJson(
                        token,
                        "/api/resumes/" + resume.id(),
                        fullResumeRequest(resume.title(), ResumeTemplate.CLASSIC, "TAMPERED"));
        assertThat(updateAttempt.getStatus()).isEqualTo(409);
        assertThat(updateAttempt.getContentAsString()).contains("RESUME_LOCKED");

        MockHttpServletResponse deleteAttempt = deleteReq(token, "/api/resumes/" + resume.id());
        assertThat(deleteAttempt.getStatus()).isEqualTo(409);

        // Re-render the PDF after the failed tamper attempt: it must still be the ORIGINAL
        // content, proving the locked version is the exact artifact that was submitted —
        // never silently mutated by a rejected write.
        byte[] pdfAfterTamperAttempt = getBytes(token, "/api/resumes/" + resume.id() + "/pdf");
        String textAfterTamperAttempt = extractPdfText(pdfAfterTamperAttempt);
        assertThat(textAfterTamperAttempt).contains(markerV1);
        assertThat(textAfterTamperAttempt).doesNotContain("TAMPERED");
    }

    @Test
    void resume_duplicate_producesUnlockedIndependentVersion_bothVersionsIndependentlyRetrievable() throws Exception {
        String admin = adminToken();
        Department dept = persistDepartment();
        Course course = persistCourse(dept);
        Student student = persistStudent(dept, course);
        String token = loginAsStudent(student);

        CompanyResponse company = createCompany(admin);
        JobResponse job = createOpenJobNoRequirements(admin, company.id());

        String markerV1 = "DUPV1_" + tag();
        ResumeResponse original =
                createResume(token, fullResumeRequest(PREFIX + " Dup Original " + tag(), ResumeTemplate.CLASSIC, markerV1));

        // Lock it via a real application.
        MockHttpServletResponse applyResponse =
                postJson(token, "/api/applications", new PlacementApplicationCreateRequest(job.id(), original.id(), null));
        assertThat(applyResponse.getStatus()).isEqualTo(201);

        // Duplicate is the §35 escape hatch — works even though the source is locked.
        MockHttpServletResponse duplicateResponse =
                postJson(token, "/api/resumes/" + original.id() + "/duplicate", new ResumeDuplicateRequest("Duplicated Version"));
        assertThat(duplicateResponse.getStatus()).isEqualTo(201);
        ResumeResponse duplicate = objectMapper.readValue(duplicateResponse.getContentAsString(), ResumeResponse.class);

        assertThat(duplicate.id()).isNotEqualTo(original.id());
        assertThat(duplicate.locked()).isFalse();
        assertThat(duplicate.lockedAt()).isNull();

        // Both versions independently retrievable, each with the SAME copied content but
        // distinct identities — editing the duplicate does not touch the original.
        ResumeSaveRequest editedDuplicate = fullResumeRequest("Duplicated Version", ResumeTemplate.MODERN, "EDITED_" + tag());
        MockHttpServletResponse editResponse = putJson(token, "/api/resumes/" + duplicate.id(), editedDuplicate);
        assertThat(editResponse.getStatus()).isEqualTo(200);

        MockHttpServletResponse originalAfter = getReq(token, "/api/resumes/" + original.id());
        ResumeResponse originalReloaded = objectMapper.readValue(originalAfter.getContentAsString(), ResumeResponse.class);
        assertThat(originalReloaded.template()).isEqualTo(ResumeTemplate.CLASSIC);
        assertThat(extractPdfText(getBytes(token, "/api/resumes/" + original.id() + "/pdf"))).contains(markerV1);

        MockHttpServletResponse duplicateAfter = getReq(token, "/api/resumes/" + duplicate.id());
        ResumeResponse duplicateReloaded = objectMapper.readValue(duplicateAfter.getContentAsString(), ResumeResponse.class);
        assertThat(duplicateReloaded.template()).isEqualTo(ResumeTemplate.MODERN);
    }

    // ==================================================================
    // 3. Cross-student isolation — the actual §35 security guarantee
    // ==================================================================

    @Test
    void anotherStudent_cannotReadOrDownloadOrAttach_someoneElsesResume_byId() throws Exception {
        Department dept = persistDepartment();
        Course course = persistCourse(dept);
        Student owner = persistStudent(dept, course);
        Student attacker = persistStudent(dept, course);
        String ownerToken = loginAsStudent(owner);
        String attackerToken = loginAsStudent(attacker);

        String admin = adminToken();
        CompanyResponse company = createCompany(admin);
        JobResponse job = createOpenJobNoRequirements(admin, company.id());

        ResumeResponse ownerResume =
                createResume(ownerToken, fullResumeRequest(PREFIX + " Private " + tag(), ResumeTemplate.CLASSIC, "PRIV" + tag()));

        // GET by id — must be 404, never 403 (id must not be probeable), and never leak content.
        MockHttpServletResponse readAttempt = getReq(attackerToken, "/api/resumes/" + ownerResume.id());
        assertThat(readAttempt.getStatus()).isEqualTo(404);
        assertThat(readAttempt.getContentAsString()).doesNotContain(ownerResume.fullName());

        // PDF download by id — must also be 404, never a real PDF.
        MockHttpServletResponse pdfAttempt = getReq(attackerToken, "/api/resumes/" + ownerResume.id() + "/pdf");
        assertThat(pdfAttempt.getStatus()).isEqualTo(404);

        // Attempting to attach someone else's resume to your OWN application must fail
        // and must NOT create an application with that resume attached.
        MockHttpServletResponse applyAttempt =
                postJson(
                        attackerToken,
                        "/api/applications",
                        new PlacementApplicationCreateRequest(job.id(), ownerResume.id(), null));
        assertThat(applyAttempt.getStatus()).isEqualTo(404);

        long applicationsForJobByAttacker =
                placementApplicationRepository.findAll().stream()
                        .filter(a -> a.getJob().getId().equals(job.id()))
                        .filter(a -> a.getStudent().getId().equals(attacker.getId()))
                        .count();
        assertThat(applicationsForJobByAttacker).isZero();

        // The owner's resume must remain unlocked — the failed cross-student attach must
        // not have side-effected the victim's row.
        Resume reloadedOwnerResume = resumeRepository.findById(ownerResume.id()).orElseThrow();
        assertThat(reloadedOwnerResume.getLockedAt()).isNull();

        // Same guarantee via PATCH /api/applications/{id}/resume: attacker applies with no
        // resume, then tries to attach the victim's resume after the fact.
        MockHttpServletResponse bareApply =
                postJson(attackerToken, "/api/applications", new PlacementApplicationCreateRequest(job.id(), null, null));
        assertThat(bareApply.getStatus()).isEqualTo(201);
        PlacementApplicationResponse attackerApplication =
                objectMapper.readValue(bareApply.getContentAsString(), PlacementApplicationResponse.class);
        assertThat(attackerApplication.resumeId()).isNull();

        MockHttpServletResponse patchAttempt =
                patchJson(
                        attackerToken,
                        "/api/applications/" + attackerApplication.id() + "/resume",
                        new ApplicationResumeUpdateRequest(ownerResume.id()));
        assertThat(patchAttempt.getStatus()).isEqualTo(404);

        var reloadedAttackerApplication =
                placementApplicationRepository.findById(attackerApplication.id()).orElseThrow();
        assertThat(reloadedAttackerApplication.getResume()).isNull();
    }

    @Test
    void admin_canReadAndDownload_anyStudentsResume_forApplicantReview() throws Exception {
        String admin = adminToken();
        Department dept = persistDepartment();
        Course course = persistCourse(dept);
        Student student = persistStudent(dept, course);
        String token = loginAsStudent(student);
        String marker = "ADM" + tag();

        ResumeResponse resume =
                createResume(token, fullResumeRequest(PREFIX + " AdminView " + tag(), ResumeTemplate.CLASSIC, marker));

        MockHttpServletResponse adminRead = getReq(admin, "/api/resumes/" + resume.id());
        assertThat(adminRead.getStatus()).isEqualTo(200);

        byte[] adminPdf = getBytes(admin, "/api/resumes/" + resume.id() + "/pdf");
        assertThat(adminPdf.length).isGreaterThan(500);
        assertThat(extractPdfText(adminPdf)).contains("Ananya Rao " + marker);
    }

    // ==================================================================
    // 4. Generation is server-side: the same rendered PDF regardless of caller, never a
    //    client-supplied artifact, and §69 — no fabricated prefill content.
    // ==================================================================

    @Test
    void prefill_neverFabricatesInstitutionOrCgpa_whenStudentHasNoCourseOrMarks() throws Exception {
        User user = persistUser("bare", Role.STUDENT);
        Student bareStudent =
                studentRepository.save(
                        Student.builder().user(user).status(StudentStatus.PENDING).build());
        String token = login(user.getEmail(), RAW_PASSWORD);

        MockHttpServletResponse response = getReq(token, "/api/resumes/prefill");
        assertThat(response.getStatus()).isEqualTo(200);
        ResumePrefillResponse prefill =
                objectMapper.readValue(response.getContentAsString(), ResumePrefillResponse.class);

        // §69: no course means no fabricated institution/education row.
        assertThat(prefill.educations()).isEmpty();
        assertThat(prefill.fullName()).isEqualTo(user.getFullName());
        assertThat(prefill.email()).isEqualTo(user.getEmail());
    }

    @Test
    void pdf_sameResume_rendersSameContent_onRepeatedRequests_provingServerSideRegeneration() throws Exception {
        Department dept = persistDepartment();
        Course course = persistCourse(dept);
        Student student = persistStudent(dept, course);
        String token = loginAsStudent(student);
        String marker = "DET" + tag();

        ResumeResponse resume =
                createResume(token, fullResumeRequest(PREFIX + " Deterministic " + tag(), ResumeTemplate.CLASSIC, marker));

        byte[] first = getBytes(token, "/api/resumes/" + resume.id() + "/pdf");
        byte[] second = getBytes(token, "/api/resumes/" + resume.id() + "/pdf");
        // Rendered on demand, server-side, from the same stored rows both times. OpenPDF
        // embeds a fresh random /ID per render (standard PDF behaviour, not app content),
        // so raw bytes legitimately differ — the invariant that actually matters is that
        // both renders carry the identical REAL text content, proving the server re-reads
        // the same saved rows rather than serving a cached or client-supplied artifact.
        assertThat(first.length).isGreaterThan(500);
        assertThat(second.length).isGreaterThan(500);
        assertThat(extractPdfText(first)).isEqualTo(extractPdfText(second));
        assertThat(extractPdfText(first)).contains("Ananya Rao " + marker);
    }

    // ==================================================================
    // 5. No password or hash ever appears in any resume-related response
    // ==================================================================

    @Test
    void noSecretLeaksAcrossResumeEndpoints() throws Exception {
        Department dept = persistDepartment();
        Course course = persistCourse(dept);
        Student student = persistStudent(dept, course);
        String token = loginAsStudent(student);

        ResumeResponse resume =
                createResume(token, fullResumeRequest(PREFIX + " Secrets " + tag(), ResumeTemplate.CLASSIC, "SEC" + tag()));

        String createBody = getReq(token, "/api/resumes/" + resume.id()).getContentAsString();
        String meBody = getReq(token, "/api/resumes/me").getContentAsString();
        String prefillBody = getReq(token, "/api/resumes/prefill").getContentAsString();

        for (String body : List.of(createBody, meBody, prefillBody)) {
            assertThat(body).doesNotContainIgnoringCase("password");
            assertThat(body).doesNotContain("$2a$");
            assertThat(body).doesNotContain("\tat ");
            assertThat(body).doesNotContain("Caused by:");
        }
    }
}
