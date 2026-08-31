package smartcampus.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.AnalyticsStudentResponse;
import smartcampus.dto.EligibilityReason;
import smartcampus.dto.EligibilityReasonCode;
import smartcampus.dto.EligibleStudentRow;
import smartcampus.dto.JobDepartmentRef;
import smartcampus.dto.JobEligibilityResponse;
import smartcampus.dto.PageResponse;
import smartcampus.entity.Job;
import smartcampus.entity.JobStatus;
import smartcampus.entity.PlacementApplication;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.User;
import smartcampus.exception.BadRequestException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.JobEligibleDepartmentRepository;
import smartcampus.repository.PlacementApplicationRepository;
import smartcampus.repository.StudentRepository;

/**
 * The §34 eligibility engine — evaluates a student against a job/drive's criteria
 * (student status, eligible departments, graduation year, minimum CGPA, minimum marks
 * percentage) plus the §35 procedural blockers (drive not open, deadline passed,
 * already applied), and produces the exact reason set the checkpoint asserts on.
 *
 * <p>CGPA and marks percentage are never recomputed here — both are read live from
 * {@link AnalyticsService#myAnalytics} / {@link AnalyticsService#studentAnalytics}, the
 * single Phase 5 source of truth, and that call is also this feature's entire
 * authorization boundary: {@code myAnalytics} enforces {@code requireOwnStudent} and
 * {@code studentAnalytics} enforces {@code requireAdmin}. No second authorization path
 * is added here.
 *
 * <p>Graduation year is never stored; it is derived from {@code admissionYear} and the
 * student's course duration on every call (see {@link #deriveGraduationYear}).
 *
 * <p>Every method is {@code @Transactional(readOnly = true)}: {@code
 * spring.jpa.open-in-view=false}, and building the response touches LAZY associations
 * ({@code job.getCompany()}, {@code student.getCourse()}, {@code student.getDepartment()})
 * that are only reachable inside the owning transaction.
 */
@Service
public class PlacementEligibilityService {

    /**
     * The eight CRITERION codes (§34): any presence means {@code eligible = false}. The
     * remaining three codes (DRIVE_NOT_OPEN, DEADLINE_PASSED, ALREADY_APPLIED) are
     * BLOCKER codes and never appear in this set — they only affect {@code canApply}.
     */
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

    private final JobService jobService;
    private final JobEligibleDepartmentRepository jobEligibleDepartmentRepository;
    private final PlacementApplicationRepository placementApplicationRepository;
    private final StudentRepository studentRepository;
    private final AnalyticsService analyticsService;
    private final ScopedWriteAuthorizer scopedWriteAuthorizer;

    public PlacementEligibilityService(
            JobService jobService,
            JobEligibleDepartmentRepository jobEligibleDepartmentRepository,
            PlacementApplicationRepository placementApplicationRepository,
            StudentRepository studentRepository,
            AnalyticsService analyticsService,
            ScopedWriteAuthorizer scopedWriteAuthorizer) {
        this.jobService = jobService;
        this.jobEligibleDepartmentRepository = jobEligibleDepartmentRepository;
        this.placementApplicationRepository = placementApplicationRepository;
        this.studentRepository = studentRepository;
        this.analyticsService = analyticsService;
        this.scopedWriteAuthorizer = scopedWriteAuthorizer;
    }

    /**
     * {@code GET /api/jobs/{jobId}/eligibility} — a STUDENT evaluates their own
     * eligibility ({@code studentId} is ignored for this caller, never honoured), or an
     * ADMIN evaluates a named student's eligibility ({@code studentId} required).
     */
    @Transactional(readOnly = true)
    public JobEligibilityResponse evaluate(Long jobId, Long studentId, User caller) {
        Job job = jobService.loadVisibleJob(jobId, caller);

        boolean self;
        Student student;
        if (caller != null && caller.getRole() == Role.ADMIN) {
            if (studentId == null) {
                throw new BadRequestException("studentId is required when an ADMIN checks a student's eligibility.");
            }
            self = false;
            student =
                    studentRepository
                            .findById(studentId)
                            .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + studentId));
        } else if (caller != null && caller.getRole() == Role.STUDENT) {
            // studentId is deliberately ignored here: honouring it would let a student
            // read another student's eligibility, which is an authorization bypass.
            self = true;
            student = scopedWriteAuthorizer.requireOwnStudent(caller);
        } else {
            throw new AccessDeniedException(
                    "This operation is restricted to a student checking their own eligibility, or an admin.");
        }

        AnalyticsStudentResponse a =
                self
                        ? analyticsService.myAnalytics(caller, null, null, null)
                        : analyticsService.studentAnalytics(student.getId(), caller, null, null, null);

        return buildEligibilityResponse(job, student, a);
    }

    /**
     * {@code GET /api/jobs/{jobId}/eligible-students} — ADMIN only. Evaluating
     * eligibility calls {@link AnalyticsService} per student, a heavy aggregation, so
     * only the requested page of ACTIVE students (narrowed to the drive's eligible
     * departments when the drive restricts them) is ever evaluated.
     */
    @Transactional(readOnly = true)
    public PageResponse<EligibleStudentRow> eligibleStudents(Long jobId, User caller, Pageable pageable) {
        scopedWriteAuthorizer.requireAdmin(caller);
        Job job = jobService.loadVisibleJob(jobId, caller);
        List<JobDepartmentRef> eligibleDepartments = eligibleDepartments(job);
        Set<Long> deptIds = eligibleDepartments.stream().map(JobDepartmentRef::id).collect(Collectors.toSet());

        Specification<Student> spec =
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(cb.equal(root.get("status"), StudentStatus.ACTIVE));
                    if (!deptIds.isEmpty()) {
                        predicates.add(root.get("department").get("id").in(deptIds));
                    }
                    return cb.and(predicates.toArray(new Predicate[0]));
                };
        Page<Student> page = studentRepository.findAll(spec, pageable);

        List<Long> studentIds = page.getContent().stream().map(Student::getId).toList();
        Map<Long, PlacementApplication> existingByStudentId =
                studentIds.isEmpty()
                        ? Map.of()
                        : placementApplicationRepository
                                .findAll(
                                        (root, query, cb) ->
                                                cb.and(
                                                        cb.equal(root.get("job").get("id"), job.getId()),
                                                        root.get("student").get("id").in(studentIds)))
                                .stream()
                                .collect(Collectors.toMap(app -> app.getStudent().getId(), app -> app));

        return PageResponse.of(
                page,
                student -> buildEligibleStudentRow(student, job, eligibleDepartments, existingByStudentId, caller));
    }

    private JobEligibilityResponse buildEligibilityResponse(Job job, Student student, AnalyticsStudentResponse a) {
        List<JobDepartmentRef> eligibleDepartments = eligibleDepartments(job);
        PlacementApplication existing =
                placementApplicationRepository.findByJobIdAndStudentId(job.getId(), student.getId()).orElse(null);
        Integer derivedGraduationYear = deriveGraduationYear(student);

        List<EligibilityReason> reasons =
                evaluateReasons(
                        student.getStatus(),
                        a.departmentId(),
                        a.departmentName(),
                        derivedGraduationYear,
                        a.cgpa(),
                        a.marksPercentage(),
                        job,
                        eligibleDepartments,
                        existing);

        boolean eligible = reasons.stream().noneMatch(r -> CRITERION_CODES.contains(r.code()));
        boolean canApply = eligible && reasons.isEmpty();

        return new JobEligibilityResponse(
                job.getId(),
                job.getTitle(),
                job.getCompany().getId(),
                job.getCompany().getName(),
                job.getStatus(),
                job.getApplicationDeadline(),
                student.getId(),
                a.registerNumber(),
                a.studentName(),
                eligible,
                canApply,
                reasons,
                job.getMinCgpa(),
                a.cgpa(),
                job.getMinMarksPercentage(),
                a.marksPercentage(),
                job.getGraduationYear(),
                derivedGraduationYear,
                eligibleDepartments,
                a.departmentId(),
                a.departmentName(),
                existing != null ? existing.getId() : null,
                existing != null ? existing.getStatus() : null);
    }

    private EligibleStudentRow buildEligibleStudentRow(
            Student student,
            Job job,
            List<JobDepartmentRef> eligibleDepartments,
            Map<Long, PlacementApplication> existingByStudentId,
            User caller) {
        AnalyticsStudentResponse a = analyticsService.studentAnalytics(student.getId(), caller, null, null, null);
        Integer derivedGraduationYear = deriveGraduationYear(student);
        PlacementApplication existing = existingByStudentId.get(student.getId());

        List<EligibilityReason> reasons =
                evaluateReasons(
                        student.getStatus(),
                        a.departmentId(),
                        a.departmentName(),
                        derivedGraduationYear,
                        a.cgpa(),
                        a.marksPercentage(),
                        job,
                        eligibleDepartments,
                        existing);
        boolean eligible = reasons.stream().noneMatch(r -> CRITERION_CODES.contains(r.code()));

        return new EligibleStudentRow(
                student.getId(),
                a.registerNumber(),
                a.studentName(),
                student.getUser().getEmail(),
                a.departmentId(),
                a.departmentName(),
                a.courseId(),
                a.courseName(),
                a.currentSemester(),
                a.section(),
                derivedGraduationYear,
                a.cgpa(),
                a.marksPercentage(),
                eligible,
                existing != null,
                existing != null ? existing.getStatus() : null,
                reasons);
    }

    /**
     * Evaluates every §34 criterion and §35 blocker, in exactly the order the checkpoint
     * asserts on. Never short-circuits: a student failing three criteria receives three
     * reasons.
     */
    private List<EligibilityReason> evaluateReasons(
            StudentStatus studentStatus,
            Long studentDepartmentId,
            String studentDepartmentName,
            Integer derivedGraduationYear,
            BigDecimal cgpa,
            BigDecimal marksPercentage,
            Job job,
            List<JobDepartmentRef> eligibleDepartments,
            PlacementApplication existingApplication) {
        List<EligibilityReason> reasons = new ArrayList<>();

        // 1. PROFILE_NOT_ACTIVE
        if (studentStatus != StudentStatus.ACTIVE) {
            reasons.add(
                    new EligibilityReason(
                            EligibilityReasonCode.PROFILE_NOT_ACTIVE,
                            "Your student profile is "
                                    + studentStatus.name()
                                    + ", not ACTIVE. Only active students can apply to placement drives.",
                            "ACTIVE",
                            studentStatus.name()));
        }

        // 2. DEPARTMENT_NOT_ELIGIBLE
        if (!eligibleDepartments.isEmpty()) {
            boolean inSet =
                    studentDepartmentId != null
                            && eligibleDepartments.stream().anyMatch(d -> d.id().equals(studentDepartmentId));
            if (!inSet) {
                String names =
                        eligibleDepartments.stream().map(JobDepartmentRef::name).collect(Collectors.joining(", "));
                String deptDisplay = studentDepartmentName != null ? studentDepartmentName : "not set";
                reasons.add(
                        new EligibilityReason(
                                EligibilityReasonCode.DEPARTMENT_NOT_ELIGIBLE,
                                "This drive is open to " + names + " only. Your department is " + deptDisplay + ".",
                                names,
                                studentDepartmentName));
            }
        }

        // 3. GRADUATION_YEAR_UNKNOWN / GRADUATION_YEAR_MISMATCH
        if (job.getGraduationYear() != null) {
            if (derivedGraduationYear == null) {
                reasons.add(
                        new EligibilityReason(
                                EligibilityReasonCode.GRADUATION_YEAR_UNKNOWN,
                                "Your graduation year cannot be determined because your admission year or "
                                        + "course is not set on your profile. Contact the administration office.",
                                String.valueOf(job.getGraduationYear()),
                                null));
            } else if (!derivedGraduationYear.equals(job.getGraduationYear())) {
                reasons.add(
                        new EligibilityReason(
                                EligibilityReasonCode.GRADUATION_YEAR_MISMATCH,
                                "This drive is open to the "
                                        + job.getGraduationYear()
                                        + " graduating batch. You graduate in "
                                        + derivedGraduationYear
                                        + ".",
                                String.valueOf(job.getGraduationYear()),
                                String.valueOf(derivedGraduationYear)));
            }
        }

        // 4. CGPA_NOT_AVAILABLE / CGPA_BELOW_MINIMUM
        if (job.getMinCgpa() != null) {
            BigDecimal min = job.getMinCgpa();
            if (cgpa == null) {
                reasons.add(
                        new EligibilityReason(
                                EligibilityReasonCode.CGPA_NOT_AVAILABLE,
                                "This drive requires a minimum CGPA of "
                                        + min.toPlainString()
                                        + ", but you have no graded subjects yet, so your CGPA cannot be computed.",
                                min.toPlainString(),
                                null));
            } else if (cgpa.compareTo(min) < 0) {
                reasons.add(
                        new EligibilityReason(
                                EligibilityReasonCode.CGPA_BELOW_MINIMUM,
                                "This drive requires a minimum CGPA of "
                                        + min.toPlainString()
                                        + ". Yours is "
                                        + cgpa.toPlainString()
                                        + ".",
                                min.toPlainString(),
                                cgpa.toPlainString()));
            }
        }

        // 5. PERCENTAGE_NOT_AVAILABLE / PERCENTAGE_BELOW_MINIMUM
        if (job.getMinMarksPercentage() != null) {
            BigDecimal min = job.getMinMarksPercentage();
            if (marksPercentage == null) {
                reasons.add(
                        new EligibilityReason(
                                EligibilityReasonCode.PERCENTAGE_NOT_AVAILABLE,
                                "This drive requires a minimum aggregate of "
                                        + min.toPlainString()
                                        + "%, but you have no marks recorded yet, so your percentage cannot be"
                                        + " computed.",
                                min.toPlainString(),
                                null));
            } else if (marksPercentage.compareTo(min) < 0) {
                reasons.add(
                        new EligibilityReason(
                                EligibilityReasonCode.PERCENTAGE_BELOW_MINIMUM,
                                "This drive requires a minimum aggregate of "
                                        + min.toPlainString()
                                        + "%. Yours is "
                                        + marksPercentage.toPlainString()
                                        + "%.",
                                min.toPlainString(),
                                marksPercentage.toPlainString()));
            }
        }

        // 6. DRIVE_NOT_OPEN (blocker)
        if (job.getStatus() != JobStatus.OPEN) {
            reasons.add(
                    new EligibilityReason(
                            EligibilityReasonCode.DRIVE_NOT_OPEN,
                            "This drive is " + job.getStatus().name() + " and is not accepting applications.",
                            "OPEN",
                            job.getStatus().name()));
        }

        // 7. DEADLINE_PASSED (blocker) -- inclusive: now == deadline is still open.
        if (LocalDateTime.now().isAfter(job.getApplicationDeadline())) {
            reasons.add(
                    new EligibilityReason(
                            EligibilityReasonCode.DEADLINE_PASSED,
                            "The application deadline for this drive passed on "
                                    + job.getApplicationDeadline().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                    + ".",
                            job.getApplicationDeadline().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                            null));
        }

        // 8. ALREADY_APPLIED (blocker)
        if (existingApplication != null) {
            reasons.add(
                    new EligibilityReason(
                            EligibilityReasonCode.ALREADY_APPLIED,
                            "You have already applied to this drive on "
                                    + existingApplication
                                            .getAppliedAt()
                                            .toLocalDate()
                                            .format(DateTimeFormatter.ISO_LOCAL_DATE)
                                    + ".",
                            null,
                            existingApplication.getAppliedAt().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)));
        }

        return reasons;
    }

    private List<JobDepartmentRef> eligibleDepartments(Job job) {
        return jobEligibleDepartmentRepository.findByJobId(job.getId()).stream()
                .map(jed -> new JobDepartmentRef(jed.getDepartment().getId(), jed.getDepartment().getName()))
                .toList();
    }

    /**
     * Graduation year is deliberately never stored (see V8__placement.sql): it is
     * derived from {@code admissionYear + ceil(durationSemesters / 2)}. A {@code null}
     * result means it cannot be determined — never a guessed year, never "eligible by
     * default".
     */
    private Integer deriveGraduationYear(Student s) {
        if (s.getAdmissionYear() == null || s.getCourse() == null || s.getCourse().getDurationSemesters() == null) {
            return null;
        }
        return s.getAdmissionYear() + ((s.getCourse().getDurationSemesters() + 1) / 2);
    }
}
