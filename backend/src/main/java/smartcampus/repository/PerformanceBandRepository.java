package smartcampus.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import smartcampus.entity.PerformanceBand;
import smartcampus.entity.PerformanceCategory;

/**
 * Persistence access for {@link PerformanceBand}. There are exactly four rows, one per
 * {@link PerformanceCategory} — no {@code save}-as-create is ever called by
 * {@code PerformanceBandService}, only {@code saveAndFlush} against an existing row, and no
 * {@code delete} is called at all (see the service javadoc for why).
 */
public interface PerformanceBandRepository extends JpaRepository<PerformanceBand, Long> {

    /**
     * Every band, in classification priority order (1 = strictest, 4 = the catch-all). This is
     * the order {@code PerformanceClassifier} MUST iterate in — never the {@link
     * PerformanceCategory} enum's declaration order.
     */
    List<PerformanceBand> findAllByOrderByDisplayOrderAsc();

    Optional<PerformanceBand> findByCategory(PerformanceCategory category);
}
