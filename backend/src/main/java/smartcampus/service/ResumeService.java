package smartcampus.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.PageResponse;
import smartcampus.dto.ResumeAchievementRequest;
import smartcampus.dto.ResumeAchievementResponse;
import smartcampus.dto.ResumeCertificationRequest;
import smartcampus.dto.ResumeCertificationResponse;
import smartcampus.dto.ResumeDuplicateRequest;
import smartcampus.dto.ResumeEducationRequest;
import smartcampus.dto.ResumeEducationResponse;
import smartcampus.dto.ResumeExperienceRequest;
import smartcampus.dto.ResumeExperienceResponse;
import smartcampus.dto.ResumePrefillResponse;
import smartcampus.dto.ResumeProjectRequest;
import smartcampus.dto.ResumeProjectResponse;
import smartcampus.dto.ResumeResponse;
import smartcampus.dto.ResumeSaveRequest;
import smartcampus.dto.ResumeSkillRequest;
import smartcampus.dto.ResumeSkillResponse;
import smartcampus.dto.ResumeSummaryResponse;
import smartcampus.entity.Course;
import smartcampus.entity.Department;
import smartcampus.entity.GradeScale;
import smartcampus.entity.Resume;
import smartcampus.entity.ResumeAchievement;
import smartcampus.entity.ResumeCertification;
import smartcampus.entity.ResumeEducation;
import smartcampus.entity.ResumeExperience;
import smartcampus.entity.ResumeProject;
import smartcampus.entity.ResumeSkill;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.User;
import smartcampus.exception.BadRequestException;
import smartcampus.exception.DuplicateResourceException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.exception.ResumeLockedException;
import smartcampus.repository.ResumeAchievementRepository;
import smartcampus.repository.ResumeCertificationRepository;
import smartcampus.repository.ResumeEducationRepository;
import smartcampus.repository.ResumeExperienceRepository;
import smartcampus.repository.ResumeProjectRepository;
import smartcampus.repository.ResumeRepository;
import smartcampus.repository.ResumeSkillRepository;

/**
 * The §37 resume builder: CRUD over one student's saved resume versions, each with six
 * ordered sections, plus §69-honest prefill and on-demand PDF rendering.
 *
 * <p>Method security is NOT enabled on this build; every authorization rule below lives
 * in this service. {@code spring.jpa.open-in-view=false}, so every read method assembles
 * its full response DTO (including all six section lists) inside its own transaction -
 * {@link Resume} deliberately declares no {@code @OneToMany} collections, so sections are
 * always read back through their own repositories, in {@code (display_order, id)} order.
 *
 * <p><b>R-1:</b> every write method validates the ENTIRE request and resolves every
 * business rule (max resumes, duplicate title, lock state) before the first {@code save}
 * call. An unchecked throw after a save inside a {@code @Transactional} method silently
 * discards that save under Spring's default rollback-on-unchecked rule.
 *
 * <p><b>Locking (§35):</b> {@code lockedAt != null} means this version has been attached
 * to a placement application and is permanently read-only. {@link #update} and
 * {@link #delete} refuse such a resume with {@link ResumeLockedException}; only
 * {@link #duplicate} may still act on a locked version - it is the escape hatch that
 * produces a fresh, unlocked, editable copy. Nothing in this class ever sets or clears
 * {@code lockedAt}; a different agent's attach-to-application code owns that.
 */
@Service
public class ResumeService {

    /** A constant, not a config property - see Addendum 2 trap 5 (test properties shadow main). */
    private static final int MAX_RESUMES_PER_STUDENT = 20;

    private final ResumeRepository resumeRepository;
    private final ResumeEducationRepository resumeEducationRepository;
    private final ResumeExperienceRepository resumeExperienceRepository;
    private final ResumeProjectRepository resumeProjectRepository;
    private final ResumeCertificationRepository resumeCertificationRepository;
    private final ResumeSkillRepository resumeSkillRepository;
    private final ResumeAchievementRepository resumeAchievementRepository;
    private final ScopedWriteAuthorizer scopedWriteAuthorizer;
    private final MarksService marksService;
    private final ResumePdfService resumePdfService;

    public ResumeService(
            ResumeRepository resumeRepository,
            ResumeEducationRepository resumeEducationRepository,
            ResumeExperienceRepository resumeExperienceRepository,
            ResumeProjectRepository resumeProjectRepository,
            ResumeCertificationRepository resumeCertificationRepository,
            ResumeSkillRepository resumeSkillRepository,
            ResumeAchievementRepository resumeAchievementRepository,
            ScopedWriteAuthorizer scopedWriteAuthorizer,
            MarksService marksService,
            ResumePdfService resumePdfService) {
        this.resumeRepository = resumeRepository;
        this.resumeEducationRepository = resumeEducationRepository;
        this.resumeExperienceRepository = resumeExperienceRepository;
        this.resumeProjectRepository = resumeProjectRepository;
        this.resumeCertificationRepository = resumeCertificationRepository;
        this.resumeSkillRepository = resumeSkillRepository;
        this.resumeAchievementRepository = resumeAchievementRepository;
        this.scopedWriteAuthorizer = scopedWriteAuthorizer;
        this.marksService = marksService;
        this.resumePdfService = resumePdfService;
    }

    /** {@code POST /api/resumes} - STUDENT only. */
    @Transactional
    public ResumeResponse create(ResumeSaveRequest request, User caller) {
        Student student = requireStudentCaller(caller);
        validateSaveRequest(request);
        if (resumeRepository.countByStudentId(student.getId()) >= MAX_RESUMES_PER_STUDENT) {
            throw new BadRequestException(
                    "You already have " + MAX_RESUMES_PER_STUDENT
                            + " resumes, the maximum allowed. Delete one before creating another.");
        }
        if (resumeRepository.existsByStudentIdAndTitle(student.getId(), request.title())) {
            throw new DuplicateResourceException("You already have a resume titled \"" + request.title() + "\".");
        }

        Resume resume =
                Resume.builder()
                        .student(student)
                        .title(request.title())
                        .template(request.template())
                        .fullName(request.fullName())
                        .email(request.email())
                        .phone(request.phone())
                        .location(request.location())
                        .linkedinUrl(request.linkedinUrl())
                        .githubUrl(request.githubUrl())
                        .portfolioUrl(request.portfolioUrl())
                        .summary(request.summary())
                        .build();
        try {
            resume = resumeRepository.save(resume);
        } catch (DataIntegrityViolationException e) {
            // The existsBy check above and this insert are not atomic under concurrent
            // submits; uk_resumes_student_title is the actual guard.
            throw new DuplicateResourceException("You already have a resume titled \"" + request.title() + "\".");
        }

        writeSections(resume, request);
        return toResponse(resume);
    }

    /** {@code GET /api/resumes/me} - STUDENT only, own resumes. */
    @Transactional(readOnly = true)
    public PageResponse<ResumeSummaryResponse> myResumes(User caller, Pageable pageable) {
        Student student = requireStudentCaller(caller);
        Page<Resume> page = resumeRepository.findByStudentId(student.getId(), pageable);
        return PageResponse.of(page, this::toSummary);
    }

    /** {@code GET /api/resumes/{id}} - owner STUDENT or ADMIN; 404 (never 403) otherwise. */
    @Transactional(readOnly = true)
    public ResumeResponse getById(Long id, User caller) {
        return toResponse(loadForRead(id, caller));
    }

    /** {@code PUT /api/resumes/{id}} - owner STUDENT only; 409 if locked. */
    @Transactional
    public ResumeResponse update(Long id, ResumeSaveRequest request, User caller) {
        Student student = requireStudentCaller(caller);
        Resume resume = loadOwnedOrThrow(id, student);
        if (resume.getLockedAt() != null) {
            throw new ResumeLockedException(lockedMessage(resume));
        }
        validateSaveRequest(request);
        if (!resume.getTitle().equals(request.title())
                && resumeRepository.existsByStudentIdAndTitle(student.getId(), request.title())) {
            throw new DuplicateResourceException("You already have a resume titled \"" + request.title() + "\".");
        }

        resume.setTitle(request.title());
        resume.setTemplate(request.template());
        resume.setFullName(request.fullName());
        resume.setEmail(request.email());
        resume.setPhone(request.phone());
        resume.setLocation(request.location());
        resume.setLinkedinUrl(request.linkedinUrl());
        resume.setGithubUrl(request.githubUrl());
        resume.setPortfolioUrl(request.portfolioUrl());
        resume.setSummary(request.summary());
        try {
            resume = resumeRepository.save(resume);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException("You already have a resume titled \"" + request.title() + "\".");
        }

        writeSections(resume, request);
        return toResponse(resume);
    }

    /** {@code DELETE /api/resumes/{id}} - owner STUDENT only; 409 if locked. */
    @Transactional
    public void delete(Long id, User caller) {
        Student student = requireStudentCaller(caller);
        Resume resume = loadOwnedOrThrow(id, student);
        if (resume.getLockedAt() != null) {
            throw new ResumeLockedException(lockedMessage(resume));
        }
        deleteAllSections(id);
        resumeRepository.delete(resume);
    }

    /**
     * {@code POST /api/resumes/{id}/duplicate} - owner STUDENT only. Works on a locked
     * resume - this is the §35 escape hatch. Copies every field and section EXCEPT id,
     * lockedAt (left null), createdAt/updatedAt; uses the new title from the request.
     */
    @Transactional
    public ResumeResponse duplicate(Long id, ResumeDuplicateRequest request, User caller) {
        Student student = requireStudentCaller(caller);
        Resume source = loadOwnedOrThrow(id, student);
        if (resumeRepository.countByStudentId(student.getId()) >= MAX_RESUMES_PER_STUDENT) {
            throw new BadRequestException(
                    "You already have " + MAX_RESUMES_PER_STUDENT
                            + " resumes, the maximum allowed. Delete one before duplicating another.");
        }
        if (resumeRepository.existsByStudentIdAndTitle(student.getId(), request.title())) {
            throw new DuplicateResourceException("You already have a resume titled \"" + request.title() + "\".");
        }

        Resume copy =
                Resume.builder()
                        .student(student)
                        .title(request.title())
                        .template(source.getTemplate())
                        .fullName(source.getFullName())
                        .email(source.getEmail())
                        .phone(source.getPhone())
                        .location(source.getLocation())
                        .linkedinUrl(source.getLinkedinUrl())
                        .githubUrl(source.getGithubUrl())
                        .portfolioUrl(source.getPortfolioUrl())
                        .summary(source.getSummary())
                        .build();
        try {
            copy = resumeRepository.save(copy);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException("You already have a resume titled \"" + request.title() + "\".");
        }

        copySections(source.getId(), copy);
        return toResponse(copy);
    }

    /** {@code GET /api/resumes/prefill} - STUDENT only. §69: nothing here is fabricated. */
    @Transactional(readOnly = true)
    public ResumePrefillResponse prefill(User caller) {
        Student student = requireStudentCaller(caller);
        String suggestedTitle = "Resume " + (resumeRepository.countByStudentId(student.getId()) + 1);
        String fullName = student.getUser().getFullName();
        String email = student.getUser().getEmail();

        List<ResumeEducationRequest> educations;
        Course course = student.getCourse();
        if (course == null) {
            educations = List.of();
        } else {
            Department department = student.getDepartment();
            Integer admissionYear = student.getAdmissionYear();
            Integer endYear =
                    admissionYear == null
                            ? null
                            : admissionYear + (int) Math.ceil(course.getDurationSemesters() / 2.0);
            BigDecimal cgpa = marksService.mySummary(caller, null, null).cgpa();
            GradeScale gradeScale = cgpa == null ? null : GradeScale.CGPA;
            educations =
                    List.of(
                            new ResumeEducationRequest(
                                    "",
                                    course.getName(),
                                    department != null ? department.getName() : null,
                                    admissionYear,
                                    endYear,
                                    cgpa,
                                    gradeScale));
        }

        return new ResumePrefillResponse(suggestedTitle, fullName, email, null, null, educations);
    }

    /** Assembled by the controller into the {@code /pdf} response's headers and body. */
    public record ResumePdf(byte[] bytes, String fileName) {}

    /** {@code GET /api/resumes/{id}/pdf} - owner STUDENT or ADMIN; 404 (never 403) otherwise. */
    @Transactional(readOnly = true)
    public ResumePdf renderPdf(Long id, User caller) {
        ResumeResponse resume = toResponse(loadForRead(id, caller));
        byte[] bytes = resumePdfService.render(resume);
        String fileName = resumePdfService.fileName(resume);
        return new ResumePdf(bytes, fileName);
    }

    // ------------------------------------------------------------------
    // Authorization
    // ------------------------------------------------------------------

    /** STUDENT only; ADMIN/FACULTY get 403 (writes, prefill, myResumes are never 404). */
    private Student requireStudentCaller(User caller) {
        if (caller == null || caller.getRole() != Role.STUDENT) {
            throw new AccessDeniedException("This operation requires a STUDENT account.");
        }
        return scopedWriteAuthorizer.requireOwnStudent(caller);
    }

    /**
     * Loads a resume for a single-resume READ (getById, PDF): the owning STUDENT or any
     * ADMIN. Anyone else - a different student, FACULTY, or no caller - gets 404, never
     * 403, so an id is never probeable.
     */
    private Resume loadForRead(Long id, User caller) {
        if (scopedWriteAuthorizer.isAdmin(caller)) {
            return resumeRepository
                    .findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Resume not found: " + id));
        }
        if (caller == null || caller.getRole() != Role.STUDENT) {
            throw new ResourceNotFoundException("Resume not found: " + id);
        }
        Student student = scopedWriteAuthorizer.requireOwnStudent(caller);
        return loadOwnedOrThrow(id, student);
    }

    private Resume loadOwnedOrThrow(Long id, Student student) {
        return resumeRepository
                .findByIdAndStudentId(id, student.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found: " + id));
    }

    private static String lockedMessage(Resume resume) {
        return "This resume was attached to a placement application on " + resume.getLockedAt().toLocalDate()
                + " and can no longer be changed. Duplicate it to create a new version you can edit.";
    }

    // ------------------------------------------------------------------
    // Cross-field validation (mirrors the V9 CHECK constraints; all thrown
    // BEFORE any save - see R-1 in the class Javadoc)
    // ------------------------------------------------------------------

    private void validateSaveRequest(ResumeSaveRequest request) {
        for (ResumeEducationRequest edu : orEmpty(request.educations())) {
            if (edu.startYear() != null && edu.endYear() != null && edu.endYear() < edu.startYear()) {
                throw new BadRequestException(
                        "Education end year cannot be before start year for \"" + edu.institution() + "\".");
            }
            boolean hasValue = edu.gradeValue() != null;
            boolean hasScale = edu.gradeScale() != null;
            if (hasValue != hasScale) {
                throw new BadRequestException(
                        "Education grade value and grade scale must both be present or both be absent for \""
                                + edu.institution() + "\".");
            }
            if (hasValue) {
                BigDecimal max = edu.gradeScale() == GradeScale.CGPA ? BigDecimal.TEN : new BigDecimal("100");
                if (edu.gradeValue().compareTo(BigDecimal.ZERO) < 0 || edu.gradeValue().compareTo(max) > 0) {
                    throw new BadRequestException(
                            "Education grade value " + edu.gradeValue() + " is out of range for "
                                    + edu.gradeScale() + " (\"" + edu.institution() + "\").");
                }
            }
        }

        for (ResumeExperienceRequest exp : orEmpty(request.experiences())) {
            if (exp.currentPosition() && exp.endDate() != null) {
                throw new BadRequestException(
                        "A current position must not have an end date (\"" + exp.companyName() + "\").");
            }
            if (!exp.currentPosition() && exp.endDate() == null) {
                throw new BadRequestException(
                        "A past position must have an end date (\"" + exp.companyName() + "\").");
            }
            if (exp.endDate() != null && exp.endDate().isBefore(exp.startDate())) {
                throw new BadRequestException(
                        "Experience end date cannot be before start date (\"" + exp.companyName() + "\").");
            }
        }

        for (ResumeProjectRequest project : orEmpty(request.projects())) {
            if (project.startDate() != null
                    && project.endDate() != null
                    && project.endDate().isBefore(project.startDate())) {
                throw new BadRequestException(
                        "Project end date cannot be before start date (\"" + project.name() + "\").");
            }
        }

        for (ResumeCertificationRequest cert : orEmpty(request.certifications())) {
            if (cert.issueDate() != null
                    && cert.expiryDate() != null
                    && cert.expiryDate().isBefore(cert.issueDate())) {
                throw new BadRequestException(
                        "Certification expiry date cannot be before issue date (\"" + cert.name() + "\").");
            }
        }

        Set<String> seenSkillNames = new HashSet<>();
        for (ResumeSkillRequest skill : orEmpty(request.skills())) {
            if (!seenSkillNames.add(skill.name().toLowerCase())) {
                throw new BadRequestException("Duplicate skill \"" + skill.name() + "\" in the same resume.");
            }
        }
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    // ------------------------------------------------------------------
    // Section replace (update/create) and copy (duplicate)
    // ------------------------------------------------------------------

    /**
     * Wholesale replace: deletes every existing row of all six sections, then inserts
     * the request's rows. For {@link #create}, the deletes are no-ops (nothing exists
     * yet) but keep this one code path shared. Deletes MUST run for every section before
     * any insert runs, or a skill "re-added" in the same request would collide with
     * itself on {@code uk_resume_skills_resume_name} (R-3) - the six repositories'
     * {@code deleteAllByResumeId} are bulk {@code @Modifying} deletes for exactly this
     * reason.
     */
    private void writeSections(Resume resume, ResumeSaveRequest request) {
        deleteAllSections(resume.getId());

        List<ResumeEducationRequest> educations = orEmpty(request.educations());
        for (int i = 0; i < educations.size(); i++) {
            ResumeEducationRequest e = educations.get(i);
            resumeEducationRepository.save(
                    ResumeEducation.builder()
                            .resume(resume)
                            .institution(e.institution())
                            .degree(e.degree())
                            .fieldOfStudy(e.fieldOfStudy())
                            .startYear(e.startYear())
                            .endYear(e.endYear())
                            .gradeValue(e.gradeValue())
                            .gradeScale(e.gradeScale())
                            .displayOrder(i)
                            .build());
        }

        List<ResumeExperienceRequest> experiences = orEmpty(request.experiences());
        for (int i = 0; i < experiences.size(); i++) {
            ResumeExperienceRequest e = experiences.get(i);
            resumeExperienceRepository.save(
                    ResumeExperience.builder()
                            .resume(resume)
                            .companyName(e.companyName())
                            .roleTitle(e.roleTitle())
                            .location(e.location())
                            .employmentType(e.employmentType())
                            .startDate(e.startDate())
                            .endDate(e.endDate())
                            .currentPosition(e.currentPosition())
                            .description(e.description())
                            .displayOrder(i)
                            .build());
        }

        List<ResumeProjectRequest> projects = orEmpty(request.projects());
        for (int i = 0; i < projects.size(); i++) {
            ResumeProjectRequest p = projects.get(i);
            resumeProjectRepository.save(
                    ResumeProject.builder()
                            .resume(resume)
                            .name(p.name())
                            .description(p.description())
                            .techStack(p.techStack())
                            .projectUrl(p.projectUrl())
                            .repositoryUrl(p.repositoryUrl())
                            .startDate(p.startDate())
                            .endDate(p.endDate())
                            .displayOrder(i)
                            .build());
        }

        List<ResumeCertificationRequest> certifications = orEmpty(request.certifications());
        for (int i = 0; i < certifications.size(); i++) {
            ResumeCertificationRequest c = certifications.get(i);
            resumeCertificationRepository.save(
                    ResumeCertification.builder()
                            .resume(resume)
                            .name(c.name())
                            .issuer(c.issuer())
                            .issueDate(c.issueDate())
                            .expiryDate(c.expiryDate())
                            .credentialId(c.credentialId())
                            .credentialUrl(c.credentialUrl())
                            .displayOrder(i)
                            .build());
        }

        List<ResumeSkillRequest> skills = orEmpty(request.skills());
        for (int i = 0; i < skills.size(); i++) {
            ResumeSkillRequest s = skills.get(i);
            resumeSkillRepository.save(
                    ResumeSkill.builder()
                            .resume(resume)
                            .name(s.name())
                            .category(s.category())
                            .proficiency(s.proficiency())
                            .displayOrder(i)
                            .build());
        }

        List<ResumeAchievementRequest> achievements = orEmpty(request.achievements());
        for (int i = 0; i < achievements.size(); i++) {
            ResumeAchievementRequest a = achievements.get(i);
            resumeAchievementRepository.save(
                    ResumeAchievement.builder()
                            .resume(resume)
                            .title(a.title())
                            .description(a.description())
                            .issuer(a.issuer())
                            .achievedOn(a.achievedOn())
                            .displayOrder(i)
                            .build());
        }
    }

    private void deleteAllSections(Long resumeId) {
        resumeEducationRepository.deleteAllByResumeId(resumeId);
        resumeExperienceRepository.deleteAllByResumeId(resumeId);
        resumeProjectRepository.deleteAllByResumeId(resumeId);
        resumeCertificationRepository.deleteAllByResumeId(resumeId);
        resumeSkillRepository.deleteAllByResumeId(resumeId);
        resumeAchievementRepository.deleteAllByResumeId(resumeId);
    }

    /** Copies every section row of {@code sourceId} onto {@code copy}, preserving display order. */
    private void copySections(Long sourceId, Resume copy) {
        for (ResumeEducation e : resumeEducationRepository.findByResumeIdOrderByDisplayOrderAscIdAsc(sourceId)) {
            resumeEducationRepository.save(
                    ResumeEducation.builder()
                            .resume(copy)
                            .institution(e.getInstitution())
                            .degree(e.getDegree())
                            .fieldOfStudy(e.getFieldOfStudy())
                            .startYear(e.getStartYear())
                            .endYear(e.getEndYear())
                            .gradeValue(e.getGradeValue())
                            .gradeScale(e.getGradeScale())
                            .displayOrder(e.getDisplayOrder())
                            .build());
        }
        for (ResumeExperience e : resumeExperienceRepository.findByResumeIdOrderByDisplayOrderAscIdAsc(sourceId)) {
            resumeExperienceRepository.save(
                    ResumeExperience.builder()
                            .resume(copy)
                            .companyName(e.getCompanyName())
                            .roleTitle(e.getRoleTitle())
                            .location(e.getLocation())
                            .employmentType(e.getEmploymentType())
                            .startDate(e.getStartDate())
                            .endDate(e.getEndDate())
                            .currentPosition(e.isCurrentPosition())
                            .description(e.getDescription())
                            .displayOrder(e.getDisplayOrder())
                            .build());
        }
        for (ResumeProject p : resumeProjectRepository.findByResumeIdOrderByDisplayOrderAscIdAsc(sourceId)) {
            resumeProjectRepository.save(
                    ResumeProject.builder()
                            .resume(copy)
                            .name(p.getName())
                            .description(p.getDescription())
                            .techStack(p.getTechStack())
                            .projectUrl(p.getProjectUrl())
                            .repositoryUrl(p.getRepositoryUrl())
                            .startDate(p.getStartDate())
                            .endDate(p.getEndDate())
                            .displayOrder(p.getDisplayOrder())
                            .build());
        }
        for (ResumeCertification c :
                resumeCertificationRepository.findByResumeIdOrderByDisplayOrderAscIdAsc(sourceId)) {
            resumeCertificationRepository.save(
                    ResumeCertification.builder()
                            .resume(copy)
                            .name(c.getName())
                            .issuer(c.getIssuer())
                            .issueDate(c.getIssueDate())
                            .expiryDate(c.getExpiryDate())
                            .credentialId(c.getCredentialId())
                            .credentialUrl(c.getCredentialUrl())
                            .displayOrder(c.getDisplayOrder())
                            .build());
        }
        for (ResumeSkill s : resumeSkillRepository.findByResumeIdOrderByDisplayOrderAscIdAsc(sourceId)) {
            resumeSkillRepository.save(
                    ResumeSkill.builder()
                            .resume(copy)
                            .name(s.getName())
                            .category(s.getCategory())
                            .proficiency(s.getProficiency())
                            .displayOrder(s.getDisplayOrder())
                            .build());
        }
        for (ResumeAchievement a : resumeAchievementRepository.findByResumeIdOrderByDisplayOrderAscIdAsc(sourceId)) {
            resumeAchievementRepository.save(
                    ResumeAchievement.builder()
                            .resume(copy)
                            .title(a.getTitle())
                            .description(a.getDescription())
                            .issuer(a.getIssuer())
                            .achievedOn(a.getAchievedOn())
                            .displayOrder(a.getDisplayOrder())
                            .build());
        }
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    private ResumeResponse toResponse(Resume resume) {
        Long resumeId = resume.getId();
        List<ResumeEducationResponse> educations =
                resumeEducationRepository.findByResumeIdOrderByDisplayOrderAscIdAsc(resumeId).stream()
                        .map(this::toEducationResponse)
                        .toList();
        List<ResumeExperienceResponse> experiences =
                resumeExperienceRepository.findByResumeIdOrderByDisplayOrderAscIdAsc(resumeId).stream()
                        .map(this::toExperienceResponse)
                        .toList();
        List<ResumeProjectResponse> projects =
                resumeProjectRepository.findByResumeIdOrderByDisplayOrderAscIdAsc(resumeId).stream()
                        .map(this::toProjectResponse)
                        .toList();
        List<ResumeCertificationResponse> certifications =
                resumeCertificationRepository.findByResumeIdOrderByDisplayOrderAscIdAsc(resumeId).stream()
                        .map(this::toCertificationResponse)
                        .toList();
        List<ResumeSkillResponse> skills =
                resumeSkillRepository.findByResumeIdOrderByDisplayOrderAscIdAsc(resumeId).stream()
                        .map(this::toSkillResponse)
                        .toList();
        List<ResumeAchievementResponse> achievements =
                resumeAchievementRepository.findByResumeIdOrderByDisplayOrderAscIdAsc(resumeId).stream()
                        .map(this::toAchievementResponse)
                        .toList();

        return new ResumeResponse(
                resume.getId(),
                resume.getStudent().getId(),
                resume.getTitle(),
                resume.getTemplate(),
                resume.getFullName(),
                resume.getEmail(),
                resume.getPhone(),
                resume.getLocation(),
                resume.getLinkedinUrl(),
                resume.getGithubUrl(),
                resume.getPortfolioUrl(),
                resume.getSummary(),
                resume.getLockedAt() != null,
                resume.getLockedAt(),
                educations,
                experiences,
                projects,
                certifications,
                skills,
                achievements,
                resume.getCreatedAt(),
                resume.getUpdatedAt());
    }

    private ResumeSummaryResponse toSummary(Resume resume) {
        return new ResumeSummaryResponse(
                resume.getId(),
                resume.getTitle(),
                resume.getTemplate(),
                resume.getLockedAt() != null,
                resume.getLockedAt(),
                resume.getCreatedAt(),
                resume.getUpdatedAt());
    }

    private ResumeEducationResponse toEducationResponse(ResumeEducation e) {
        return new ResumeEducationResponse(
                e.getId(),
                e.getInstitution(),
                e.getDegree(),
                e.getFieldOfStudy(),
                e.getStartYear(),
                e.getEndYear(),
                e.getGradeValue(),
                e.getGradeScale(),
                e.getDisplayOrder());
    }

    private ResumeExperienceResponse toExperienceResponse(ResumeExperience e) {
        return new ResumeExperienceResponse(
                e.getId(),
                e.getCompanyName(),
                e.getRoleTitle(),
                e.getLocation(),
                e.getEmploymentType(),
                e.getStartDate(),
                e.getEndDate(),
                e.isCurrentPosition(),
                e.getDescription(),
                e.getDisplayOrder());
    }

    private ResumeProjectResponse toProjectResponse(ResumeProject p) {
        return new ResumeProjectResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getTechStack(),
                p.getProjectUrl(),
                p.getRepositoryUrl(),
                p.getStartDate(),
                p.getEndDate(),
                p.getDisplayOrder());
    }

    private ResumeCertificationResponse toCertificationResponse(ResumeCertification c) {
        return new ResumeCertificationResponse(
                c.getId(),
                c.getName(),
                c.getIssuer(),
                c.getIssueDate(),
                c.getExpiryDate(),
                c.getCredentialId(),
                c.getCredentialUrl(),
                c.getDisplayOrder());
    }

    private ResumeSkillResponse toSkillResponse(ResumeSkill s) {
        return new ResumeSkillResponse(
                s.getId(), s.getName(), s.getCategory(), s.getProficiency(), s.getDisplayOrder());
    }

    private ResumeAchievementResponse toAchievementResponse(ResumeAchievement a) {
        return new ResumeAchievementResponse(
                a.getId(), a.getTitle(), a.getDescription(), a.getIssuer(), a.getAchievedOn(), a.getDisplayOrder());
    }
}
