package smartcampus.coding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import smartcampus.TestcontainersConfiguration;
import smartcampus.dto.ContestLeaderboardRowResponse;
import smartcampus.dto.GlobalLeaderboardRowResponse;
import smartcampus.dto.PageResponse;
import smartcampus.entity.CodingContest;
import smartcampus.entity.CodingProblem;
import smartcampus.entity.CodingSubmission;
import smartcampus.entity.ContestParticipant;
import smartcampus.entity.ContestProblem;
import smartcampus.entity.ContestStatus;
import smartcampus.entity.ProblemDifficulty;
import smartcampus.entity.ProgrammingLanguage;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.SubmissionStatus;
import smartcampus.entity.User;
import smartcampus.repository.CodingContestRepository;
import smartcampus.repository.CodingProblemRepository;
import smartcampus.repository.CodingSubmissionRepository;
import smartcampus.repository.ContestParticipantRepository;
import smartcampus.repository.ContestProblemRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.UserRepository;
import smartcampus.service.CodingContestService;
import smartcampus.service.CodingLeaderboardService;
import smartcampus.service.ContestScoringService;

/**
 * Verification suite for the Phase 7 ICPC-style contest scoring algorithm ({@link
 * ContestScoringService}), the per-contest leaderboard read ({@link
 * CodingContestService#leaderboard}) and the global leaderboard's anti-double-counting
 * rule ({@link CodingLeaderboardService}), run against a real, freshly-provisioned
 * MySQL container (see {@link TestcontainersConfiguration}) so every {@code CHECK}
 * constraint from {@code V7__coding.sql} participates for real.
 *
 * <p>{@code coding_submissions.created_at} is {@code @CreationTimestamp} — Hibernate
 * overwrites any value assigned before {@code save}, so it cannot be set through the
 * entity. {@link #setCreatedAt} instead issues a raw {@code UPDATE} through the
 * application's own {@link DataSource} immediately after each submission is inserted,
 * so every scoring scenario below can place a submission at an exact, controlled
 * instant relative to a contest's {@code startTime}/{@code endTime}. Because {@link
 * ContestScoringService}'s methods are {@code @Transactional} and this test class is
 * not, each such update is fully committed — and any later re-read starts a brand new
 * persistence context (open-in-view is off) — before the scoring logic under test ever
 * queries it, so there is no first-level-cache staleness to account for.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ContestScoringServiceTest {

    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private CodingProblemRepository codingProblemRepository;
    @Autowired private CodingContestRepository codingContestRepository;
    @Autowired private ContestProblemRepository contestProblemRepository;
    @Autowired private ContestParticipantRepository contestParticipantRepository;
    @Autowired private CodingSubmissionRepository codingSubmissionRepository;
    @Autowired private DataSource dataSource;

    @Autowired private ContestScoringService contestScoringService;
    @Autowired private CodingContestService codingContestService;
    @Autowired private CodingLeaderboardService codingLeaderboardService;

    private long counter = 0;

    private String tag() {
        return System.nanoTime() + "-" + (counter++);
    }

    // ------------------------------------------------------------------
    // Fixture builders
    // ------------------------------------------------------------------

    private User persistAdmin() {
        return userRepository.save(
                User.builder()
                        .email("scoring-admin-" + tag() + "@example.com")
                        .password("not-a-real-hash")
                        .fullName("Scoring Admin")
                        .role(Role.ADMIN)
                        .build());
    }

    /** PENDING, not ACTIVE — {@code chk_students_active_requires_assignment} needs no
     * department/course/register-number fixture for a PENDING row, and the Phase 7
     * contract explicitly allows PENDING students to submit and register. */
    private Student persistStudent() {
        User user =
                userRepository.save(
                        User.builder()
                                .email("scoring-student-" + tag() + "@example.com")
                                .password("not-a-real-hash")
                                .fullName("Scoring Student " + tag())
                                .role(Role.STUDENT)
                                .build());
        return studentRepository.save(
                Student.builder().user(user).status(StudentStatus.PENDING).build());
    }

    private CodingProblem persistProblem(User admin, ProblemDifficulty difficulty) {
        return codingProblemRepository.save(
                CodingProblem.builder()
                        .slug("scoring-problem-" + tag())
                        .title("Scoring Problem " + tag())
                        .description("Description")
                        .difficulty(difficulty)
                        .createdBy(admin)
                        .build());
    }

    private CodingContest persistContest(
            User admin, LocalDateTime start, LocalDateTime end, int penaltyMinutes) {
        return codingContestRepository.save(
                CodingContest.builder()
                        .slug("scoring-contest-" + tag())
                        .title("Scoring Contest " + tag())
                        .startTime(start)
                        .endTime(end)
                        .status(ContestStatus.PUBLISHED)
                        .penaltyMinutesPerWrongAttempt(penaltyMinutes)
                        .createdBy(admin)
                        .build());
    }

    private ContestProblem persistContestProblem(
            CodingContest contest, CodingProblem problem, int ordinal, int points) {
        return contestProblemRepository.save(
                ContestProblem.builder()
                        .contest(contest)
                        .problem(problem)
                        .ordinal(ordinal)
                        .points(points)
                        .build());
    }

    private ContestParticipant persistParticipant(CodingContest contest, Student student) {
        return contestParticipantRepository.save(
                ContestParticipant.builder().contest(contest).student(student).build());
    }

    private ContestParticipant persistParticipantWithScore(
            CodingContest contest,
            Student student,
            int totalScore,
            int penaltySeconds,
            LocalDateTime lastAcceptedAt) {
        return contestParticipantRepository.saveAndFlush(
                ContestParticipant.builder()
                        .contest(contest)
                        .student(student)
                        .totalScore(totalScore)
                        .problemsSolved(1)
                        .penaltySeconds(penaltySeconds)
                        .lastAcceptedAt(lastAcceptedAt)
                        .build());
    }

    /** {@code passed}/{@code total} chosen so every relevant CHECK on coding_submissions holds. */
    private static int[] countsFor(SubmissionStatus status) {
        return switch (status) {
            case ACCEPTED -> new int[] {1, 1};
            case PENDING, RUNNING, INTERNAL_ERROR -> new int[] {0, 0};
            default -> new int[] {0, 1};
        };
    }

    private CodingSubmission persistSubmission(
            CodingProblem problem,
            Student student,
            CodingContest contest,
            SubmissionStatus status,
            LocalDateTime createdAt) {
        int[] counts = countsFor(status);
        CodingSubmission submission =
                CodingSubmission.builder()
                        .problem(problem)
                        .student(student)
                        .contest(contest)
                        .language(ProgrammingLanguage.JAVA)
                        .sourceCode("public class Main {}")
                        .status(status)
                        .passedTestCases(counts[0])
                        .totalTestCases(counts[1])
                        .score(counts[0])
                        .maxScore(counts[1])
                        .build();
        submission = codingSubmissionRepository.saveAndFlush(submission);
        setCreatedAt(submission.getId(), createdAt);
        return submission;
    }

    /**
     * {@code created_at} is {@code @CreationTimestamp}, so Hibernate stamps it at
     * insert regardless of what the entity carried — this bypasses that with a raw
     * JDBC update through the application's own {@link DataSource}, committed
     * immediately (no Spring-managed transaction wraps this test class).
     */
    private void setCreatedAt(Long submissionId, LocalDateTime at) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps =
                        conn.prepareStatement("UPDATE coding_submissions SET created_at = ? WHERE id = ?")) {
            ps.setObject(1, at);
            ps.setLong(2, submissionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set created_at for test fixture", e);
        }
    }

    private ContestParticipant reload(Long contestId, Long studentId) {
        return contestParticipantRepository.findByContestIdAndStudentId(contestId, studentId).orElseThrow();
    }

    // ------------------------------------------------------------------
    // recomputeParticipant — the ICPC scoring algorithm
    // ------------------------------------------------------------------

    @Test
    void solveOneProblem25MinutesIn_withTwoPriorWrongAttempts_computesExpectedPenalty() {
        User admin = persistAdmin();
        Student student = persistStudent();
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusHours(3);
        CodingContest contest = persistContest(admin, start, end, 10);
        CodingProblem problem = persistProblem(admin, ProblemDifficulty.EASY);
        persistContestProblem(contest, problem, 1, 100);
        persistParticipant(contest, student);

        persistSubmission(problem, student, contest, SubmissionStatus.WRONG_ANSWER, start.plusMinutes(5));
        persistSubmission(problem, student, contest, SubmissionStatus.WRONG_ANSWER, start.plusMinutes(10));
        persistSubmission(problem, student, contest, SubmissionStatus.ACCEPTED, start.plusMinutes(25));

        contestScoringService.recomputeParticipant(contest.getId(), student.getId());

        ContestParticipant result = reload(contest.getId(), student.getId());
        assertThat(result.getProblemsSolved()).isEqualTo(1);
        assertThat(result.getTotalScore()).isEqualTo(100);
        // floor(25min/60) * 60 + 2 wrong * 10 penalty-minutes * 60 = 1500 + 1200 = 2700
        assertThat(result.getPenaltySeconds()).isEqualTo(25 * 60 + 2 * 10 * 60);
        assertThat(result.getPenaltySeconds()).isEqualTo(2700);
    }

    @Test
    void problemAttemptedButNeverSolved_contributesNoScoreAndNoPenalty() {
        User admin = persistAdmin();
        Student student = persistStudent();
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusHours(3);
        CodingContest contest = persistContest(admin, start, end, 10);
        CodingProblem problem = persistProblem(admin, ProblemDifficulty.MEDIUM);
        persistContestProblem(contest, problem, 1, 100);
        persistParticipant(contest, student);

        persistSubmission(problem, student, contest, SubmissionStatus.WRONG_ANSWER, start.plusMinutes(5));
        persistSubmission(
                problem, student, contest, SubmissionStatus.TIME_LIMIT_EXCEEDED, start.plusMinutes(10));

        contestScoringService.recomputeParticipant(contest.getId(), student.getId());

        ContestParticipant result = reload(contest.getId(), student.getId());
        assertThat(result.getProblemsSolved()).isZero();
        assertThat(result.getTotalScore()).isZero();
        assertThat(result.getPenaltySeconds()).isZero();
        assertThat(result.getLastAcceptedAt()).isNull();
    }

    @Test
    void participantWithNothingSolved_isWrittenCleanAndInsertSucceeds() {
        User admin = persistAdmin();
        Student student = persistStudent();
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusHours(2);
        CodingContest contest = persistContest(admin, start, end, 10);

        // The clean 0/0/0/null state itself must be a legal row.
        ContestParticipant participant = persistParticipant(contest, student);
        assertThat(participant.getId()).isNotNull();

        contestScoringService.recomputeParticipant(contest.getId(), student.getId());
        ContestParticipant reloaded = reload(contest.getId(), student.getId());
        assertThat(reloaded.getProblemsSolved()).isZero();
        assertThat(reloaded.getTotalScore()).isZero();
        assertThat(reloaded.getPenaltySeconds()).isZero();
        assertThat(reloaded.getLastAcceptedAt()).isNull();

        // And the database really does enforce this — a "dirty" unsolved row (penalty
        // accrued with nothing solved) must be rejected by
        // chk_contest_participants_unsolved_is_clean, proving the invariant recompute
        // relies on is real, not merely respected by convention.
        Student otherStudent = persistStudent();
        assertThatThrownBy(
                        () ->
                                contestParticipantRepository.saveAndFlush(
                                        ContestParticipant.builder()
                                                .contest(contest)
                                                .student(otherStudent)
                                                .problemsSolved(0)
                                                .penaltySeconds(600)
                                                .totalScore(0)
                                                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void internalErrorAndPendingAreNotWrongAttempts_butWrongAnswerAndCompilationErrorAre() {
        User admin = persistAdmin();
        Student student = persistStudent();
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusHours(2);
        CodingContest contest = persistContest(admin, start, end, 10);
        CodingProblem problem = persistProblem(admin, ProblemDifficulty.HARD);
        persistContestProblem(contest, problem, 1, 100);
        persistParticipant(contest, student);

        // Judge0 is unreachable on this machine (G10): INTERNAL_ERROR is the normal
        // outcome, and PENDING is what a submission looks like mid-judging. Neither may
        // ever cost the student penalty time.
        persistSubmission(problem, student, contest, SubmissionStatus.INTERNAL_ERROR, start.plusSeconds(1));
        persistSubmission(problem, student, contest, SubmissionStatus.PENDING, start.plusSeconds(2));
        persistSubmission(problem, student, contest, SubmissionStatus.WRONG_ANSWER, start.plusSeconds(3));
        persistSubmission(
                problem, student, contest, SubmissionStatus.COMPILATION_ERROR, start.plusSeconds(4));
        persistSubmission(problem, student, contest, SubmissionStatus.ACCEPTED, start.plusSeconds(5));

        contestScoringService.recomputeParticipant(contest.getId(), student.getId());

        ContestParticipant result = reload(contest.getId(), student.getId());
        assertThat(result.getProblemsSolved()).isEqualTo(1);
        // 0 minutes elapsed (accept is 5 seconds after start) + exactly 2 wrong attempts.
        assertThat(result.getPenaltySeconds()).isEqualTo(2 * 10 * 60);
    }

    @Test
    void attemptsAfterTheFirstAccept_addNoPenalty() {
        User admin = persistAdmin();
        Student student = persistStudent();
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusHours(2);
        CodingContest contest = persistContest(admin, start, end, 10);
        CodingProblem problem = persistProblem(admin, ProblemDifficulty.EASY);
        persistContestProblem(contest, problem, 1, 50);
        persistParticipant(contest, student);

        persistSubmission(problem, student, contest, SubmissionStatus.ACCEPTED, start.plusMinutes(5));
        // A wrong attempt AFTER the accept — must not move the penalty clock.
        persistSubmission(problem, student, contest, SubmissionStatus.WRONG_ANSWER, start.plusMinutes(10));

        contestScoringService.recomputeParticipant(contest.getId(), student.getId());

        ContestParticipant result = reload(contest.getId(), student.getId());
        assertThat(result.getProblemsSolved()).isEqualTo(1);
        assertThat(result.getPenaltySeconds()).isEqualTo(5 * 60);
    }

    @Test
    void submissionsOutsideTheContestWindow_areNotCountedAsAnAccept() {
        User admin = persistAdmin();
        Student student = persistStudent();
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusHours(1);
        CodingContest contest = persistContest(admin, start, end, 10);
        CodingProblem beforeStart = persistProblem(admin, ProblemDifficulty.EASY);
        CodingProblem afterEnd = persistProblem(admin, ProblemDifficulty.EASY);
        persistContestProblem(contest, beforeStart, 1, 100);
        persistContestProblem(contest, afterEnd, 2, 100);
        persistParticipant(contest, student);

        persistSubmission(
                beforeStart, student, contest, SubmissionStatus.ACCEPTED, start.minusMinutes(10));
        persistSubmission(afterEnd, student, contest, SubmissionStatus.ACCEPTED, end.plusMinutes(5));

        contestScoringService.recomputeParticipant(contest.getId(), student.getId());

        ContestParticipant result = reload(contest.getId(), student.getId());
        assertThat(result.getProblemsSolved()).isZero();
        assertThat(result.getTotalScore()).isZero();
        assertThat(result.getPenaltySeconds()).isZero();
        assertThat(result.getLastAcceptedAt()).isNull();
    }

    @Test
    void recompute_isIdempotent() {
        User admin = persistAdmin();
        Student student = persistStudent();
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusHours(3);
        CodingContest contest = persistContest(admin, start, end, 10);
        CodingProblem problem = persistProblem(admin, ProblemDifficulty.EASY);
        persistContestProblem(contest, problem, 1, 100);
        persistParticipant(contest, student);

        persistSubmission(problem, student, contest, SubmissionStatus.WRONG_ANSWER, start.plusMinutes(2));
        persistSubmission(problem, student, contest, SubmissionStatus.ACCEPTED, start.plusMinutes(12));

        contestScoringService.recomputeParticipant(contest.getId(), student.getId());
        ContestParticipant first = reload(contest.getId(), student.getId());
        int score1 = first.getTotalScore();
        int solved1 = first.getProblemsSolved();
        int penalty1 = first.getPenaltySeconds();
        LocalDateTime lastAccepted1 = first.getLastAcceptedAt();

        contestScoringService.recomputeParticipant(contest.getId(), student.getId());
        ContestParticipant second = reload(contest.getId(), student.getId());

        assertThat(second.getTotalScore()).isEqualTo(score1);
        assertThat(second.getProblemsSolved()).isEqualTo(solved1);
        assertThat(second.getPenaltySeconds()).isEqualTo(penalty1);
        assertThat(second.getLastAcceptedAt()).isEqualTo(lastAccepted1);
    }

    // ------------------------------------------------------------------
    // Per-contest leaderboard ordering (CodingContestService.leaderboard)
    // ------------------------------------------------------------------

    @Test
    void leaderboard_ordersByScoreThenPenaltyThenLastAcceptedThenStudentId() {
        User admin = persistAdmin();
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusHours(3);
        CodingContest contest = persistContest(admin, start, end, 10);

        Student worst = persistStudent(); // score 100 (lowest score -> last)
        Student tiedHighPenalty = persistStudent(); // score 200, penalty 100
        Student tiedLowPenalty = persistStudent(); // score 200, penalty 50, later accept
        Student best = persistStudent(); // score 200, penalty 50, earliest accept

        persistParticipantWithScore(contest, worst, 100, 100, start.plusMinutes(10));
        persistParticipantWithScore(contest, tiedHighPenalty, 200, 100, start.plusMinutes(10));
        persistParticipantWithScore(contest, tiedLowPenalty, 200, 50, start.plusMinutes(10));
        persistParticipantWithScore(contest, best, 200, 50, start.plusMinutes(5));

        PageResponse<ContestLeaderboardRowResponse> page =
                codingContestService.leaderboard(contest.getId(), admin, PageRequest.of(0, 10));

        List<Long> order = page.content().stream().map(ContestLeaderboardRowResponse::studentId).toList();
        assertThat(order)
                .containsExactly(best.getId(), tiedLowPenalty.getId(), tiedHighPenalty.getId(), worst.getId());
        assertThat(page.content().get(0).rank()).isEqualTo(1);
        assertThat(page.content().get(1).rank()).isEqualTo(2);
        assertThat(page.content().get(2).rank()).isEqualTo(3);
        assertThat(page.content().get(3).rank()).isEqualTo(4);
        assertThat(page.totalElements()).isEqualTo(4);
    }

    // ------------------------------------------------------------------
    // Global leaderboard — the double-counting trap
    // ------------------------------------------------------------------

    @Test
    void globalLeaderboard_solvingTheSameProblemTwice_scoresOnce() {
        User admin = persistAdmin();
        Student student = persistStudent();
        CodingProblem problem = persistProblem(admin, ProblemDifficulty.EASY);

        // Two ACCEPTED practice submissions (no contest) for the SAME problem.
        persistSubmission(problem, student, null, SubmissionStatus.ACCEPTED, LocalDateTime.now().minusMinutes(10));
        persistSubmission(problem, student, null, SubmissionStatus.ACCEPTED, LocalDateTime.now().minusMinutes(5));

        PageResponse<GlobalLeaderboardRowResponse> page =
                codingLeaderboardService.globalLeaderboard(PageRequest.of(0, 500));

        GlobalLeaderboardRowResponse row =
                page.content().stream()
                        .filter(r -> r.studentId().equals(student.getId()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Student not found in global leaderboard"));
        assertThat(row.problemsSolved()).isEqualTo(1);
        // Default smartcampus.coding.points.easy = 10 (no override in test properties).
        assertThat(row.totalScore()).isEqualTo(10);
    }
}
