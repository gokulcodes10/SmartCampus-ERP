package smartcampus.repository;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import smartcampus.entity.AIRequestLog;

/**
 * Persistence access for {@link AIRequestLog}.
 *
 * <p>{@link #countByUserIdAndCreatedAtGreaterThanEqual} backs the §61 rate limiter: run
 * once for the per-minute window and once for the per-day window, both served by
 * {@code idx_ai_request_logs_user_created}.
 */
public interface AIRequestLogRepository extends JpaRepository<AIRequestLog, Long> {

    long countByUserIdAndCreatedAtGreaterThanEqual(Long userId, LocalDateTime since);
}
