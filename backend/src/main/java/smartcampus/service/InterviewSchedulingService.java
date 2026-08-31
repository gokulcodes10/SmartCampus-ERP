package smartcampus.service;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.InterviewRescheduleRequest;
import smartcampus.dto.InterviewResponse;
import smartcampus.dto.InterviewScheduleRequest;
import smartcampus.dto.InterviewStatusUpdateRequest;
import smartcampus.dto.InterviewUpdateRequest;
import smartcampus.dto.NotificationDispatch;
import smartcampus.dto.PageResponse;
import smartcampus.entity.Interview;
import smartcampus.entity.InterviewMode;
import smartcampus.entity.InterviewStatus;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.User;
import smartcampus.exception.BadRequestException;
import smartcampus.exception.DuplicateResourceException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.InterviewRepository;
import smartcampus.repository.StudentRepository;

/**
 * §39 interview scheduling: conflict detection, the status lifecycle and role-scoped
 * visibility. Authorization lives here, not in route rules — method security is not
 * enabled on this build (see {@code ScopedWriteAuthorizer}'s own javadoc for the same
 * convention in Phase 4).
 *
 * <p>Every schedule and reschedule call routes through {@link #requireNoConflict}, which
 * takes {@code PESSIMISTIC_WRITE} locks over the student's overlapping active interviews
 * inside the current read-write transaction — see {@link InterviewRepository#lockOverlapping}.
 * The DB's partial-unique {@code uk_interviews_student_active_slot} key is the
 * belt-and-braces backstop for the narrowest race (two live interviews for one student
 * starting at the exact same instant); a violation of it is caught and re-thrown as the
 * same {@link DuplicateResourceException} the lock-based check would have thrown, so a
 * raw {@link DataIntegrityViolationException} never reaches the client.
 */
@Service
public class InterviewSchedulingService {

    /** An interview only holds its slot while it is live. */
    private static final Set<InterviewStatus> BLOCKING =
            EnumSet.of(InterviewStatus.SCHEDULED, InterviewStatus.RESCHEDULED);

    /** §7 status lifecycle — SCHEDULED and RESCHEDULED both move forward identically; the rest are terminal. */
    private static final Map<InterviewStatus, Set<InterviewStatus>> ALLOWED_TRANSITIONS =
            Map.of(
                    InterviewStatus.SCHEDULED,
                            EnumSet.of(
                                    InterviewStatus.RESCHEDULED,
                                    InterviewStatus.COMPLETED,
                                    InterviewStatus.CANCELLED,
                                    InterviewStatus.NO_SHOW),
                    InterviewStatus.RESCHEDULED,
                            EnumSet.of(
                                    InterviewStatus.RESCHEDULED,
                                    InterviewStatus.COMPLETED,
                                    InterviewStatus.CANCELLED,
                                    InterviewStatus.NO_SHOW),
                    InterviewStatus.COMPLETED, EnumSet.noneOf(InterviewStatus.class),
                    InterviewStatus.CANCELLED, EnumSet.noneOf(InterviewStatus.class),
                    InterviewStatus.NO_SHOW, EnumSet.noneOf(InterviewStatus.class));

    private static final Logger log = LoggerFactory.getLogger(InterviewSchedulingService.class);

    private final InterviewRepository interviewRepository;
    private final StudentRepository studentRepository;
    private final ScopedWriteAuthorizer scopedWriteAuthorizer;
    private final NotificationService notificationService;

    public InterviewSchedulingService(
            InterviewRepository interviewRepository,
            StudentRepository studentRepository,
            ScopedWriteAuthorizer scopedWriteAuthorizer,
            NotificationService notificationService) {
        this.interviewRepository = interviewRepository;
        this.studentRepository = studentRepository;
        this.scopedWriteAuthorizer = scopedWriteAuthorizer;
        this.notificationService = notificationService;
    }

    // ------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------

    @Transactional
    public InterviewResponse schedule(InterviewScheduleRequest request, User caller) {
        if (caller == null) {
            throw new AccessDeniedException("Authentication is required for this operation.");
        }
        validateWindow(request.scheduledStart(), request.scheduledEnd());
        validateOnlineHasLink(request.mode(), request.meetingLink());

        Student student = resolveScheduleTargetStudent(request.studentId(), caller);

        Interview interview =
                Interview.builder()
                        .student(student)
                        .title(request.title())
                        .interviewType(request.interviewType())
                        .companyName(request.companyName())
                        .roundName(request.roundName())
                        .mode(request.mode())
                        .meetingLink(request.meetingLink())
                        .location(request.location())
                        .interviewerName(request.interviewerName())
                        .scheduledStart(request.scheduledStart())
                        .scheduledEnd(request.scheduledEnd())
                        .status(InterviewStatus.SCHEDULED)
                        .notes(request.notes())
                        .createdBy(caller)
                        .build();

        // requireNoConflict and the insert are in the SAME try: under real concurrent load,
        // MySQL can pick this transaction as the deadlock victim while it holds the
        // PESSIMISTIC_WRITE lock (CannotAcquireLockException), not only fail the unique
        // constraint on insert (DataIntegrityViolationException). Both mean the same thing
        // to the caller — this slot lost the race — so both become the same clean 409, never
        // a raw 500 leaking InnoDB's deadlock detection to the client.
        try {
            requireNoConflict(student.getId(), request.scheduledStart(), request.scheduledEnd(), null);
            interview = interviewRepository.saveAndFlush(interview);
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException ex) {
            throw slotTakenException();
        }

        // ---- D. INTERVIEW_UPDATE (Phase 11 hook) ----
        Interview scheduled = interview;
        dispatchInterviewUpdateUnlessSelf(
                caller,
                student,
                () -> NotificationMessages.interviewScheduled(
                        student.getUser().getId(), scheduled.getId(), scheduled.getTitle(), scheduled.getScheduledStart()));

        return InterviewResponse.from(interview);
    }

    private Student resolveScheduleTargetStudent(Long requestedStudentId, User caller) {
        if (caller.getRole() == Role.ADMIN) {
            if (requestedStudentId == null) {
                throw new BadRequestException("studentId is required when an admin schedules an interview.");
            }
            return studentRepository
                    .findById(requestedStudentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + requestedStudentId));
        }
        if (caller.getRole() == Role.STUDENT) {
            Student own = scopedWriteAuthorizer.requireOwnStudent(caller);
            if (requestedStudentId != null && !requestedStudentId.equals(own.getId())) {
                throw new AccessDeniedException("Students may only schedule interviews for themselves.");
            }
            return own;
        }
        throw new AccessDeniedException("This operation is restricted to students and admins.");
    }

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<InterviewResponse> list(
            InterviewStatus status,
            Long studentId,
            LocalDateTime from,
            LocalDateTime to,
            String q,
            User caller,
            Pageable pageable) {
        if (caller == null) {
            throw new AccessDeniedException("Authentication is required for this operation.");
        }
        Long effectiveStudentId;
        if (caller.getRole() == Role.ADMIN) {
            effectiveStudentId = studentId; // honoured, may be null (= all students)
        } else if (caller.getRole() == Role.STUDENT) {
            // A supplied studentId that is not the caller's own is silently ignored, never honoured.
            effectiveStudentId = scopedWriteAuthorizer.requireOwnStudent(caller).getId();
        } else {
            throw new AccessDeniedException("This operation is restricted to students and admins.");
        }

        Specification<Interview> spec = buildFilter(status, effectiveStudentId, from, to, q);
        Page<Interview> page = interviewRepository.findAll(spec, pageable);
        return PageResponse.of(page, InterviewResponse::from);
    }

    @Transactional(readOnly = true)
    public List<InterviewResponse> upcoming(User caller, int limit) {
        if (caller == null || caller.getRole() != Role.STUDENT) {
            throw new AccessDeniedException("This operation is restricted to students.");
        }
        Student student = scopedWriteAuthorizer.requireOwnStudent(caller);
        int clampedLimit = Math.min(Math.max(limit, 1), 20);
        return interviewRepository
                .findByStudentIdAndStatusInAndScheduledStartGreaterThanEqualOrderByScheduledStartAsc(
                        student.getId(), BLOCKING, LocalDateTime.now(), PageRequest.of(0, clampedLimit))
                .stream()
                .map(InterviewResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public InterviewResponse getById(Long id, User caller) {
        return InterviewResponse.from(loadVisible(id, caller));
    }

    /** Owner student or ADMIN only; every other caller sees a 404 — never a 403, so ids cannot be probed. */
    private Interview loadVisible(Long id, User caller) {
        if (caller == null) {
            throw new ResourceNotFoundException("Interview not found: " + id);
        }
        if (caller.getRole() == Role.ADMIN) {
            return interviewRepository
                    .findWithStudentById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + id));
        }
        if (caller.getRole() == Role.STUDENT) {
            Student student = studentRepository.findByUserId(caller.getId()).orElse(null);
            if (student != null) {
                return interviewRepository
                        .findByIdAndStudentId(id, student.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + id));
            }
        }
        throw new ResourceNotFoundException("Interview not found: " + id);
    }

    // ------------------------------------------------------------------
    // Update / reschedule / status / delete
    // ------------------------------------------------------------------

    @Transactional
    public InterviewResponse update(Long id, InterviewUpdateRequest request, User caller) {
        Interview interview = loadForWrite(id, caller);
        validateOnlineHasLink(request.mode(), request.meetingLink());

        interview.setTitle(request.title());
        interview.setInterviewType(request.interviewType());
        interview.setCompanyName(request.companyName());
        interview.setRoundName(request.roundName());
        interview.setMode(request.mode());
        interview.setMeetingLink(request.meetingLink());
        interview.setLocation(request.location());
        interview.setInterviewerName(request.interviewerName());
        interview.setNotes(request.notes());

        return InterviewResponse.from(interview);
    }

    @Transactional
    public InterviewResponse reschedule(Long id, InterviewRescheduleRequest request, User caller) {
        Interview interview = loadForWrite(id, caller);
        validateWindow(request.scheduledStart(), request.scheduledEnd());
        validateOnlineHasLink(interview.getMode(), interview.getMeetingLink());

        if (interview.getStatus() != InterviewStatus.SCHEDULED
                && interview.getStatus() != InterviewStatus.RESCHEDULED) {
            throw new BadRequestException(
                    "Only a SCHEDULED or RESCHEDULED interview can be rescheduled; current status is "
                            + interview.getStatus() + ".");
        }

        interview.setScheduledStart(request.scheduledStart());
        interview.setScheduledEnd(request.scheduledEnd());
        interview.setStatus(InterviewStatus.RESCHEDULED);

        // See schedule()'s identical try block for why requireNoConflict and the write share
        // one try/catch that also covers PessimisticLockingFailureException.
        try {
            requireNoConflict(
                    interview.getStudent().getId(),
                    request.scheduledStart(),
                    request.scheduledEnd(),
                    interview.getId());
            interview = interviewRepository.saveAndFlush(interview);
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException ex) {
            throw slotTakenException();
        }

        // ---- D. INTERVIEW_UPDATE (Phase 11 hook) ----
        Interview rescheduled = interview;
        dispatchInterviewUpdateUnlessSelf(
                caller,
                rescheduled.getStudent(),
                () -> NotificationMessages.interviewRescheduled(
                        rescheduled.getStudent().getUser().getId(),
                        rescheduled.getId(),
                        rescheduled.getTitle(),
                        rescheduled.getScheduledStart()));

        return InterviewResponse.from(interview);
    }

    @Transactional
    public InterviewResponse updateStatus(Long id, InterviewStatusUpdateRequest request, User caller) {
        Interview interview = loadForWrite(id, caller);

        InterviewStatus current = interview.getStatus();
        InterviewStatus target = request.status();
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new BadRequestException("Cannot move an interview from " + current + " to " + target + ".");
        }
        if (target == InterviewStatus.CANCELLED
                && (request.cancellationReason() == null || request.cancellationReason().isBlank())) {
            throw new BadRequestException("A cancellation reason is required when cancelling an interview.");
        }
        if (request.outcome() != null && target != InterviewStatus.COMPLETED) {
            throw new BadRequestException("An outcome may only be recorded when the interview is COMPLETED.");
        }

        interview.setStatus(target);
        interview.setOutcome(request.outcome());
        interview.setFeedback(request.feedback());
        interview.setCancellationReason(request.cancellationReason());

        // ---- D. INTERVIEW_UPDATE (Phase 11 hook) ----
        Interview updated = interview;
        dispatchInterviewUpdateUnlessSelf(
                caller,
                updated.getStudent(),
                () -> NotificationMessages.interviewStatusChanged(
                        updated.getStudent().getUser().getId(), updated.getId(), updated.getTitle(), target));

        return InterviewResponse.from(interview);
    }

    /**
     * D. INTERVIEW_UPDATE — skips when the caller IS the interview's own student acting
     * on themselves (nobody needs a notification about their own click). Notifying must
     * never fail the write it is reporting on, so every failure is caught and logged.
     */
    private void dispatchInterviewUpdateUnlessSelf(
            User caller, Student interviewStudent, Supplier<NotificationDispatch> commandSupplier) {
        if (caller != null && caller.getId().equals(interviewStudent.getUser().getId())) {
            return;
        }
        try {
            notificationService.dispatch(commandSupplier.get());
        } catch (Exception ex) {
            log.warn("Failed to dispatch INTERVIEW_UPDATE for interview: {}", ex.getMessage(), ex);
        }
    }

    @Transactional
    public void delete(Long id, User caller) {
        scopedWriteAuthorizer.requireAdmin(caller);
        Interview interview =
                interviewRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + id));
        interviewRepository.delete(interview);
    }

    /** ADMIN or the owning STUDENT; a non-owner student gets 404, FACULTY gets 403. */
    private Interview loadForWrite(Long id, User caller) {
        if (caller == null) {
            throw new AccessDeniedException("Authentication is required for this operation.");
        }
        if (caller.getRole() == Role.ADMIN) {
            return interviewRepository
                    .findWithStudentById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + id));
        }
        if (caller.getRole() == Role.STUDENT) {
            Student student = scopedWriteAuthorizer.requireOwnStudent(caller);
            return interviewRepository
                    .findByIdAndStudentId(id, student.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + id));
        }
        throw new AccessDeniedException("This operation is restricted to the owning student or an admin.");
    }

    // ------------------------------------------------------------------
    // Conflict detection — THE checkpoint.
    // ------------------------------------------------------------------

    /**
     * PESSIMISTIC_WRITE takes InnoDB next-key/gap locks over the (student_id,
     * scheduled_start) range so a second concurrent request blocks instead of also
     * passing the check. MUST be called from a read-write {@code @Transactional} method
     * — a {@code readOnly = true} transaction cannot take these locks.
     */
    @Transactional
    private void requireNoConflict(Long studentId, LocalDateTime start, LocalDateTime end, Long excludeInterviewId) {
        interviewRepository.lockOverlapping(studentId, BLOCKING, start, end).stream()
                .filter(existing -> !existing.getId().equals(excludeInterviewId))
                .findFirst()
                .ifPresent(
                        clash -> {
                            throw new DuplicateResourceException(
                                    "This student already has an interview scheduled from "
                                            + clash.getScheduledStart() + " to " + clash.getScheduledEnd()
                                            + " (\"" + clash.getTitle()
                                            + "\"). Overlapping interviews are not allowed.");
                        });
    }

    private static DuplicateResourceException slotTakenException() {
        return new DuplicateResourceException(
                "This student already has an interview scheduled that overlaps this window. Overlapping "
                        + "interviews are not allowed.");
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    private static void validateWindow(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new BadRequestException("scheduledEnd must be strictly after scheduledStart.");
        }
    }

    private static void validateOnlineHasLink(InterviewMode mode, String meetingLink) {
        if (mode == InterviewMode.ONLINE && (meetingLink == null || meetingLink.isBlank())) {
            throw new BadRequestException("An ONLINE interview requires a meeting link.");
        }
    }

    // ------------------------------------------------------------------
    // Filtering
    // ------------------------------------------------------------------

    private Specification<Interview> buildFilter(
            InterviewStatus status, Long studentId, LocalDateTime from, LocalDateTime to, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (studentId != null) {
                predicates.add(cb.equal(root.get("student").get("id"), studentId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledStart"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("scheduledStart"), to));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.toLowerCase() + "%";
                predicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("title")), like),
                                cb.like(cb.lower(root.get("companyName")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
