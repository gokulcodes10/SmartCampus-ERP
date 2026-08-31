package smartcampus.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.NotificationDispatch;
import smartcampus.entity.CodingContest;
import smartcampus.entity.CodingSubmission;
import smartcampus.entity.ContestParticipant;
import smartcampus.entity.ContestProblem;
import smartcampus.entity.SubmissionStatus;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.CodingContestRepository;
import smartcampus.repository.CodingSubmissionRepository;
import smartcampus.repository.ContestParticipantRepository;
import smartcampus.repository.ContestProblemRepository;

/**
 * Owns the ICPC-style contest scoring algorithm. Every scoring column on {@link
 * ContestParticipant} is REBUILT WHOLESALE from {@code coding_submissions} — never
 * incremented in place — so a participant row can never drift from the submissions
 * that justify it, and {@code POST /api/contests/{id}/recompute} can always regenerate
 * the entire leaderboard from the submission table alone.
 *
 * <p><b>This is the cross-task seam.</b> The submission-authoring agent calls {@link
 * #recomputeParticipant(Long, Long)} after every judged contest submission (accepted or
 * not — a wrong attempt still moves the penalty clock). The signature below is fixed:
 * do not rename it, reorder its parameters, or give it a return value.
 *
 * <p><b>A judge outage must never penalize a student.</b> {@link
 * SubmissionStatus#PENDING}, {@link SubmissionStatus#RUNNING} and {@link
 * SubmissionStatus#INTERNAL_ERROR} are NOT wrong attempts — per clarification G10,
 * Judge0 is unreachable on this development machine, so {@code INTERNAL_ERROR} is
 * currently the normal outcome of every submission, and counting it as a wrong attempt
 * would invent penalty minutes out of an infrastructure failure.
 */
@Service
public class ContestScoringService {

    /**
     * The statuses that count as a "wrong attempt" for ICPC penalty purposes. Notably
     * excludes {@link SubmissionStatus#PENDING}, {@link SubmissionStatus#RUNNING} and
     * {@link SubmissionStatus#INTERNAL_ERROR} — see the class javadoc.
     */
    private static final Set<SubmissionStatus> WRONG_ATTEMPT_STATUSES =
            EnumSet.of(
                    SubmissionStatus.WRONG_ANSWER,
                    SubmissionStatus.TIME_LIMIT_EXCEEDED,
                    SubmissionStatus.MEMORY_LIMIT_EXCEEDED,
                    SubmissionStatus.RUNTIME_ERROR,
                    SubmissionStatus.COMPILATION_ERROR);

    private static final Logger log = LoggerFactory.getLogger(ContestScoringService.class);

    /**
     * The same ICPC-style leaderboard ordering as {@code
     * ContestParticipantRepository#findByContestIdOrderByTotalScoreDescPenaltySecondsAscLastAcceptedAtAscStudentIdAsc},
     * applied in memory so {@link #recomputeContest} can compute a rank BEFORE and AFTER
     * the recompute without an extra round trip per participant.
     */
    private static final Comparator<ContestParticipant> LEADERBOARD_ORDER =
            Comparator.<ContestParticipant, Integer>comparing(ContestParticipant::getTotalScore, Comparator.reverseOrder())
                    .thenComparing(ContestParticipant::getPenaltySeconds)
                    .thenComparing(
                            ContestParticipant::getLastAcceptedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(p -> p.getStudent().getId());

    private final CodingContestRepository codingContestRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final ContestParticipantRepository contestParticipantRepository;
    private final CodingSubmissionRepository codingSubmissionRepository;
    private final NotificationService notificationService;

    public ContestScoringService(
            CodingContestRepository codingContestRepository,
            ContestProblemRepository contestProblemRepository,
            ContestParticipantRepository contestParticipantRepository,
            CodingSubmissionRepository codingSubmissionRepository,
            NotificationService notificationService) {
        this.codingContestRepository = codingContestRepository;
        this.contestProblemRepository = contestProblemRepository;
        this.contestParticipantRepository = contestParticipantRepository;
        this.codingSubmissionRepository = codingSubmissionRepository;
        this.notificationService = notificationService;
    }

    /**
     * Rebuilds one participant's {@code totalScore}/{@code problemsSolved}/{@code
     * penaltySeconds}/{@code lastAcceptedAt} from scratch, from every {@code
     * coding_submissions} row for {@code (contestId, studentId)}.
     *
     * <p>A no-op when no {@link ContestParticipant} row exists for this pair — nothing
     * to recompute without a registration, and the caller (a submission's contest
     * validation) already requires registration before a contest submission can even be
     * created.
     */
    @Transactional
    public void recomputeParticipant(Long contestId, Long studentId) {
        ContestParticipant participant =
                contestParticipantRepository.findByContestIdAndStudentId(contestId, studentId).orElse(null);
        if (participant == null) {
            return;
        }

        CodingContest contest =
                codingContestRepository
                        .findById(contestId)
                        .orElseThrow(() -> new ResourceNotFoundException("Contest not found."));
        List<ContestProblem> contestProblems =
                contestProblemRepository.findByContestIdOrderByOrdinalAsc(contestId);
        List<CodingSubmission> submissions =
                codingSubmissionRepository.findByContestIdAndStudentIdOrderByCreatedAtAscIdAsc(
                        contestId, studentId);

        int totalScore = 0;
        int problemsSolved = 0;
        long penaltySeconds = 0;
        LocalDateTime lastAcceptedAt = null;

        for (ContestProblem contestProblem : contestProblems) {
            Long problemId = contestProblem.getProblem().getId();
            List<CodingSubmission> forProblem =
                    submissions.stream().filter(s -> s.getProblem().getId().equals(problemId)).toList();

            int firstAcceptIndex = -1;
            for (int i = 0; i < forProblem.size(); i++) {
                CodingSubmission s = forProblem.get(i);
                if (s.getStatus() == SubmissionStatus.ACCEPTED
                        && !s.getCreatedAt().isBefore(contest.getStartTime())
                        && !s.getCreatedAt().isAfter(contest.getEndTime())) {
                    firstAcceptIndex = i;
                    break;
                }
            }
            if (firstAcceptIndex < 0) {
                // Unsolved (or solved only outside the contest window): contributes
                // nothing — no score, no penalty.
                continue;
            }

            CodingSubmission firstAccept = forProblem.get(firstAcceptIndex);
            problemsSolved++;
            totalScore += contestProblem.getPoints();

            long minutes =
                    Duration.between(contest.getStartTime(), firstAccept.getCreatedAt()).getSeconds() / 60;
            if (minutes < 0) {
                minutes = 0;
            }

            int wrongAttempts = 0;
            for (int i = 0; i < firstAcceptIndex; i++) {
                if (WRONG_ATTEMPT_STATUSES.contains(forProblem.get(i).getStatus())) {
                    wrongAttempts++;
                }
            }

            penaltySeconds +=
                    minutes * 60 + (long) wrongAttempts * contest.getPenaltyMinutesPerWrongAttempt() * 60;

            if (lastAcceptedAt == null || firstAccept.getCreatedAt().isAfter(lastAcceptedAt)) {
                lastAcceptedAt = firstAccept.getCreatedAt();
            }
        }

        if (problemsSolved == 0) {
            // chk_contest_participants_unsolved_is_clean: a participant who solved
            // nothing MUST be written back to the clean 0/0/0/null state, never left
            // with stale values from a previous recompute.
            participant.setTotalScore(0);
            participant.setProblemsSolved(0);
            participant.setPenaltySeconds(0);
            participant.setLastAcceptedAt(null);
        } else {
            participant.setTotalScore(totalScore);
            participant.setProblemsSolved(problemsSolved);
            participant.setPenaltySeconds((int) penaltySeconds);
            participant.setLastAcceptedAt(lastAcceptedAt);
        }
        contestParticipantRepository.save(participant);
    }

    /**
     * Rebuilds every participant's row for a contest — what {@code POST
     * /api/contests/{id}/recompute} calls to regenerate the whole leaderboard from
     * {@code coding_submissions} alone.
     */
    @Transactional
    public void recomputeContest(Long contestId) {
        CodingContest contest =
                codingContestRepository
                        .findById(contestId)
                        .orElseThrow(() -> new ResourceNotFoundException("Contest not found."));
        List<ContestParticipant> participants = contestParticipantRepository.findByContestId(contestId);

        // ---- F. LEADERBOARD_UPDATE (Phase 11 hook) — rank BEFORE the recompute. ----
        Map<Long, Integer> ranksBefore = rankByStudentId(participants);

        for (ContestParticipant participant : participants) {
            recomputeParticipant(contestId, participant.getStudent().getId());
        }

        // Ranks AFTER: re-read so each participant's totalScore/penaltySeconds/
        // lastAcceptedAt reflect what recomputeParticipant just wrote.
        List<ContestParticipant> refreshed = contestParticipantRepository.findByContestId(contestId);
        Map<Long, Integer> ranksAfter = rankByStudentId(refreshed);

        try {
            dispatchLeaderboardMovedNotifications(contest, refreshed, ranksBefore, ranksAfter);
        } catch (Exception ex) {
            log.warn("Failed to dispatch LEADERBOARD_UPDATE for contest {}: {}", contestId, ex.getMessage(), ex);
        }
    }

    /** Ranks every participant per {@link #LEADERBOARD_ORDER}: 1 is first place. */
    private static Map<Long, Integer> rankByStudentId(List<ContestParticipant> participants) {
        List<ContestParticipant> sorted =
                participants.stream().sorted(LEADERBOARD_ORDER).toList();
        Map<Long, Integer> ranks = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            ranks.put(sorted.get(i).getStudent().getId(), i + 1);
        }
        return ranks;
    }

    /**
     * F. LEADERBOARD_UPDATE — only participants whose rank actually MOVED, never every
     * participant on every recompute (that would fire a frame per submission per
     * participant and drown the centre — see {@link #recomputeParticipant}'s javadoc for
     * why this hook lives here and not there).
     */
    private void dispatchLeaderboardMovedNotifications(
            CodingContest contest,
            List<ContestParticipant> participants,
            Map<Long, Integer> ranksBefore,
            Map<Long, Integer> ranksAfter) {
        List<NotificationDispatch> commands = new ArrayList<>();
        for (ContestParticipant participant : participants) {
            Long studentId = participant.getStudent().getId();
            Integer before = ranksBefore.get(studentId);
            Integer after = ranksAfter.get(studentId);
            if (after != null && !after.equals(before)) {
                commands.add(
                        NotificationMessages.leaderboardMoved(
                                participant.getStudent().getUser().getId(),
                                contest.getId(),
                                contest.getTitle(),
                                studentId,
                                after));
            }
        }
        notificationService.dispatchAll(commands);
    }
}
