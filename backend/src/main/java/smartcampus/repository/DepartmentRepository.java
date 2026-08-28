package smartcampus.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import smartcampus.entity.Department;

/**
 * Persistence access for {@link Department}.
 *
 * <p>{@code code} and {@code name} are unique. {@link JpaSpecificationExecutor} supports
 * dynamic filtering and pagination for admin listing and search screens.
 */
public interface DepartmentRepository extends JpaRepository<Department, Long>,
    JpaSpecificationExecutor<Department> {

    Optional<Department> findByCode(String code);

    Optional<Department> findByName(String name);

    boolean existsByCode(String code);

    boolean existsByName(String name);
}
