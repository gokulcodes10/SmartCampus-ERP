package smartcampus.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import smartcampus.entity.Company;
import smartcampus.entity.CompanyStatus;

/**
 * Persistence access for {@link Company}.
 *
 * <p>{@link JpaSpecificationExecutor} supports dynamic filtering and pagination for admin
 * listing and search screens (§44 server-side paging).
 */
public interface CompanyRepository extends JpaRepository<Company, Long>,
    JpaSpecificationExecutor<Company> {

    /**
     * Finds a company by name, case-insensitive.
     */
    Optional<Company> findByNameIgnoreCase(String name);

    /**
     * Checks if a company with the given name exists, case-insensitive.
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Counts companies with the given status.
     */
    long countByStatus(CompanyStatus status);
}
