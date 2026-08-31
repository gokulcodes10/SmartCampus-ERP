package smartcampus.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import smartcampus.entity.CodingProblem;

/**
 * Persistence access for {@link CodingProblem}.
 *
 * <p>{@link JpaSpecificationExecutor} supports dynamic filtering (difficulty, tag,
 * published, title/slug search) and pagination for {@code GET /api/problems}.
 * {@link #findByIdAndPublishedTrue} backs the non-admin "problem must be published to
 * be visible" rule (§R8 - an unpublished problem is a 404, never a 403, for a
 * non-admin caller).
 */
public interface CodingProblemRepository extends JpaRepository<CodingProblem, Long>,
    JpaSpecificationExecutor<CodingProblem> {

    Optional<CodingProblem> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Optional<CodingProblem> findByIdAndPublishedTrue(Long id);
}
