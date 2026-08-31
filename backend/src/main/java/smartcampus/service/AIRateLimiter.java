package smartcampus.service;

import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import smartcampus.entity.User;
import smartcampus.exception.AIRateLimitExceededException;
import smartcampus.repository.AIRequestLogRepository;

/**
 * The §61 per-user AI request cap: a rolling per-minute window and a rolling per-day
 * window, both counted from {@code ai_request_logs} — every attempt, successful or not,
 * counts (a failed call still consumed a provider round trip, so it must still count
 * against the limit or a caller could hammer a failing provider forever).
 *
 * <p>Takes a {@link Clock} as a constructor parameter, defaulting to {@link
 * Clock#systemDefaultZone()} — deliberately NOT {@link Clock#systemUTC()}. {@code
 * ai_request_logs.created_at} is stamped by Hibernate's {@code @CreationTimestamp} in
 * the JVM's default zone, so a UTC clock would silently mis-size the window on any
 * non-UTC JVM. The second constructor lets {@code AIRateLimiterTest} move time without
 * sleeping.
 */
@Component
public class AIRateLimiter {

    private final AIRequestLogRepository aiRequestLogRepository;
    private final Clock clock;

    @Value("${smartcampus.ai.rate-limit.per-minute:5}")
    private int perMinute;

    @Value("${smartcampus.ai.rate-limit.per-day:100}")
    private int perDay;

    @Autowired
    public AIRateLimiter(AIRequestLogRepository aiRequestLogRepository) {
        this(aiRequestLogRepository, Clock.systemDefaultZone());
    }

    public AIRateLimiter(AIRequestLogRepository aiRequestLogRepository, Clock clock) {
        this.aiRequestLogRepository = aiRequestLogRepository;
        this.clock = clock;
    }

    /**
     * Throws {@link AIRateLimitExceededException} the moment either window is at or
     * over its cap. The minute window is always checked first, so a caller over both
     * limits at once sees the minute message.
     */
    public void checkAllowed(User user) {
        LocalDateTime now = LocalDateTime.now(clock);

        long usedLastMinute =
                aiRequestLogRepository.countByUserIdAndCreatedAtGreaterThanEqual(
                        user.getId(), now.minusMinutes(1));
        if (usedLastMinute >= perMinute) {
            throw new AIRateLimitExceededException(
                    "AI request limit reached: " + perMinute + " requests per minute. Try again shortly.");
        }

        long usedToday =
                aiRequestLogRepository.countByUserIdAndCreatedAtGreaterThanEqual(
                        user.getId(), now.minusDays(1));
        if (usedToday >= perDay) {
            throw new AIRateLimitExceededException(
                    "AI request limit reached: " + perDay + " requests per day. Try again tomorrow.");
        }
    }

    /** A non-throwing read of the same two windows, for {@code GET /api/ai/status}. */
    public AIRateLimitSnapshot snapshot(User user) {
        LocalDateTime now = LocalDateTime.now(clock);

        long usedLastMinute =
                aiRequestLogRepository.countByUserIdAndCreatedAtGreaterThanEqual(
                        user.getId(), now.minusMinutes(1));
        long usedToday =
                aiRequestLogRepository.countByUserIdAndCreatedAtGreaterThanEqual(
                        user.getId(), now.minusDays(1));

        long remainingMinute = Math.max(0, perMinute - usedLastMinute);
        long remainingDay = Math.max(0, perDay - usedToday);

        return new AIRateLimitSnapshot(perMinute, perDay, usedLastMinute, usedToday, remainingMinute, remainingDay);
    }
}
