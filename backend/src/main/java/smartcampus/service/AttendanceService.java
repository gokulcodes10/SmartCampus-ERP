package smartcampus.service;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.AttendanceBulkRequest;
import smartcampus.dto.AttendanceBulkResponse;
import smartcampus.dto.AttendanceClassSummaryEntry;
import smartcampus.dto.AttendanceClassSummaryResponse;
import smartcampus.dto.AttendanceMarkEntry;
import smartcampus.dto.AttendanceResponse;
import smartcampus.dto.AttendanceRosterEntry;
import smartcampus.dto.AttendanceRosterResponse;
import smartcampus.dto.AttendanceSubjectSummary;
import smartcampus.dto.AttendanceSummaryResponse;
import smartcampus.dto.AttendanceUpdateRequest;
import smartcampus.dto.PageResponse;
import smartcampus.entity.Attendance;
import smartcampus.entity.Enrollment;
import smartcampus.entity.EnrollmentStatus;
import smartcampus.entity.Faculty;
import smartcampus.entity.Student;
import smartcampus.entity.Subject;
import smartcampus.entity.User;
import smartcampus.exception.BadRequestException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.AttendanceRepository;
import smartcampus.repository.EnrollmentRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.SubjectRepository;
import smartcampus.repository.projection.AttendanceStudentTotals;
import smartcampus.repository.projection.AttendanceSubjectTotals;

/**
 * Business logic for the {@code attendance} resource: bulk roster marking, single-row
 * correction, and every percentage/summary view built from those rows.
 *
 * <p><b>Authorization.</b> Every write, and every read that would leak a class roster
 * (the roster screen and the class summary), routes through {@link
 * ScopedWriteAuthorizer#requireScopedWrite}, which is the single Phase 4 write gate
 * and ultimately delegates to {@link AcademicAccessGuard}. This class never queries
 * {@code FacultySubjectAssignmentRepository} directly and never re-implements the
 * assignment check. A {@code /me} read is instead gated by {@link
 * ScopedWriteAuthorizer#requireOwnStudent}, so a caller can only ever read their own
 * rows regardless of any id present in the request.
 *
 * <p><b>The attendance-percentage rule (G6)</b> is implemented exactly once, in {@link
 * #percentageOf(long, long)}: {@code heldClasses == 0} (which covers both "zero
 * records" and "every session CANCELLED") always yields a {@code null} percentage and
 * {@code lowAttendance = false} — a student with no held classes is never treated as
 * being at 0%. The rule itself (which statuses count as held/attended) is not restated
 * here; it lives in {@link smartcampus.entity.AttendanceStatus} and is only ever
 * consumed through {@link AttendanceRepository#summarizeByStudent} and {@link
 * AttendanceRepository#summarizeByClass}, whose default overloads already supply
 * {@code AttendanceStatus.heldStatuses()}/{@code attendedStatuses()}.
 *
 * <p>An overall percentage across several subjects is always the credit-blind
 * aggregate {@code sum(attendedClasses) / sum(heldClasses)}, never the arithmetic mean
 * of the per-subject percentages — see {@link #buildSummary}.
 */
@Service
public class AttendanceService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);

    private final AttendanceRepository attendanceRepository;
    private final SubjectRepository subjectRepository;
    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ScopedWriteAuthorizer scopedWriteAuthorizer;
    private final NotificationService notificationService;

    /**
     * The low-attendance threshold, admin-configurable. The inline {@code :75}
     * default is mandatory: {@code src/test/resources/application.properties}
     * shadows the main configuration file rather than merging with it, so a property
     * defined only in {@code src/main} would be invisible to the test context and
     * boot would fail there.
     */
    @Value("${smartcampus.attendance.minimum-percentage:75}")
    private BigDecimal minimumPercentage;

    public AttendanceService(
            AttendanceRepository attendanceRepository,
            SubjectRepository subjectRepository,
            StudentRepository studentRepository,
            EnrollmentRepository enrollmentRepository,
            ScopedWriteAuthorizer scopedWriteAuthorizer,
            NotificationService notificationService) {
        this.attendanceRepository = attendanceRepository;
        this.subjectRepository = subjectRepository;
        this.studentRepository = studentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.scopedWriteAuthorizer = scopedWriteAuthorizer;
        this.notificationService = notificationService;
    }

    // ------------------------------------------------------------------
    // Bulk marking (upsert)
    // ------------------------------------------------------------------

    @Transactional
    public AttendanceBulkResponse bulkMark(AttendanceBulkRequest request, User caller) {
        // The tuple gated here is the tuple from the request body itself, and this
        // happens before any lookup or write — the non-negotiable rule for this
        // endpoint.
        scopedWriteAuthorizer.requireScopedWrite(
                caller, request.subjectId(), request.academicYear(), request.semester(), request.section());

        Subject subject =
                subjectRepository
                        .findById(request.subjectId())
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Subject not found: " + request.subjectId()));

        // ---- Validate the entire batch before saving any row. ----

        Set<Long> seenStudentIds = new HashSet<>();
        for (AttendanceMarkEntry entry : request.entries()) {
            if (!seenStudentIds.add(entry.studentId())) {
                throw new BadRequestException(
                        "Duplicate studentId " + entry.studentId() + " in the same attendance batch.");
            }
        }

        List<Enrollment> activeEnrollments =
                enrollmentRepository
                        .findBySubjectIdAndAcademicYearAndSemesterAndSection(
                                request.subjectId(),
                                request.academicYear(),
                                request.semester(),
                                request.section())
                        .stream()
                        .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.ACTIVE)
                        .toList();
        Map<Long, Student> activeStudentsById = new HashMap<>();
        for (Enrollment enrollment : activeEnrollments) {
            activeStudentsById.put(enrollment.getStudent().getId(), enrollment.getStudent());
        }

        for (AttendanceMarkEntry entry : request.entries()) {
            if (!activeStudentsById.containsKey(entry.studentId())) {
                throw new BadRequestException(
                        "Student " + entry.studentId()
                                + " has no ACTIVE enrollment in this subject/academic year/semester/section.");
            }
        }

        // ---- Everything above only reads. Only now do we write. ----

        Faculty markedBy = scopedWriteAuthorizer.facultyOrNull(caller);

        int createdCount = 0;
        int updatedCount = 0;
        List<Attendance> toSave = new ArrayList<>(request.entries().size());
        for (AttendanceMarkEntry entry : request.entries()) {
            Attendance attendance =
                    attendanceRepository
                            .findByStudentIdAndSubjectIdAndAttendanceDateAndPeriod(
                                    entry.studentId(), request.subjectId(), request.date(), request.period())
                            .orElse(null);
            if (attendance != null) {
                attendance.setStatus(entry.status());
                attendance.setRemarks(entry.remarks());
                attendance.setMarkedByFaculty(markedBy);
                updatedCount++;
            } else {
                attendance =
                        Attendance.builder()
                                .student(activeStudentsById.get(entry.studentId()))
                                .subject(subject)
                                .academicYear(request.academicYear())
                                .semester(request.semester())
                                .section(request.section())
                                .attendanceDate(request.date())
                                .period(request.period())
                                .status(entry.status())
                                .remarks(entry.remarks())
                                .markedByFaculty(markedBy)
                                .build();
                createdCount++;
            }
            toSave.add(attendance);
        }

        List<Attendance> saved = attendanceRepository.saveAll(toSave);

        // ---- A. ATTENDANCE_WARNING (Phase 11 hook) ----
        // Marking attendance must never fail because a notification could not be
        // written, so every failure here is caught and logged, never rethrown.
        for (Long studentId : seenStudentIds) {
            try {
                dispatchAttendanceWarningIfLow(
                        activeStudentsById.get(studentId), subject, request.academicYear(), request.semester());
            } catch (Exception ex) {
                log.warn(
                        "Failed to dispatch ATTENDANCE_WARNING for student {} subject {}: {}",
                        studentId,
                        subject.getId(),
                        ex.getMessage(),
                        ex);
            }
        }

        List<AttendanceResponse> records =
                saved.stream()
                        .sorted(
                                Comparator.comparing(
                                        (Attendance a) -> a.getStudent().getRegisterNumber(),
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(AttendanceResponse::from)
                        .toList();

        return new AttendanceBulkResponse(
                subject.getId(),
                subject.getCode(),
                subject.getName(),
                request.academicYear(),
                request.semester(),
                request.section(),
                request.date(),
                request.period(),
                createdCount,
                updatedCount,
                records);
    }

    // ------------------------------------------------------------------
    // Roster
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AttendanceRosterResponse roster(
            Long subjectId,
            String academicYear,
            Integer semester,
            String section,
            LocalDate date,
            Integer period,
            User caller) {
        // A roster leaks the class list, so it is gated identically to a write.
        scopedWriteAuthorizer.requireScopedWrite(caller, subjectId, academicYear, semester, section);

        Subject subject =
                subjectRepository
                        .findById(subjectId)
                        .orElseThrow(() -> new ResourceNotFoundException("Subject not found: " + subjectId));

        List<Enrollment> activeEnrollments =
                enrollmentRepository
                        .findBySubjectIdAndAcademicYearAndSemesterAndSection(
                                subjectId, academicYear, semester, section)
                        .stream()
                        .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.ACTIVE)
                        .toList();

        List<Attendance> existing =
                attendanceRepository.findBySubjectIdAndAcademicYearAndSemesterAndSectionAndAttendanceDateAndPeriod(
                        subjectId, academicYear, semester, section, date, period);
        Map<Long, Attendance> existingByStudentId = new HashMap<>();
        for (Attendance attendance : existing) {
            existingByStudentId.put(attendance.getStudent().getId(), attendance);
        }

        List<AttendanceRosterEntry> entries =
                activeEnrollments.stream()
                        .map(Enrollment::getStudent)
                        .sorted(
                                Comparator.comparing(
                                        Student::getRegisterNumber,
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(
                                student -> {
                                    Attendance attendance = existingByStudentId.get(student.getId());
                                    return new AttendanceRosterEntry(
                                            student.getId(),
                                            student.getRegisterNumber(),
                                            student.getUser().getFullName(),
                                            attendance != null ? attendance.getId() : null,
                                            attendance != null ? attendance.getStatus() : null,
                                            attendance != null ? attendance.getRemarks() : null);
                                })
                        .toList();

        return new AttendanceRosterResponse(
                subject.getId(),
                subject.getCode(),
                subject.getName(),
                academicYear,
                semester,
                section,
                date,
                period,
                !existing.isEmpty(),
                entries);
    }

    // ------------------------------------------------------------------
    // Single-row correction
    // ------------------------------------------------------------------

    @Transactional
    public AttendanceResponse update(Long id, AttendanceUpdateRequest request, User caller) {
        Attendance attendance =
                attendanceRepository
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found: " + id));

        // Gated on the ROW'S OWN tuple, never a tuple supplied by the caller.
        scopedWriteAuthorizer.requireScopedWrite(
                caller,
                attendance.getSubject().getId(),
                attendance.getAcademicYear(),
                attendance.getSemester(),
                attendance.getSection());

        attendance.setStatus(request.status());
        attendance.setRemarks(request.remarks());
        attendance.setMarkedByFaculty(scopedWriteAuthorizer.facultyOrNull(caller));

        return AttendanceResponse.from(attendance);
    }

    // ------------------------------------------------------------------
    // Student self-service reads
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<AttendanceResponse> myAttendance(
            User caller, String academicYear, Integer semester, Long subjectId, int page, int size) {
        Student student = scopedWriteAuthorizer.requireOwnStudent(caller);
        Specification<Attendance> spec =
                buildFilter(student.getId(), academicYear, semester, subjectId);
        Pageable pageable =
                PageRequest.of(
                        page, size, Sort.by(Sort.Direction.DESC, "attendanceDate").and(Sort.by("period")));
        Page<Attendance> resultPage = attendanceRepository.findAll(spec, pageable);
        return PageResponse.of(resultPage, AttendanceResponse::from);
    }

    @Transactional(readOnly = true)
    public AttendanceSummaryResponse mySummary(User caller, String academicYear, Integer semester) {
        Student student = scopedWriteAuthorizer.requireOwnStudent(caller);
        return buildSummary(student, academicYear, semester);
    }

    // ------------------------------------------------------------------
    // Admin summary
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AttendanceSummaryResponse adminSummary(
            Long studentId, String academicYear, Integer semester, User caller) {
        scopedWriteAuthorizer.requireAdmin(caller);
        Student student =
                studentRepository
                        .findById(studentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + studentId));
        return buildSummary(student, academicYear, semester);
    }

    // ------------------------------------------------------------------
    // Faculty/admin class summary
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AttendanceClassSummaryResponse classSummary(
            Long subjectId, String academicYear, Integer semester, String section, User caller) {
        scopedWriteAuthorizer.requireScopedWrite(caller, subjectId, academicYear, semester, section);

        Subject subject =
                subjectRepository
                        .findById(subjectId)
                        .orElseThrow(() -> new ResourceNotFoundException("Subject not found: " + subjectId));

        List<AttendanceStudentTotals> totals =
                attendanceRepository.summarizeByClass(subjectId, academicYear, semester, section);

        List<AttendanceClassSummaryEntry> entries =
                totals.stream()
                        .map(
                                totalsRow -> {
                                    long held = nz(totalsRow.getHeldClasses());
                                    long attended = nz(totalsRow.getAttendedClasses());
                                    long cancelled = nz(totalsRow.getCancelledClasses());
                                    long total = nz(totalsRow.getTotalRecords());
                                    BigDecimal percentage = percentageOf(attended, held);
                                    boolean low =
                                            percentage != null && percentage.compareTo(minimumPercentage) < 0;
                                    return new AttendanceClassSummaryEntry(
                                            totalsRow.getStudentId(),
                                            totalsRow.getRegisterNumber(),
                                            totalsRow.getStudentName(),
                                            total,
                                            held,
                                            attended,
                                            cancelled,
                                            percentage,
                                            low);
                                })
                        .toList();

        int lowAttendanceCount =
                (int) entries.stream().filter(AttendanceClassSummaryEntry::lowAttendance).count();

        return new AttendanceClassSummaryResponse(
                subject.getId(),
                subject.getCode(),
                subject.getName(),
                academicYear,
                semester,
                section,
                minimumPercentage,
                entries.size(),
                lowAttendanceCount,
                entries);
    }

    // ------------------------------------------------------------------
    // Shared summary computation (G6)
    // ------------------------------------------------------------------

    private AttendanceSummaryResponse buildSummary(Student student, String academicYear, Integer semester) {
        List<AttendanceSubjectTotals> subjectTotals =
                attendanceRepository.summarizeByStudent(student.getId(), academicYear, semester, null);

        List<AttendanceSubjectSummary> subjects =
                subjectTotals.stream().map(this::toSubjectSummary).toList();

        long totalRecords = 0;
        long heldClasses = 0;
        long attendedClasses = 0;
        long cancelledClasses = 0;
        for (AttendanceSubjectTotals totalsRow : subjectTotals) {
            totalRecords += nz(totalsRow.getTotalRecords());
            heldClasses += nz(totalsRow.getHeldClasses());
            attendedClasses += nz(totalsRow.getAttendedClasses());
            cancelledClasses += nz(totalsRow.getCancelledClasses());
        }

        // Credit-blind aggregate over the WHOLE set, not the mean of the per-subject
        // percentages above — a 1-class subject must not weigh the same as a
        // 40-class subject.
        BigDecimal overallPercentage = percentageOf(attendedClasses, heldClasses);
        boolean lowAttendance =
                overallPercentage != null && overallPercentage.compareTo(minimumPercentage) < 0;

        return new AttendanceSummaryResponse(
                student.getId(),
                student.getRegisterNumber(),
                student.getUser().getFullName(),
                academicYear,
                semester,
                totalRecords,
                heldClasses,
                attendedClasses,
                cancelledClasses,
                overallPercentage,
                minimumPercentage,
                lowAttendance,
                subjects);
    }

    private AttendanceSubjectSummary toSubjectSummary(AttendanceSubjectTotals totalsRow) {
        long held = nz(totalsRow.getHeldClasses());
        long attended = nz(totalsRow.getAttendedClasses());
        long cancelled = nz(totalsRow.getCancelledClasses());
        long total = nz(totalsRow.getTotalRecords());
        BigDecimal percentage = percentageOf(attended, held);
        boolean low = percentage != null && percentage.compareTo(minimumPercentage) < 0;
        return new AttendanceSubjectSummary(
                totalsRow.getSubjectId(),
                totalsRow.getSubjectCode(),
                totalsRow.getSubjectName(),
                totalsRow.getCredits(),
                totalsRow.getAcademicYear(),
                totalsRow.getSemester(),
                total,
                held,
                attended,
                cancelled,
                percentage,
                low);
    }

    /**
     * A. ATTENDANCE_WARNING — reuses {@link AttendanceRepository#summarizeByStudent} and
     * {@link #percentageOf(long, long)} (the G6 rule is not restated here) to decide, for
     * one student in this batch, whether their attendance in this one subject/term is now
     * below {@link #minimumPercentage}. {@code heldClasses == 0} (no records yet, or every
     * session CANCELLED) yields a {@code null} percentage, which is deliberately never
     * "low" — see {@link #percentageOf}.
     */
    private void dispatchAttendanceWarningIfLow(
            Student student, Subject subject, String academicYear, Integer semester) {
        List<AttendanceSubjectTotals> totals =
                attendanceRepository.summarizeByStudent(student.getId(), academicYear, semester, subject.getId());
        if (totals.isEmpty()) {
            return;
        }
        AttendanceSubjectTotals totalsRow = totals.get(0);
        long held = nz(totalsRow.getHeldClasses());
        long attended = nz(totalsRow.getAttendedClasses());
        BigDecimal percentage = percentageOf(attended, held);
        if (percentage == null || percentage.compareTo(minimumPercentage) >= 0) {
            return;
        }
        notificationService.dispatch(
                NotificationMessages.attendanceWarning(
                        student.getUser().getId(),
                        subject.getId(),
                        subject.getCode(),
                        subject.getName(),
                        academicYear,
                        semester,
                        percentage,
                        minimumPercentage));
    }

    /**
     * The G6 formula, implemented exactly once. {@code heldClasses == 0} — which
     * covers both zero attendance records and a subject where every session was
     * CANCELLED — always yields {@code null}, never {@code 0}/{@code BigDecimal.ZERO}.
     */
    private static BigDecimal percentageOf(long attendedClasses, long heldClasses) {
        if (heldClasses == 0) {
            return null;
        }
        return BigDecimal.valueOf(attendedClasses)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(heldClasses), 2, RoundingMode.HALF_UP);
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    private Specification<Attendance> buildFilter(
            Long studentId, String academicYear, Integer semester, Long subjectId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("student").get("id"), studentId));
            if (academicYear != null && !academicYear.isBlank()) {
                predicates.add(cb.equal(root.get("academicYear"), academicYear));
            }
            if (semester != null) {
                predicates.add(cb.equal(root.get("semester"), semester));
            }
            if (subjectId != null) {
                predicates.add(cb.equal(root.get("subject").get("id"), subjectId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
