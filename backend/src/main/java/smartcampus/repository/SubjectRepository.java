package smartcampus.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import smartcampus.entity.Subject;

/**
 * Persistence access for {@link Subject}.
 *
 * <p>{@code code} is unique. {@link JpaSpecificationExecutor} supports dynamic filtering
 * and pagination for admin listing and search screens. {@link #findByCourseId} and
 * {@link #findByCourseIdAndSemester} back Phase 3 admin filtering by course and semester.
 */
public interface SubjectRepository extends JpaRepository<Subject, Long>,
    JpaSpecificationExecutor<Subject> {

    Optional<Subject> findByCode(String code);

    boolean existsByCode(String code);

    List<Subject> findByCourseId(Long courseId);

    List<Subject> findByCourseIdAndSemester(Long courseId, Integer semester);
}
