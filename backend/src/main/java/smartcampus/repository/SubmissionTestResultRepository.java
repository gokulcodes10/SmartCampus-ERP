package smartcampus.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import smartcampus.entity.SubmissionTestResult;

/**
 * Persistence access for {@link SubmissionTestResult}.
 *
 * <p>{@link #findBySubmissionIdOrderByOrdinalAsc} backs {@code
 * SubmissionDetailResponse.testResults}. {@link #deleteBySubmissionId} exists for the
 * (uncommon) case of re-judging a submission from scratch.
 */
public interface SubmissionTestResultRepository extends JpaRepository<SubmissionTestResult, Long> {

    List<SubmissionTestResult> findBySubmissionIdOrderByOrdinalAsc(Long submissionId);

    void deleteBySubmissionId(Long submissionId);
}
