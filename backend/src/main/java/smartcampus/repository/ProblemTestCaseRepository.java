package smartcampus.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import smartcampus.entity.ProblemTestCase;

/**
 * Persistence access for {@link ProblemTestCase}.
 *
 * <p>{@link #findByProblemIdOrderByOrdinalAsc} is what a submission judges against, in
 * execution order. {@link #findByProblemIdAndSampleTrueOrderByOrdinalAsc} backs the
 * playground "Run" button and the student-facing sample cases - note the derived-query
 * keyword {@code Sample}, matching the entity field name (not the {@code is_sample}
 * column).
 */
public interface ProblemTestCaseRepository extends JpaRepository<ProblemTestCase, Long> {

    List<ProblemTestCase> findByProblemIdOrderByOrdinalAsc(Long problemId);

    List<ProblemTestCase> findByProblemIdAndSampleTrueOrderByOrdinalAsc(Long problemId);

    long countByProblemId(Long problemId);

    Optional<ProblemTestCase> findByIdAndProblemId(Long id, Long problemId);

    boolean existsByProblemIdAndOrdinal(Long problemId, Integer ordinal);
}
