package smartcampus.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import smartcampus.entity.AIStudyPlan;
import smartcampus.entity.AIStudyPlanStatus;
import smartcampus.entity.AIStudyPlanType;

/**
 * Persistence access for {@link AIStudyPlan}.
 *
 * <p>{@link #findByIdAndStudentId} is the ONLY way a plan is ever loaded for a caller —
 * ownership must never be checked after an unscoped {@code findById}, per the
 * non-probing 404 rule.
 */
public interface AIStudyPlanRepository extends JpaRepository<AIStudyPlan, Long> {

    Optional<AIStudyPlan> findByIdAndStudentId(Long id, Long studentId);

    Page<AIStudyPlan> findByStudentId(Long studentId, Pageable pageable);

    Page<AIStudyPlan> findByStudentIdAndPlanType(
            Long studentId, AIStudyPlanType planType, Pageable pageable);

    Page<AIStudyPlan> findByStudentIdAndStatus(
            Long studentId, AIStudyPlanStatus status, Pageable pageable);

    Page<AIStudyPlan> findByStudentIdAndPlanTypeAndStatus(
            Long studentId, AIStudyPlanType planType, AIStudyPlanStatus status, Pageable pageable);
}
