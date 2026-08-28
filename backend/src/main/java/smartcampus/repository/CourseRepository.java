package smartcampus.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import smartcampus.entity.Course;

/**
 * Persistence access for {@link Course}.
 *
 * <p>{@code code} is unique. {@link JpaSpecificationExecutor} supports dynamic filtering
 * and pagination for admin listing and search screens. {@link #findByDepartmentId}
 * backs Phase 3 admin server-side filtering by department.
 */
public interface CourseRepository extends JpaRepository<Course, Long>,
    JpaSpecificationExecutor<Course> {

    Optional<Course> findByCode(String code);

    boolean existsByCode(String code);

    List<Course> findByDepartmentId(Long departmentId);
}
