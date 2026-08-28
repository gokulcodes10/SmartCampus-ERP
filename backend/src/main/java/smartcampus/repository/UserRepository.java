package smartcampus.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import smartcampus.entity.User;

/**
 * Persistence access for {@link User}. {@code email} is unique and, under the
 * {@code utf8mb4_unicode_ci} collation used across the schema, comparisons are already
 * case-insensitive at the database level, so no separate case-insensitive lookup is
 * needed here.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
