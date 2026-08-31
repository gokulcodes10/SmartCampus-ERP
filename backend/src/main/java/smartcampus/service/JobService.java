package smartcampus.service;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.JobCreateRequest;
import smartcampus.dto.JobDepartmentRef;
import smartcampus.dto.JobResponse;
import smartcampus.dto.JobStatusUpdateRequest;
import smartcampus.dto.JobUpdateRequest;
import smartcampus.dto.NotificationDispatch;
import smartcampus.dto.PageResponse;
import smartcampus.entity.Company;
import smartcampus.entity.Department;
import smartcampus.entity.Job;
import smartcampus.entity.JobEligibleDepartment;
import smartcampus.entity.JobStatus;
import smartcampus.entity.JobType;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.User;
import smartcampus.exception.BadRequestException;
import smartcampus.exception.DuplicateResourceException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.CompanyRepository;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.JobEligibleDepartmentRepository;
import smartcampus.repository.JobRepository;
import smartcampus.repository.NotificationRecipientRepository;
import smartcampus.repository.PlacementApplicationRepository;
import smartcampus.repository.projection.JobStatusCount;

/**
 * §33-§35: placement drives (job postings), their §34 eligibility criteria and their
 * §53 application deadline / lifecycle.
 *
 * <p>{@link #loadVisibleJob(Long, User)} is the single definition of "can this caller
 * see this drive" and is a frozen cross-task signature — {@code
 * PlacementEligibilityService} and {@code PlacementApplicationService} both call it.
 * Every other visibility check in this class routes through it too.
 *
 * <p>Method security is not enabled on this build; every ADMIN-only method starts with
 * {@link ScopedWriteAuthorizer#requireAdmin}.
 */
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final JobEligibleDepartmentRepository jobEligibleDepartmentRepository;
    private final PlacementApplicationRepository placementApplicationRepository;
    private final ScopedWriteAuthorizer scopedWriteAuthorizer;
    private final NotificationRecipientRepository notificationRecipientRepository;
    private final NotificationService notificationService;

    public JobService(
            JobRepository jobRepository,
            CompanyRepository companyRepository,
            DepartmentRepository departmentRepository,
            JobEligibleDepartmentRepository jobEligibleDepartmentRepository,
            PlacementApplicationRepository placementApplicationRepository,
            ScopedWriteAuthorizer scopedWriteAuthorizer,
            NotificationRecipientRepository notificationRecipientRepository,
            NotificationService notificationService) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.departmentRepository = departmentRepository;
        this.jobEligibleDepartmentRepository = jobEligibleDepartmentRepository;
        this.placementApplicationRepository = placementApplicationRepository;
        this.scopedWriteAuthorizer = scopedWriteAuthorizer;
        this.notificationRecipientRepository = notificationRecipientRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public JobResponse create(JobCreateRequest request, User caller) {
        scopedWriteAuthorizer.requireAdmin(caller);

        validateSalaryRange(request.salaryMin(), request.salaryMax());
        validateDriveDate(request.applicationDeadline(), request.driveDate());

        JobStatus status = request.status() == null ? JobStatus.DRAFT : request.status();
        if (status != JobStatus.DRAFT && status != JobStatus.OPEN) {
            throw new BadRequestException("New drives may only be created as DRAFT or OPEN.");
        }

        Company company =
                companyRepository
                        .findById(request.companyId())
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Company not found: " + request.companyId()));

        String currency = normalizeCurrency(request.salaryCurrency());
        List<Department> departments = resolveDepartments(request.eligibleDepartmentIds());

        Job job =
                Job.builder()
                        .company(company)
                        .title(request.title())
                        .description(request.description())
                        .location(request.location())
                        .jobType(request.jobType())
                        .openings(request.openings())
                        .salaryMin(request.salaryMin())
                        .salaryMax(request.salaryMax())
                        .salaryCurrency(currency)
                        .minCgpa(request.minCgpa())
                        .minMarksPercentage(request.minMarksPercentage())
                        .graduationYear(request.graduationYear())
                        .applicationDeadline(request.applicationDeadline())
                        .driveDate(request.driveDate())
                        .status(status)
                        .postedBy(caller)
                        .build();

        try {
            job = jobRepository.save(job);
            jobRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw duplicateJobException();
        }

        if (!departments.isEmpty()) {
            Job savedJob = job;
            List<JobEligibleDepartment> rows =
                    departments.stream()
                            .map(d -> JobEligibleDepartment.builder().job(savedJob).department(d).build())
                            .toList();
            jobEligibleDepartmentRepository.saveAll(rows);
        }

        return toResponse(job);
    }

    @Transactional
    public JobResponse update(Long id, JobUpdateRequest request, User caller) {
        scopedWriteAuthorizer.requireAdmin(caller);
        Job job = findOrThrow(id);

        validateSalaryRange(request.salaryMin(), request.salaryMax());
        validateDriveDate(request.applicationDeadline(), request.driveDate());

        String currency = normalizeCurrency(request.salaryCurrency());
        List<Department> departments = resolveDepartments(request.eligibleDepartmentIds());

        job.setTitle(request.title());
        job.setDescription(request.description());
        job.setLocation(request.location());
        job.setJobType(request.jobType());
        job.setOpenings(request.openings());
        job.setSalaryMin(request.salaryMin());
        job.setSalaryMax(request.salaryMax());
        job.setSalaryCurrency(currency);
        job.setMinCgpa(request.minCgpa());
        job.setMinMarksPercentage(request.minMarksPercentage());
        job.setGraduationYear(request.graduationYear());
        job.setApplicationDeadline(request.applicationDeadline());
        job.setDriveDate(request.driveDate());

        try {
            job = jobRepository.save(job);
            jobRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw duplicateJobException();
        }

        jobEligibleDepartmentRepository.deleteByJobId(id);
        jobEligibleDepartmentRepository.flush();
        if (!departments.isEmpty()) {
            Job savedJob = job;
            List<JobEligibleDepartment> rows =
                    departments.stream()
                            .map(d -> JobEligibleDepartment.builder().job(savedJob).department(d).build())
                            .toList();
            jobEligibleDepartmentRepository.saveAll(rows);
        }

        return toResponse(job);
    }

    @Transactional
    public JobResponse updateStatus(Long id, JobStatusUpdateRequest request, User caller) {
        scopedWriteAuthorizer.requireAdmin(caller);
        Job job = findOrThrow(id);

        JobStatus from = job.getStatus();
        JobStatus to = request.status();

        if (!allowedTransitions(from).contains(to)) {
            throw new BadRequestException("Cannot move job from " + from.name() + " to " + to.name() + ".");
        }

        if (to == JobStatus.OPEN && !job.getApplicationDeadline().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("Cannot open a drive whose application deadline has already passed.");
        }

        job.setStatus(to);
        job = jobRepository.save(job);

        // Build the response BEFORE dispatching notifications: dispatchAll (below) issues
        // entityManager.clear() calls to bound memory on a large fan-out, which detaches
        // every entity in this shared persistence context — including `job` — and turns
        // any lazy field read afterwards (e.g. job.getPostedBy().getFullName()) into a
        // LazyInitializationException. Reading everything toResponse needs first sidesteps
        // that entirely; see NotificationService#dispatchAll's javadoc for the same note.
        JobResponse response = toResponse(job);

        // ---- B. PLACEMENT_UPDATE (Phase 11 hook) — only on the transition INTO OPEN. ----
        if (from != JobStatus.OPEN && to == JobStatus.OPEN) {
            try {
                dispatchDriveOpenNotifications(job);
            } catch (Exception ex) {
                log.warn("Failed to dispatch PLACEMENT_UPDATE for job {}: {}", job.getId(), ex.getMessage(), ex);
            }
        }

        return response;
    }

    /**
     * B. PLACEMENT_UPDATE broadcast: every ACTIVE student in the drive's eligible
     * departments, or every ACTIVE student when the drive restricts no department. This
     * is a broadcast, not an eligibility decision — it deliberately does NOT call {@code
     * PlacementEligibilityService}, which requires an ADMIN caller and runs full
     * per-student analytics; CGPA/marks eligibility is still checked when the student
     * opens the drive.
     */
    private void dispatchDriveOpenNotifications(Job job) {
        List<Long> eligibleDepartmentIds =
                jobEligibleDepartmentRepository.findByJobId(job.getId()).stream()
                        .map(row -> row.getDepartment().getId())
                        .toList();
        List<Long> recipientUserIds =
                eligibleDepartmentIds.isEmpty()
                        ? notificationRecipientRepository.findEnabledStudentUserIdsByStatus(StudentStatus.ACTIVE)
                        : notificationRecipientRepository.findEnabledStudentUserIdsByStatusAndDepartments(
                                StudentStatus.ACTIVE, eligibleDepartmentIds);
        Set<Long> distinctRecipientUserIds = new LinkedHashSet<>(recipientUserIds);

        String jobTitle = job.getTitle();
        String companyName = job.getCompany().getName();
        List<NotificationDispatch> commands =
                distinctRecipientUserIds.stream()
                        .map(
                                userId ->
                                        NotificationMessages.placementDriveOpen(
                                                userId, job.getId(), jobTitle, companyName))
                        .toList();
        notificationService.dispatchAll(commands);
    }

    @Transactional
    public void delete(Long id, User caller) {
        scopedWriteAuthorizer.requireAdmin(caller);
        Job job = findOrThrow(id);

        if (placementApplicationRepository.existsByJobId(id)) {
            throw new DuplicateResourceException(
                    "Cannot delete a drive that has applications; cancel it instead.");
        }

        jobEligibleDepartmentRepository.deleteByJobId(id);
        jobEligibleDepartmentRepository.flush();
        try {
            jobRepository.delete(job);
            jobRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException(
                    "Cannot delete a drive that has applications; cancel it instead.");
        }
    }

    @Transactional(readOnly = true)
    public JobResponse getById(Long id, User caller) {
        return toResponse(loadVisibleJob(id, caller));
    }

    @Transactional(readOnly = true)
    public PageResponse<JobResponse> list(
            Long companyId,
            JobType jobType,
            JobStatus status,
            Long departmentId,
            String search,
            Boolean acceptingOnly,
            User caller,
            Pageable pageable) {
        boolean admin = scopedWriteAuthorizer.isAdmin(caller);
        Specification<Job> spec =
                buildFilter(companyId, jobType, status, departmentId, search, acceptingOnly, admin);
        Page<Job> page = jobRepository.findAll(spec, pageable);

        List<Long> ids = page.getContent().stream().map(Job::getId).toList();
        Map<Long, List<JobDepartmentRef>> deptMap =
                jobEligibleDepartmentRepository.findByJobIdIn(ids).stream()
                        .collect(
                                Collectors.groupingBy(
                                        row -> row.getJob().getId(),
                                        Collectors.mapping(
                                                row -> new JobDepartmentRef(row.getDepartment().getId(), row.getDepartment().getName()),
                                                Collectors.toList())));
        Map<Long, Long> appCountMap =
                placementApplicationRepository.countGroupedByJobAndStatus(ids).stream()
                        .collect(
                                Collectors.groupingBy(
                                        JobStatusCount::getJobId, Collectors.summingLong(JobStatusCount::getTotal)));

        return PageResponse.of(
                page,
                job ->
                        toResponse(
                                job,
                                deptMap.getOrDefault(job.getId(), List.of()),
                                appCountMap.getOrDefault(job.getId(), 0L)));
    }

    /**
     * ***** CROSS-TASK CONTRACT — frozen signature, called by {@code
     * PlacementEligibilityService} and {@code PlacementApplicationService}. *****
     *
     * <p>404 when the job does not exist, and ALSO when the caller is not ADMIN and the
     * job's status is DRAFT or CANCELLED — 404, never 403, so an id is never probeable.
     */
    @Transactional(readOnly = true)
    public Job loadVisibleJob(Long jobId, User caller) {
        Job job = findOrThrow(jobId);
        if (!scopedWriteAuthorizer.isAdmin(caller)
                && (job.getStatus() == JobStatus.DRAFT || job.getStatus() == JobStatus.CANCELLED)) {
            throw new ResourceNotFoundException("Job not found: " + jobId);
        }
        return job;
    }

    private Job findOrThrow(Long id) {
        return jobRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Job not found: " + id));
    }

    private static void validateSalaryRange(java.math.BigDecimal min, java.math.BigDecimal max) {
        if (min != null && max != null && max.compareTo(min) < 0) {
            throw new BadRequestException("Maximum salary must be greater than or equal to minimum salary.");
        }
    }

    private static void validateDriveDate(LocalDateTime applicationDeadline, java.time.LocalDate driveDate) {
        if (driveDate != null && applicationDeadline != null && driveDate.isBefore(applicationDeadline.toLocalDate())) {
            throw new BadRequestException("Drive date must be on or after the application deadline.");
        }
    }

    private static String normalizeCurrency(String salaryCurrency) {
        if (salaryCurrency == null || salaryCurrency.isBlank()) {
            return "INR";
        }
        return salaryCurrency.toUpperCase();
    }

    private List<Department> resolveDepartments(List<Long> eligibleDepartmentIds) {
        if (eligibleDepartmentIds == null || eligibleDepartmentIds.isEmpty()) {
            return List.of();
        }
        Set<Long> distinctIds = new LinkedHashSet<>(eligibleDepartmentIds);
        List<Department> departments = new ArrayList<>();
        for (Long deptId : distinctIds) {
            Department department =
                    departmentRepository
                            .findById(deptId)
                            .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + deptId));
            departments.add(department);
        }
        return departments;
    }

    private static Set<JobStatus> allowedTransitions(JobStatus from) {
        return switch (from) {
            case DRAFT -> Set.of(JobStatus.OPEN, JobStatus.CANCELLED);
            case OPEN -> Set.of(JobStatus.CLOSED, JobStatus.CANCELLED);
            case CLOSED -> Set.of(JobStatus.OPEN, JobStatus.CANCELLED);
            case CANCELLED -> Set.of();
        };
    }

    private static DuplicateResourceException duplicateJobException() {
        return new DuplicateResourceException(
                "A drive with this title and deadline already exists for this company.");
    }

    /** Single-job response: fetches this job's own eligible-department rows and application count. */
    private JobResponse toResponse(Job job) {
        List<JobDepartmentRef> departments =
                jobEligibleDepartmentRepository.findByJobId(job.getId()).stream()
                        .map(row -> new JobDepartmentRef(row.getDepartment().getId(), row.getDepartment().getName()))
                        .toList();
        long applicationCount =
                placementApplicationRepository.countGroupedByJobAndStatus(List.of(job.getId())).stream()
                        .mapToLong(JobStatusCount::getTotal)
                        .sum();
        return toResponse(job, departments, applicationCount);
    }

    private JobResponse toResponse(Job job, List<JobDepartmentRef> eligibleDepartments, long applicationCount) {
        boolean accepting =
                job.getStatus() == JobStatus.OPEN && !LocalDateTime.now().isAfter(job.getApplicationDeadline());
        return new JobResponse(
                job.getId(),
                job.getCompany().getId(),
                job.getCompany().getName(),
                job.getTitle(),
                job.getDescription(),
                job.getLocation(),
                job.getJobType(),
                job.getOpenings(),
                job.getSalaryMin(),
                job.getSalaryMax(),
                job.getSalaryCurrency(),
                job.getMinCgpa(),
                job.getMinMarksPercentage(),
                job.getGraduationYear(),
                eligibleDepartments,
                job.getApplicationDeadline(),
                job.getDriveDate(),
                job.getStatus(),
                accepting,
                job.getPostedBy().getId(),
                job.getPostedBy().getFullName(),
                applicationCount,
                job.getCreatedAt(),
                job.getUpdatedAt());
    }

    private Specification<Job> buildFilter(
            Long companyId,
            JobType jobType,
            JobStatus status,
            Long departmentId,
            String search,
            Boolean acceptingOnly,
            boolean admin) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (companyId != null) {
                predicates.add(cb.equal(root.get("company").get("id"), companyId));
            }
            if (jobType != null) {
                predicates.add(cb.equal(root.get("jobType"), jobType));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (!admin) {
                // SECURITY: a non-admin must never see a DRAFT or CANCELLED drive,
                // regardless of what `status` was requested. Combined with the `status`
                // predicate above (when supplied), an out-of-range request yields an
                // empty page rather than a 403 — an id/status must not be probeable.
                predicates.add(root.get("status").in(JobStatus.OPEN, JobStatus.CLOSED));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + search.toLowerCase() + "%"));
            }
            if (Boolean.TRUE.equals(acceptingOnly)) {
                predicates.add(cb.equal(root.get("status"), JobStatus.OPEN));
                predicates.add(cb.greaterThanOrEqualTo(root.get("applicationDeadline"), LocalDateTime.now()));
            }
            if (departmentId != null) {
                Subquery<Long> restrictedJobIds = query.subquery(Long.class);
                var jed = restrictedJobIds.from(JobEligibleDepartment.class);
                restrictedJobIds.select(jed.get("job").get("id"));

                Subquery<Long> matchingJobIds = query.subquery(Long.class);
                var jed2 = matchingJobIds.from(JobEligibleDepartment.class);
                matchingJobIds.select(jed2.get("job").get("id"));
                matchingJobIds.where(cb.equal(jed2.get("department").get("id"), departmentId));

                predicates.add(
                        cb.or(
                                cb.not(cb.in(root.get("id")).value(restrictedJobIds)),
                                cb.in(root.get("id")).value(matchingJobIds)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
