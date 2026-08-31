package smartcampus.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import smartcampus.entity.AIStudyPlanItem;

/**
 * Persistence access for {@link AIStudyPlanItem}.
 *
 * <p>{@link #findMaxPosition} returns {@code -1} for a plan with no items yet, so the
 * caller can always assign the next {@code position} as {@code findMaxPosition(id) + 1}
 * without a separate empty-plan branch.
 */
public interface AIStudyPlanItemRepository extends JpaRepository<AIStudyPlanItem, Long> {

    List<AIStudyPlanItem> findByStudyPlanIdOrderByPositionAsc(Long studyPlanId);

    Optional<AIStudyPlanItem> findByIdAndStudyPlanId(Long id, Long studyPlanId);

    long countByStudyPlanId(Long studyPlanId);

    long countByStudyPlanIdAndCompletedTrue(Long studyPlanId);

    @Query("select coalesce(max(i.position), -1) from AIStudyPlanItem i where i.studyPlan.id = :planId")
    int findMaxPosition(@Param("planId") Long planId);
}
