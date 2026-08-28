package smartcampus.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import smartcampus.entity.Faculty;

/**
 * Persistence access for {@link Faculty}.
 *
 * <p>{@code employee_code} is unique. {@link JpaSpecificationExecutor} supports dynamic
 * filtering and pagination for admin listing and search screens. {@link #findByUserId}
 * backs the faculty profile lookup.
 */
public interface FacultyRepository extends JpaRepository<Faculty, Long>,
    JpaSpecificationExecutor<Faculty> {

    Optional<Faculty> findByUserId(Long userId);

    Optional<Faculty> findByEmployeeCode(String employeeCode);

    boolean existsByEmployeeCode(String employeeCode);
}
