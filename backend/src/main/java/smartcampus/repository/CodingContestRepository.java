package smartcampus.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import smartcampus.entity.CodingContest;

/**
 * Persistence access for {@link CodingContest}.
 *
 * <p>{@link JpaSpecificationExecutor} supports the dynamic {@code GET /api/contests}
 * filters (status - ADMIN only, phase, title/slug search) and pagination.
 */
public interface CodingContestRepository extends JpaRepository<CodingContest, Long>,
    JpaSpecificationExecutor<CodingContest> {

    Optional<CodingContest> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
