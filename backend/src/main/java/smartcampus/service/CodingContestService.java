package smartcampus.service;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.ContestCreateRequest;
import smartcampus.dto.ContestDetailResponse;
import smartcampus.dto.ContestLeaderboardRowResponse;
import smartcampus.dto.ContestParticipantResponse;
import smartcampus.dto.ContestProblemRequest;
import smartcampus.dto.ContestProblemResponse;
import smartcampus.dto.ContestSummaryResponse;
import smartcampus.dto.ContestUpdateRequest;
import smartcampus.dto.NotificationDispatch;
import smartcampus.dto.PageResponse;
import smartcampus.entity.CodingContest;
import smartcampus.entity.CodingProblem;
import smartcampus.entity.CodingSubmission;
import smartcampus.entity.ContestParticipant;
import smartcampus.entity.ContestPhase;
import smartcampus.entity.ContestProblem;
import smartcampus.entity.ContestStatus;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.SubmissionStatus;
import smartcampus.entity.User;
import smartcampus.exception.BadRequestException;
import smartcampus.exception.DuplicateResourceException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.CodingContestRepository;
import smartcampus.repository.CodingProblemRepository;
import smartcampus.repository.CodingSubmissionRepository;
import smartcampus.repository.ContestParticipantRepository;
import smartcampus.repository.ContestProblemRepository;
import smartcampus.repository.StudentRepository;

/**
 * Business logic behind {@code /api/contests}: authoring (ADMIN only, per README "For
 * Administrators": coding contest creation and problem authoring), registration, the
 * per-contest leaderboard read, and the admin recompute endpoint.
 *
 * <p>Nothing here calls {@code AcademicAccessGuard}: contests are institution-wide, not
 * scoped to a (subject, academicYear, semester, section) tuple (R7).
 *
 * <p>Visibility (R8 — enumeration, never probing): a DRAFT or CANCELLED contest is
 * ADMIN-only; a non-admin {@code GET} by id gets {@link ResourceNotFoundException}
 * (404), never a 403, and the list never includes one. A contest's problem set is
 * additionally hidden from non-admins while its derived {@link ContestPhase} is {@code
 * UPCOMING}.
 *
 * <p>Scoring itself (recomputing {@code total_score}/{@code problems_solved}/{@code
 * penalty_seconds}) is owned entirely by {@link ContestScoringService}; this class only
 * reads the resulting {@link ContestParticipant} rows for display.
 */
@Service
public class CodingContestService {

    private static final Logger log = LoggerFactory.getLogger(CodingContestService.class);

    private final CodingContestRepository codingContestRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final ContestParticipantRepository contestParticipantRepository;
    private final CodingProblemRepository codingProblemRepository;
    private final CodingSubmissionRepository codingSubmissionRepository;
    private final StudentRepository studentRepository;
    private final ContestScoringService contestScoringService;
    private final NotificationService notificationService;

    public CodingContestService(
            CodingContestRepository codingContestRepository,
            ContestProblemRepository contestProblemRepository,
            ContestParticipantRepository contestParticipantRepository,
            CodingProblemRepository codingProblemRepository,
            CodingSubmissionRepository codingSubmissionRepository,
            StudentRepository studentRepository,
            ContestScoringService contestScoringService,
            NotificationService notificationService) {
        this.codingContestRepository = codingContestRepository;
        this.contestProblemRepository = contestProblemRepository;
        this.contestParticipantRepository = contestParticipantRepository;
        this.codingProblemRepository = codingProblemRepository;
        this.codingSubmissionRepository = codingSubmissionRepository;
        this.studentRepository = studentRepository;
        this.contestScoringService = contestScoringService;
        this.notificationService = notificationService;
    }

    // ---------------------------------------------------------------------------
    // Reads
    // ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<ContestSummaryResponse> list(
            User caller, String search, ContestStatus status, ContestPhase phase, Pageable pageable) {
        boolean admin = caller.getRole() == Role.ADMIN;
        LocalDateTime now = LocalDateTime.now();

        Specification<CodingContest> spec =
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    if (!admin) {
                        // Non-admin listing NEVER includes a DRAFT/CANCELLED contest,
                        // regardless of what the `status` query param asked for.
                        predicates.add(cb.equal(root.get("status"), ContestStatus.PUBLISHED));
                    } else if (status != null) {
                        predicates.add(cb.equal(root.get("status"), status));
                    }
                    if (phase != null) {
                        switch (phase) {
                            case UPCOMING -> predicates.add(cb.greaterThan(root.get("startTime"), now));
                            case RUNNING ->
                                    predicates.add(
                                            cb.and(
                                                    cb.lessThanOrEqualTo(root.get("startTime"), now),
                                                    cb.greaterThanOrEqualTo(root.get("endTime"), now)));
                            case ENDED -> predicates.add(cb.lessThan(root.get("endTime"), now));
                        }
                    }
                    if (search != null && !search.isBlank()) {
                        String pattern = "%" + search.toLowerCase() + "%";
                        predicates.add(
                                cb.or(
                                        cb.like(cb.lower(root.get("title")), pattern),
                                        cb.like(cb.lower(root.get("slug")), pattern)));
                    }
                    return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
                };

        Optional<Student> callerStudent = resolveOwnStudentIfStudent(caller);
        Page<CodingContest> page = codingContestRepository.findAll(spec, pageable);
        return PageResponse.of(page, c -> toSummary(c, callerStudent));
    }

    @Transactional(readOnly = true)
    public ContestDetailResponse getById(Long id, User caller) {
        CodingContest contest = loadViewable(id, caller);
        return toDetail(contest, caller);
    }

    @Transactional(readOnly = true)
    public PageResponse<ContestLeaderboardRowResponse> leaderboard(Long id, User caller, Pageable pageable) {
        loadViewable(id, caller); // 404 before leaking a DRAFT/CANCELLED contest's board
        Page<ContestParticipant> page =
                contestParticipantRepository
                        .findByContestIdOrderByTotalScoreDescPenaltySecondsAscLastAcceptedAtAscStudentIdAsc(
                                id, pageable);
        long offset = pageable.getOffset();
        List<ContestLeaderboardRowResponse> content = new ArrayList<>(page.getContent().size());
        List<ContestParticipant> rows = page.getContent();
        for (int i = 0; i < rows.size(); i++) {
            content.add(toLeaderboardRow(rows.get(i), (int) (offset + i + 1)));
        }
        return new PageResponse<>(
                content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    // ---------------------------------------------------------------------------
    // Authoring (ADMIN only)
    // ---------------------------------------------------------------------------

    @Transactional
    public ContestDetailResponse create(ContestCreateRequest request, User caller) {
        requireAdmin(caller);
        validateWindow(request.startTime(), request.endTime());
        if (codingContestRepository.existsBySlug(request.slug())) {
            throw new DuplicateResourceException("A contest with this slug already exists.");
        }
        CodingContest contest =
                CodingContest.builder()
                        .slug(request.slug())
                        .title(request.title())
                        .description(request.description())
                        .startTime(request.startTime())
                        .endTime(request.endTime())
                        .status(request.status())
                        .penaltyMinutesPerWrongAttempt(request.penaltyMinutesPerWrongAttempt())
                        .createdBy(caller)
                        .build();
        try {
            contest = codingContestRepository.saveAndFlush(contest);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("A contest with this slug already exists.");
        }
        return toDetail(contest, caller);
    }

    @Transactional
    public ContestDetailResponse update(Long id, ContestUpdateRequest request, User caller) {
        requireAdmin(caller);
        validateWindow(request.startTime(), request.endTime());
        CodingContest contest = loadOrThrow(id);

        if (!contest.getSlug().equals(request.slug()) && codingContestRepository.existsBySlug(request.slug())) {
            throw new DuplicateResourceException("A contest with this slug already exists.");
        }

        boolean windowOrStatusChanged =
                !contest.getStartTime().equals(request.startTime())
                        || !contest.getEndTime().equals(request.endTime())
                        || contest.getStatus() != request.status();

        contest.setSlug(request.slug());
        contest.setTitle(request.title());
        contest.setDescription(request.description());
        contest.setStartTime(request.startTime());
        contest.setEndTime(request.endTime());
        contest.setStatus(request.status());
        contest.setPenaltyMinutesPerWrongAttempt(request.penaltyMinutesPerWrongAttempt());

        try {
            contest = codingContestRepository.saveAndFlush(contest);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("A contest with this slug already exists.");
        }

        // Build the response BEFORE dispatching notifications: dispatchAll (below) issues
        // entityManager.clear() calls to bound memory on a large fan-out, which detaches
        // every entity in this shared persistence context — including `contest` — and
        // turns any lazy field read afterwards (e.g. contest.getCreatedBy().getFullName()
        // inside toDetail) into a LazyInitializationException. Reading everything toDetail
        // needs first sidesteps that entirely; see NotificationService#dispatchAll's
        // javadoc for the same note.
        ContestDetailResponse response = toDetail(contest, caller);

        // ---- E. CONTEST_UPDATE (Phase 11 hook) ----
        if (windowOrStatusChanged) {
            try {
                dispatchContestUpdatedNotifications(contest);
            } catch (Exception ex) {
                log.warn(
                        "Failed to dispatch CONTEST_UPDATE for contest {}: {}", contest.getId(), ex.getMessage(), ex);
            }
        }

        return response;
    }

    /**
     * E. CONTEST_UPDATE broadcast — every REGISTERED participant, keyed so an unchanged
     * re-save (identical window and status) never notifies anybody.
     */
    private void dispatchContestUpdatedNotifications(CodingContest contest) {
        List<ContestParticipant> participants = contestParticipantRepository.findByContestId(contest.getId());
        List<NotificationDispatch> commands =
                participants.stream()
                        .map(
                                participant ->
                                        NotificationMessages.contestUpdated(
                                                participant.getStudent().getUser().getId(),
                                                contest.getId(),
                                                contest.getTitle(),
                                                contest.getStartTime(),
                                                contest.getEndTime(),
                                                contest.getStatus()))
                        .toList();
        notificationService.dispatchAll(commands);
    }

    @Transactional
    public void delete(Long id, User caller) {
        requireAdmin(caller);
        CodingContest contest = loadOrThrow(id);
        // contest_problems and contest_participants CASCADE on delete; a contest with
        // existing coding_submissions is RESTRICTed by the database FK, which
        // GlobalExceptionHandler's DataIntegrityViolationException handler turns into a
        // 409 — CodingSubmissionRepository exposes no existsByContestId for an explicit
        // pre-check here.
        codingContestRepository.delete(contest);
    }

    @Transactional
    public ContestProblemResponse addProblem(Long contestId, ContestProblemRequest request, User caller) {
        requireAdmin(caller);
        CodingContest contest = loadOrThrow(contestId);
        CodingProblem problem =
                codingProblemRepository
                        .findById(request.problemId())
                        .orElseThrow(() -> new ResourceNotFoundException("Problem not found."));

        if (contestProblemRepository.existsByContestIdAndProblemId(contestId, problem.getId())) {
            throw new DuplicateResourceException("This problem is already part of the contest.");
        }
        if (contestProblemRepository.existsByContestIdAndOrdinal(contestId, request.ordinal())) {
            throw new DuplicateResourceException(
                    "A problem already occupies ordinal " + request.ordinal() + " in this contest.");
        }

        ContestProblem contestProblem =
                ContestProblem.builder()
                        .contest(contest)
                        .problem(problem)
                        .ordinal(request.ordinal())
                        .points(request.points())
                        .build();
        try {
            contestProblem = contestProblemRepository.saveAndFlush(contestProblem);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("This problem or ordinal is already used in this contest.");
        }
        return toContestProblemResponse(contestProblem, Map.of());
    }

    @Transactional
    public void removeProblem(Long contestId, Long problemId, User caller) {
        requireAdmin(caller);
        ContestProblem contestProblem =
                contestProblemRepository
                        .findByContestIdAndProblemId(contestId, problemId)
                        .orElseThrow(() -> new ResourceNotFoundException("That problem is not part of this contest."));
        // Any coding_submissions row already recorded for this contest/problem pair is
        // RESTRICTed by the composite FK on coding_submissions; the resulting
        // DataIntegrityViolationException becomes a 409 via GlobalExceptionHandler.
        contestProblemRepository.delete(contestProblem);
    }

    // ---------------------------------------------------------------------------
    // Registration
    // ---------------------------------------------------------------------------

    @Transactional
    public ContestParticipantResponse register(Long contestId, User caller) {
        Student student = resolveActingStudent(caller);
        // This route is STUDENT-only, so loadViewable's non-admin branch already
        // guarantees `contest.getStatus() == PUBLISHED` here — a DRAFT/CANCELLED
        // contest throws 404 above, matching the general visibility rule (R8).
        CodingContest contest = loadViewable(contestId, caller);
        if (!LocalDateTime.now().isBefore(contest.getEndTime())) {
            throw new BadRequestException("This contest has ended.");
        }
        if (contestParticipantRepository.existsByContestIdAndStudentId(contestId, student.getId())) {
            throw new DuplicateResourceException("You are already registered for this contest.");
        }

        ContestParticipant participant =
                ContestParticipant.builder().contest(contest).student(student).build();
        participant = contestParticipantRepository.saveAndFlush(participant);
        return toParticipantResponse(participant);
    }

    @Transactional(readOnly = true)
    public ContestParticipantResponse me(Long contestId, User caller) {
        Student student = resolveActingStudent(caller);
        ContestParticipant participant =
                contestParticipantRepository
                        .findByContestIdAndStudentId(contestId, student.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("You are not registered for this contest."));
        return toParticipantResponse(participant);
    }

    // ---------------------------------------------------------------------------
    // Recompute (ADMIN only)
    // ---------------------------------------------------------------------------

    @Transactional
    public ContestDetailResponse recompute(Long contestId, User caller) {
        requireAdmin(caller);
        CodingContest contest = loadOrThrow(contestId);
        contestScoringService.recomputeContest(contestId);
        return toDetail(contest, caller);
    }

    // ---------------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------------

    /**
     * The one gate every by-id contest read routes through. A DRAFT/CANCELLED contest
     * is {@link ResourceNotFoundException} (404) for anyone who is not ADMIN — never a
     * 403 — so an id cannot be used to probe "exists but not published" vs "does not
     * exist" (R8).
     */
    CodingContest loadViewable(Long id, User caller) {
        if (caller.getRole() == Role.ADMIN) {
            return loadOrThrow(id);
        }
        CodingContest contest = loadOrThrow(id);
        if (contest.getStatus() != ContestStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Contest not found.");
        }
        return contest;
    }

    private CodingContest loadOrThrow(Long id) {
        return codingContestRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contest not found."));
    }

    private void requireAdmin(User caller) {
        if (caller.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only administrators can perform this action.");
        }
    }

    private void validateWindow(LocalDateTime startTime, LocalDateTime endTime) {
        if (endTime == null || startTime == null || !endTime.isAfter(startTime)) {
            throw new BadRequestException("End time must be after start time.");
        }
    }

    /**
     * Section 6, "Who is a student here": no {@code students} row (ADMIN/FACULTY) is
     * 403; an {@code INACTIVE} student row is 403; {@code PENDING} and {@code ACTIVE}
     * may both register.
     */
    private Student resolveActingStudent(User caller) {
        if (caller.getRole() != Role.STUDENT) {
            throw new AccessDeniedException("Only students can register for contests.");
        }
        Student student =
                studentRepository
                        .findByUserId(caller.getId())
                        .orElseThrow(() -> new AccessDeniedException("Only students can register for contests."));
        if (student.getStatus() == StudentStatus.INACTIVE) {
            throw new AccessDeniedException("This student account is deactivated.");
        }
        return student;
    }

    private Optional<Student> resolveOwnStudentIfStudent(User caller) {
        if (caller.getRole() != Role.STUDENT) {
            return Optional.empty();
        }
        return studentRepository.findByUserId(caller.getId());
    }

    static ContestPhase computePhase(CodingContest contest) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(contest.getStartTime())) {
            return ContestPhase.UPCOMING;
        }
        if (now.isAfter(contest.getEndTime())) {
            return ContestPhase.ENDED;
        }
        return ContestPhase.RUNNING;
    }

    static String label(Integer ordinal) {
        if (ordinal == null) {
            return "";
        }
        if (ordinal >= 1 && ordinal <= 26) {
            return String.valueOf((char) ('A' + ordinal - 1));
        }
        return String.valueOf(ordinal);
    }

    private ContestSummaryResponse toSummary(CodingContest contest, Optional<Student> callerStudent) {
        int problemCount = contestProblemRepository.findByContestIdOrderByOrdinalAsc(contest.getId()).size();
        long participantCount = contestParticipantRepository.countByContestId(contest.getId());
        boolean registered =
                callerStudent
                        .map(s -> contestParticipantRepository.existsByContestIdAndStudentId(contest.getId(), s.getId()))
                        .orElse(false);
        return new ContestSummaryResponse(
                contest.getId(),
                contest.getSlug(),
                contest.getTitle(),
                contest.getStartTime(),
                contest.getEndTime(),
                contest.getStatus(),
                computePhase(contest),
                contest.getPenaltyMinutesPerWrongAttempt(),
                problemCount,
                participantCount,
                registered,
                contest.getCreatedAt(),
                contest.getUpdatedAt());
    }

    private ContestDetailResponse toDetail(CodingContest contest, User caller) {
        Optional<Student> callerStudent = resolveOwnStudentIfStudent(caller);
        List<ContestProblem> contestProblems =
                contestProblemRepository.findByContestIdOrderByOrdinalAsc(contest.getId());
        long participantCount = contestParticipantRepository.countByContestId(contest.getId());
        boolean registered =
                callerStudent
                        .map(s -> contestParticipantRepository.existsByContestIdAndStudentId(contest.getId(), s.getId()))
                        .orElse(false);

        boolean admin = caller.getRole() == Role.ADMIN;
        ContestPhase phase = computePhase(contest);
        boolean problemsVisible = admin || phase != ContestPhase.UPCOMING;

        Map<Long, List<CodingSubmission>> callerSubmissionsByProblem =
                callerStudent.isPresent()
                        ? codingSubmissionRepository
                                .findByContestIdAndStudentIdOrderByCreatedAtAscIdAsc(
                                        contest.getId(), callerStudent.get().getId())
                                .stream()
                                .collect(Collectors.groupingBy(s -> s.getProblem().getId()))
                        : Map.of();

        List<ContestProblemResponse> problems =
                problemsVisible
                        ? contestProblems.stream()
                                .map(cp -> toContestProblemResponse(cp, callerSubmissionsByProblem))
                                .toList()
                        : List.of();

        User createdBy = contest.getCreatedBy();
        return new ContestDetailResponse(
                contest.getId(),
                contest.getSlug(),
                contest.getTitle(),
                contest.getStartTime(),
                contest.getEndTime(),
                contest.getStatus(),
                phase,
                contest.getPenaltyMinutesPerWrongAttempt(),
                contestProblems.size(),
                participantCount,
                registered,
                contest.getCreatedAt(),
                contest.getUpdatedAt(),
                contest.getDescription(),
                createdBy != null ? createdBy.getId() : null,
                createdBy != null ? createdBy.getFullName() : null,
                problemsVisible,
                problems);
    }

    private ContestProblemResponse toContestProblemResponse(
            ContestProblem contestProblem, Map<Long, List<CodingSubmission>> callerSubmissionsByProblem) {
        CodingProblem problem = contestProblem.getProblem();
        List<CodingSubmission> mine =
                callerSubmissionsByProblem.getOrDefault(problem.getId(), List.of());

        SubmissionStatus myBestStatus = null;
        int myAttempts = mine.size();
        if (!mine.isEmpty()) {
            myBestStatus = mine.get(mine.size() - 1).getStatus();
            for (CodingSubmission s : mine) {
                if (s.getStatus() == SubmissionStatus.ACCEPTED) {
                    myBestStatus = SubmissionStatus.ACCEPTED;
                    break;
                }
            }
        }

        return new ContestProblemResponse(
                contestProblem.getId(),
                contestProblem.getContest().getId(),
                problem.getId(),
                problem.getTitle(),
                problem.getDifficulty(),
                contestProblem.getOrdinal(),
                label(contestProblem.getOrdinal()),
                contestProblem.getPoints(),
                myBestStatus,
                myAttempts);
    }

    private ContestParticipantResponse toParticipantResponse(ContestParticipant participant) {
        Student student = participant.getStudent();
        User user = student.getUser();
        return new ContestParticipantResponse(
                participant.getId(),
                participant.getContest().getId(),
                student.getId(),
                user != null ? user.getFullName() : null,
                student.getRegisterNumber(),
                participant.getRegisteredAt(),
                participant.getTotalScore(),
                participant.getProblemsSolved(),
                participant.getPenaltySeconds(),
                participant.getLastAcceptedAt());
    }

    private ContestLeaderboardRowResponse toLeaderboardRow(ContestParticipant participant, int rank) {
        Student student = participant.getStudent();
        User user = student.getUser();
        var department = student.getDepartment();
        return new ContestLeaderboardRowResponse(
                rank,
                student.getId(),
                user != null ? user.getFullName() : null,
                student.getRegisterNumber(),
                department != null ? department.getName() : null,
                participant.getTotalScore(),
                participant.getProblemsSolved(),
                participant.getPenaltySeconds(),
                participant.getLastAcceptedAt());
    }
}
