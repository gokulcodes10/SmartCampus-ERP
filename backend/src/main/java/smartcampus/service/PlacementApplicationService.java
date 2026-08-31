package smartcampus.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.ApplicationBulkStatusRequest;
import smartcampus.dto.ApplicationBulkStatusResponse;
import smartcampus.dto.ApplicationResumeUpdateRequest;
import smartcampus.dto.ApplicationStatusUpdateRequest;
import smartcampus.dto.BulkStatusSkip;
import smartcampus.dto.EligibilityReason;
import smartcampus.dto.EligibilityReasonCode;
import smartcampus.dto.JobEligibilityResponse;
import smartcampus.dto.PageResponse;
import smartcampus.dto.PlacementApplicationCreateRequest;
import smartcampus.dto.PlacementApplicationResponse;
import smartcampus.entity.ApplicationStatus;
import smartcampus.entity.Company;
import smartcampus.entity.Course;
import smartcampus.entity.Department;
import smartcampus.entity.Job;
import smartcampus.entity.PlacementApplication;
import smartcampus.entity.Resume;
import smartcampus.entity.Student;
import smartcampus.entity.User;
import smartcampus.exception.BadRequestException;
import smartcampus.exception.DuplicateResourceException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.PlacementApplicationRepository;
import smartcampus.repository.ResumeRepository;

/**
 * The §35 application flow and §36 admin status pipeline.
 *
 * <p>The §35 duplicate-application guard is {@code
 * uk_placement_applications_job_student} in the database, not a Java {@code if}: {@link
 * #apply} checks {@link PlacementApplicationRepository#existsByJobIdAndStudentId} first
 * for a clean 409 on the common path, but also catches {@link
 * DataIntegrityViolationException} around the insert and translates it to the same 409,
 * because the check and the insert are not atomic with respect to each other under
 * concurrent submits.
 *
 * <p>Every status transition — admin-driven or the student's own withdrawal — sets
 * {@code status}, {@code statusChangedAt} and {@code statusChangedBy} in the very same
 * save, or {@code chk_placement_applications_status_change_attributed} rejects the row
 * at flush time.
 *
 * <p>Every read method is {@code @Transactional(readOnly = true)}; every write method is
 * {@code @Transactional}. {@code spring.jpa.open-in-view=false}, and building a response
 * touches LAZY associations ({@code job.getCompany()}, {@code student.getUser()}, {@code
 * student.getDepartment()}, {@code student.getCourse()}, {@code
 * application.getStatusChangedBy()}, and (Phase 9) {@code application.getResume()}) that
 * are only reachable inside the owning transaction.
 */
@Service
public class PlacementApplicationService {

    /** The eight §34 CRITERION codes — see {@link PlacementEligibilityService}. */
    private static final Set<EligibilityReasonCode> CRITERION_CODES =
            EnumSet.of(
                    EligibilityReasonCode.PROFILE_NOT_ACTIVE,
                    EligibilityReasonCode.DEPARTMENT_NOT_ELIGIBLE,
                    EligibilityReasonCode.GRADUATION_YEAR_UNKNOWN,
                    EligibilityReasonCode.GRADUATION_YEAR_MISMATCH,
                    EligibilityReasonCode.CGPA_NOT_AVAILABLE,
                    EligibilityReasonCode.CGPA_BELOW_MINIMUM,
                    EligibilityReasonCode.PERCENTAGE_NOT_AVAILABLE,
                    EligibilityReasonCode.PERCENTAGE_BELOW_MINIMUM);

    /** §36 admin-driven transition table. WITHDRAWN never appears as a target here. */
    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ADMIN_TRANSITIONS = buildAdminTransitions();

    /** Statuses a student may withdraw an application from. */
    private static final Set<ApplicationStatus> STUDENT_WITHDRAWABLE_FROM =
            EnumSet.of(
                    ApplicationStatus.APPLIED,
                    ApplicationStatus.UNDER_REVIEW,
                    ApplicationStatus.SHORTLISTED,
                    ApplicationStatus.INTERVIEW_SCHEDULED);

    private static final Logger log = LoggerFactory.getLogger(PlacementApplicationService.class);

    private final JobService jobService;
    private final PlacementEligibilityService placementEligibilityService;
    private final PlacementApplicationRepository placementApplicationRepository;
    private final ScopedWriteAuthorizer scopedWriteAuthorizer;
    private final ResumeRepository resumeRepository;
    private final NotificationService notificationService;

    public PlacementApplicationService(
            JobService jobService,
            PlacementEligibilityService placementEligibilityService,
            PlacementApplicationRepository placementApplicationRepository,
            ScopedWriteAuthorizer scopedWriteAuthorizer,
            ResumeRepository resumeRepository,
            NotificationService notificationService) {
        this.jobService = jobService;
        this.placementEligibilityService = placementEligibilityService;
        this.placementApplicationRepository = placementApplicationRepository;
        this.scopedWriteAuthorizer = scopedWriteAuthorizer;
        this.resumeRepository = resumeRepository;
        this.notificationService = notificationService;
    }

    /**
     * {@code POST /api/applications} — STUDENT. Validates and throws BEFORE any save
     * (an unchecked throw after a save inside the same {@code @Transactional} method
     * would be silently rolled back — see Phase 2 trap 1 — so validation must come
     * first, not "save then check").
     */
    @Transactional
    public PlacementApplicationResponse apply(PlacementApplicationCreateRequest request, User caller) {
        Student student = scopedWriteAuthorizer.requireOwnStudent(caller);
        Job job = jobService.loadVisibleJob(request.jobId(), caller);

        JobEligibilityResponse eligibility = placementEligibilityService.evaluate(request.jobId(), null, caller);

        Optional<EligibilityReason> alreadyApplied = findReason(eligibility, EligibilityReasonCode.ALREADY_APPLIED);
        if (alreadyApplied.isPresent()) {
            throw new DuplicateResourceException(alreadyApplied.get().message());
        }
        Optional<EligibilityReason> driveNotOpen = findReason(eligibility, EligibilityReasonCode.DRIVE_NOT_OPEN);
        if (driveNotOpen.isPresent()) {
            throw new BadRequestException(driveNotOpen.get().message());
        }
        Optional<EligibilityReason> deadlinePassed = findReason(eligibility, EligibilityReasonCode.DEADLINE_PASSED);
        if (deadlinePassed.isPresent()) {
            throw new BadRequestException(deadlinePassed.get().message());
        }
        if (!eligibility.eligible()) {
            String joined =
                    eligibility.reasons().stream()
                            .filter(r -> CRITERION_CODES.contains(r.code()))
                            .map(EligibilityReason::message)
                            .collect(Collectors.joining("; "));
            throw new AccessDeniedException(joined);
        }

        // Resolve (and lock) the resume BEFORE the first save: an unchecked throw after
        // a save inside this @Transactional method would silently roll it back (Phase 2
        // trap 1), so every validation — including this lookup — must come first.
        Resume resume = resolveAndLockResume(request.resumeId(), student);

        PlacementApplication application =
                PlacementApplication.builder()
                        .job(job)
                        .student(student)
                        .status(ApplicationStatus.APPLIED)
                        .coverNote(request.coverNote())
                        .resume(resume)
                        .cgpaAtApplication(eligibility.studentCgpa())
                        .percentageAtApplication(eligibility.studentMarksPercentage())
                        .build();
        try {
            application = placementApplicationRepository.save(application);
        } catch (DataIntegrityViolationException e) {
            // The check above and this insert are not atomic under concurrent submits;
            // the unique key on (job_id, student_id) is the actual guard.
            throw new DuplicateResourceException("You have already applied to this drive.");
        }
        return toResponse(application);
    }

    /** {@code GET /api/applications/me} — STUDENT, own applications only. */
    @Transactional(readOnly = true)
    public PageResponse<PlacementApplicationResponse> myApplications(
            User caller, ApplicationStatus status, Pageable pageable) {
        Student student = scopedWriteAuthorizer.requireOwnStudent(caller);
        Specification<PlacementApplication> spec =
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(cb.equal(root.get("student").get("id"), student.getId()));
                    if (status != null) {
                        predicates.add(cb.equal(root.get("status"), status));
                    }
                    return cb.and(predicates.toArray(new Predicate[0]));
                };
        Page<PlacementApplication> page = placementApplicationRepository.findAll(spec, pageable);
        return PageResponse.of(page, this::toResponse);
    }

    /** {@code GET /api/applications} — ADMIN. {@code search} matches student name OR register number. */
    @Transactional(readOnly = true)
    public PageResponse<PlacementApplicationResponse> list(
            Long jobId,
            Long companyId,
            ApplicationStatus status,
            Long departmentId,
            String search,
            User caller,
            Pageable pageable) {
        scopedWriteAuthorizer.requireAdmin(caller);
        Specification<PlacementApplication> spec =
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    if (jobId != null) {
                        predicates.add(cb.equal(root.get("job").get("id"), jobId));
                    }
                    if (companyId != null) {
                        predicates.add(cb.equal(root.get("job").get("company").get("id"), companyId));
                    }
                    if (status != null) {
                        predicates.add(cb.equal(root.get("status"), status));
                    }
                    if (departmentId != null) {
                        predicates.add(cb.equal(root.get("student").get("department").get("id"), departmentId));
                    }
                    if (search != null && !search.isBlank()) {
                        String like = "%" + search.toLowerCase() + "%";
                        Predicate byName = cb.like(cb.lower(root.get("student").get("user").get("fullName")), like);
                        Predicate byRegNo = cb.like(cb.lower(root.get("student").get("registerNumber")), like);
                        predicates.add(cb.or(byName, byRegNo));
                    }
                    return cb.and(predicates.toArray(new Predicate[0]));
                };
        Page<PlacementApplication> page = placementApplicationRepository.findAll(spec, pageable);
        return PageResponse.of(page, this::toResponse);
    }

    /** {@code GET /api/applications/{id}} — owner STUDENT or ADMIN; 404 (never 403) otherwise. */
    @Transactional(readOnly = true)
    public PlacementApplicationResponse getById(Long id, User caller) {
        PlacementApplication application = findOrThrow(id);
        if (!scopedWriteAuthorizer.isAdmin(caller)) {
            Student student = scopedWriteAuthorizer.requireOwnStudent(caller);
            if (!application.getStudent().getId().equals(student.getId())) {
                throw new ResourceNotFoundException("Application not found: " + id);
            }
        }
        return toResponse(application);
    }

    /** {@code PATCH /api/applications/{id}/status} — ADMIN. An admin may never set WITHDRAWN. */
    @Transactional
    public PlacementApplicationResponse updateStatus(Long id, ApplicationStatusUpdateRequest request, User caller) {
        scopedWriteAuthorizer.requireAdmin(caller);
        PlacementApplication application = findOrThrow(id);
        ApplicationStatus from = application.getStatus();
        ApplicationStatus to = request.status();

        if (to == ApplicationStatus.WITHDRAWN || !ADMIN_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new BadRequestException("Cannot move application from " + from.name() + " to " + to.name() + ".");
        }

        application.setStatus(to);
        application.setStatusChangedAt(LocalDateTime.now());
        application.setStatusChangedBy(caller);
        application.setDecisionNote(request.decisionNote());
        PlacementApplication saved = placementApplicationRepository.save(application);

        // ---- C. APPLICATION_UPDATE (Phase 11 hook) ----
        if (from != to) {
            dispatchApplicationStatusChanged(saved, from, to);
        }

        return toResponse(saved);
    }

    /** {@code POST /api/applications/bulk-status} — ADMIN. Illegal transitions are skipped, not fatal. */
    @Transactional
    public ApplicationBulkStatusResponse bulkUpdateStatus(ApplicationBulkStatusRequest request, User caller) {
        scopedWriteAuthorizer.requireAdmin(caller);
        ApplicationStatus to = request.status();
        List<BulkStatusSkip> skipped = new ArrayList<>();
        int updated = 0;

        if (to == ApplicationStatus.WITHDRAWN) {
            for (Long id : request.applicationIds()) {
                skipped.add(new BulkStatusSkip(id, "An admin cannot set status WITHDRAWN."));
            }
            return new ApplicationBulkStatusResponse(request.applicationIds().size(), 0, skipped);
        }

        for (Long id : request.applicationIds()) {
            Optional<PlacementApplication> maybeApplication = placementApplicationRepository.findById(id);
            if (maybeApplication.isEmpty()) {
                skipped.add(new BulkStatusSkip(id, "Application not found: " + id));
                continue;
            }
            PlacementApplication application = maybeApplication.get();
            ApplicationStatus from = application.getStatus();
            if (!ADMIN_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
                skipped.add(
                        new BulkStatusSkip(
                                id, "Cannot move application from " + from.name() + " to " + to.name() + "."));
                continue;
            }
            application.setStatus(to);
            application.setStatusChangedAt(LocalDateTime.now());
            application.setStatusChangedBy(caller);
            application.setDecisionNote(request.decisionNote());
            PlacementApplication saved = placementApplicationRepository.save(application);
            updated++;

            // ---- C. APPLICATION_UPDATE (Phase 11 hook) ----
            if (from != to) {
                dispatchApplicationStatusChanged(saved, from, to);
            }
        }
        return new ApplicationBulkStatusResponse(request.applicationIds().size(), updated, skipped);
    }

    /** {@code POST /api/applications/{id}/withdraw} — owner STUDENT only; 404 (never 403) for a non-owner. */
    @Transactional
    public PlacementApplicationResponse withdraw(Long id, User caller) {
        Student student = scopedWriteAuthorizer.requireOwnStudent(caller);
        PlacementApplication application = findOrThrow(id);
        if (!application.getStudent().getId().equals(student.getId())) {
            throw new ResourceNotFoundException("Application not found: " + id);
        }
        ApplicationStatus from = application.getStatus();
        if (!STUDENT_WITHDRAWABLE_FROM.contains(from)) {
            throw new BadRequestException(
                    "Cannot move application from " + from.name() + " to " + ApplicationStatus.WITHDRAWN.name() + ".");
        }
        application.setStatus(ApplicationStatus.WITHDRAWN);
        application.setStatusChangedAt(LocalDateTime.now());
        application.setStatusChangedBy(caller);
        PlacementApplication saved = placementApplicationRepository.save(application);
        return toResponse(saved);
    }

    /**
     * {@code PATCH /api/applications/{id}/resume} — owner STUDENT only; attaches (or
     * replaces) the resume on an already-submitted application. Not offered while the
     * application is in a terminal status (SELECTED/REJECTED/WITHDRAWN) — the artifact
     * attached to a decided application must not change after the fact. Deliberately
     * does NOT touch {@code status}, {@code statusChangedAt} or {@code statusChangedBy}:
     * attaching a resume is not a status transition, and writing {@code statusChangedBy}
     * without a status change would misattribute the decision in the §36 audit trail.
     */
    @Transactional
    public PlacementApplicationResponse updateResume(Long id, ApplicationResumeUpdateRequest request, User caller) {
        Student student = scopedWriteAuthorizer.requireOwnStudent(caller);
        PlacementApplication application = findOrThrow(id);
        if (!application.getStudent().getId().equals(student.getId())) {
            throw new ResourceNotFoundException("Application not found: " + id);
        }
        ApplicationStatus status = application.getStatus();
        if (!STUDENT_WITHDRAWABLE_FROM.contains(status)) {
            throw new BadRequestException(
                    "Cannot change the resume on an application that is already " + status.name() + ".");
        }
        Resume resume = resolveAndLockResume(request.resumeId(), student);
        application.setResume(resume);
        PlacementApplication saved = placementApplicationRepository.save(application);
        return toResponse(saved);
    }

    /**
     * C. APPLICATION_UPDATE — notifies the owning student of an admin-driven status
     * transition. Never called on the student's own {@link #withdraw}: the student
     * performed that action themselves. Every dispatch is wrapped: notifying must never
     * fail the status-transition write it is reporting on.
     */
    private void dispatchApplicationStatusChanged(
            PlacementApplication application, ApplicationStatus from, ApplicationStatus to) {
        try {
            notificationService.dispatch(
                    NotificationMessages.applicationStatusChanged(
                            application.getStudent().getUser().getId(),
                            application.getId(),
                            application.getJob().getTitle(),
                            from,
                            to,
                            application.getDecisionNote()));
        } catch (Exception ex) {
            log.warn(
                    "Failed to dispatch APPLICATION_UPDATE for application {}: {}",
                    application.getId(),
                    ex.getMessage(),
                    ex);
        }
    }

    /**
     * Resolves {@code resumeId} to a resume owned by {@code student} (returning {@code
     * null} when {@code resumeId} is null — "no resume attached" is legal), and locks it
     * on first attachment. A resume that exists but belongs to another student yields the
     * exact same 404 as one that does not exist at all — never a 403, never a message
     * that reveals the id exists (an id must not be probeable).
     */
    private Resume resolveAndLockResume(Long resumeId, Student student) {
        if (resumeId == null) {
            return null;
        }
        Resume resume =
                resumeRepository
                        .findByIdAndStudentId(resumeId, student.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Resume not found."));
        if (resume.getLockedAt() == null) {
            resume.setLockedAt(LocalDateTime.now());
        }
        return resume;
    }

    private PlacementApplication findOrThrow(Long id) {
        return placementApplicationRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + id));
    }

    private static Optional<EligibilityReason> findReason(JobEligibilityResponse response, EligibilityReasonCode code) {
        return response.reasons().stream().filter(r -> r.code() == code).findFirst();
    }

    private PlacementApplicationResponse toResponse(PlacementApplication application) {
        Job job = application.getJob();
        Company company = job.getCompany();
        Student student = application.getStudent();
        User user = student.getUser();
        Department department = student.getDepartment();
        Course course = student.getCourse();
        User changedBy = application.getStatusChangedBy();
        Resume resume = application.getResume();

        return new PlacementApplicationResponse(
                application.getId(),
                job.getId(),
                job.getTitle(),
                company.getId(),
                company.getName(),
                job.getStatus(),
                job.getApplicationDeadline(),
                job.getDriveDate(),
                student.getId(),
                student.getRegisterNumber(),
                user.getFullName(),
                user.getEmail(),
                department != null ? department.getId() : null,
                department != null ? department.getName() : null,
                course != null ? course.getId() : null,
                course != null ? course.getName() : null,
                student.getCurrentSemester(),
                student.getSection(),
                application.getStatus(),
                application.getCoverNote(),
                resume != null ? resume.getId() : null,
                resume != null ? resume.getTitle() : null,
                application.getCgpaAtApplication(),
                application.getPercentageAtApplication(),
                application.getAppliedAt(),
                application.getStatusChangedAt(),
                changedBy != null ? changedBy.getId() : null,
                changedBy != null ? changedBy.getFullName() : null,
                application.getDecisionNote(),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }

    private static Map<ApplicationStatus, Set<ApplicationStatus>> buildAdminTransitions() {
        Map<ApplicationStatus, Set<ApplicationStatus>> map = new EnumMap<>(ApplicationStatus.class);
        map.put(
                ApplicationStatus.APPLIED,
                EnumSet.of(ApplicationStatus.UNDER_REVIEW, ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED));
        map.put(
                ApplicationStatus.UNDER_REVIEW,
                EnumSet.of(ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED));
        map.put(
                ApplicationStatus.SHORTLISTED,
                EnumSet.of(
                        ApplicationStatus.INTERVIEW_SCHEDULED, ApplicationStatus.SELECTED, ApplicationStatus.REJECTED));
        map.put(
                ApplicationStatus.INTERVIEW_SCHEDULED,
                EnumSet.of(ApplicationStatus.SELECTED, ApplicationStatus.REJECTED));
        return Collections.unmodifiableMap(map);
    }
}
