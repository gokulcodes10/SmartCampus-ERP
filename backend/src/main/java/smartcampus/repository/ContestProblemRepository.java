package smartcampus.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import smartcampus.entity.ContestProblem;

/**
 * Persistence access for {@link ContestProblem}.
 *
 * <p>{@link #findByContestIdOrderByOrdinalAsc} backs {@code
 * ContestDetailResponse.problems}. {@link #existsByContestIdAndProblemId} and {@link
 * #existsByProblemId} back the Java-side contest submission and problem-deletion
 * pre-checks (the database enforces the same invariants via FK/unique constraints, but
 * these give the caller a 400 with a sentence instead of a 409).
 */
public interface ContestProblemRepository extends JpaRepository<ContestProblem, Long> {

    List<ContestProblem> findByContestIdOrderByOrdinalAsc(Long contestId);

    Optional<ContestProblem> findByContestIdAndProblemId(Long contestId, Long problemId);

    boolean existsByContestIdAndProblemId(Long contestId, Long problemId);

    boolean existsByContestIdAndOrdinal(Long contestId, Integer ordinal);

    boolean existsByProblemId(Long problemId);
}
