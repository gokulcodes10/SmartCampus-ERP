package smartcampus.seed;

import org.springframework.data.jpa.repository.JpaRepository;
import smartcampus.entity.Role;
import smartcampus.entity.User;

/**
 * A second, narrow Spring Data repository over {@link User}, scoped to this package.
 *
 * <p>{@code smartcampus.repository.UserRepository} is an existing, shared file this
 * wave routes exclusively through other agents (see AGENT_CONTEXT.md file-ownership
 * rules), so {@link AdminBootstrapRunner} cannot add {@code existsByRole} to it.
 * Spring Data happily supports more than one repository interface over the same
 * entity — this one exists solely to answer "does any ADMIN user already exist?"
 * without touching a file this task does not own.
 */
public interface AdminAccountRepository extends JpaRepository<User, Long> {

    boolean existsByRole(Role role);
}
