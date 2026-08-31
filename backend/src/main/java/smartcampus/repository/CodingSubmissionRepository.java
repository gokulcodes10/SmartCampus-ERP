package smartcampus.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import smartcampus.entity.CodingSubmission;
import smartcampus.entity.SubmissionStatus;

/**
 * Persistence access for {@link CodingSubmission}.
 *
 * <p>{@link JpaSpecificationExecutor} supports the dynamic {@code
 * GET /api/coding/submissions} filters (problemId, contestId, status, studentId) and
 * pagination.
 *
 * <p>{@link #findSolvedProblemIds} and {@link #findAttemptedProblemIds} back {@code
 * CodingStatsResponse} and the "have I already solved this?" checks.
 *
 * <p>{@link #globalLeaderboardRaw} is the shape of the global leaderboard aggregation
 * only, NOT the scoring algorithm - {@code sum(case ...)} over raw submission rows
 * double-counts a problem solved more than once. The service layer computes {@code
 * totalScore} from distinct (student, problem, difficulty) data instead; see §6 of the
 * Phase 7 contract. Difficulty points are bound parameters, never literals (§60).
 */
public interface CodingSubmissionRepository extends JpaRepository<CodingSubmission, Long>,
    JpaSpecificationExecutor<CodingSubmission> {

    List<CodingSubmission> findByContestIdAndStudentIdOrderByCreatedAtAscIdAsc(
            Long contestId, Long studentId);

    List<CodingSubmission> findByContestIdOrderByCreatedAtAscIdAsc(Long contestId);

    boolean existsByProblemIdAndStudentIdAndStatus(
            Long problemId, Long studentId, SubmissionStatus status);

    long countByStudentId(Long studentId);

    long countByStudentIdAndStatus(Long studentId, SubmissionStatus status);

    @Query("""
        select distinct s.problem.id from CodingSubmission s
        where s.student.id = :studentId and s.status = smartcampus.entity.SubmissionStatus.ACCEPTED
        """)
    List<Long> findSolvedProblemIds(@Param("studentId") Long studentId);

    @Query("""
        select distinct s.problem.id from CodingSubmission s where s.student.id = :studentId
        """)
    List<Long> findAttemptedProblemIds(@Param("studentId") Long studentId);

    // Global leaderboard. Difficulty points are BOUND PARAMETERS, never literals (§60).
    // Returns Object[]{ studentId(Long), problemsSolved(Long), totalScore(Long),
    //                   lastAcceptedAt(LocalDateTime) }
    // CAUTION: sum(case ...) double counts a problem solved twice - see the class
    // javadoc. Use this ONLY for the shape; the service layer computes totalScore from
    // distinct (student, problem, difficulty) data instead.
    @Query("""
        select s.student.id,
               count(distinct s.problem.id),
               sum(case s.problem.difficulty
                     when smartcampus.entity.ProblemDifficulty.EASY   then :easy
                     when smartcampus.entity.ProblemDifficulty.MEDIUM then :medium
                     else :hard end),
               max(s.createdAt)
        from CodingSubmission s
        where s.status = smartcampus.entity.SubmissionStatus.ACCEPTED
        group by s.student.id
        """)
    Page<Object[]> globalLeaderboardRaw(
            @Param("easy") int easy, @Param("medium") int medium,
            @Param("hard") int hard, Pageable pageable);
}
