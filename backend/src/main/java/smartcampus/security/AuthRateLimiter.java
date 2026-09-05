package smartcampus.security;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The §61 auth-endpoint rate limiter: a fixed-window counter per caller key, applied
 * by {@link AuthRateLimitFilter} to {@code POST /api/auth/login}, {@code POST
 * /api/auth/register} and {@code POST /api/auth/password-reset/**} — the classic
 * credential-stuffing / registration-spam / OTP-guessing surface, which until now had
 * no limiter at all (only {@code smartcampus.service.AIRateLimiter} existed).
 *
 * <p><strong>Honesty about what this is:</strong> the counters below live in a plain
 * {@link ConcurrentHashMap} in this JVM's heap. They do NOT survive a restart (every
 * counter resets to zero on redeploy) and do NOT coordinate across replicas (behind a
 * load balancer with N instances, an attacker effectively gets N × the configured
 * limit, one window per instance). That is an accepted, documented limitation for a
 * single-instance deployment, not a claim of distributed correctness. A production
 * deployment that scales the backend horizontally needs a shared store (Redis, or a
 * database-backed counter like {@code AIRateLimiter} uses) instead.
 *
 * <p>The window is a fixed calendar-aligned bucket of {@code windowSeconds} width
 * (the same {@code epochSecond / windowSeconds} bucket every caller with the same key
 * lands in), not a sliding window — simpler and cheap to reason about, at the cost of
 * allowing a caller to burst up to {@code 2 × limit} requests across a window
 * boundary. That trade-off is acceptable for a login/registration/OTP surface, where
 * the goal is "make sustained brute-forcing slow," not perfectly smooth throttling.
 *
 * <p>Takes an injectable {@link Clock}, exactly like {@code AIRateLimiter}, so a test
 * can move time forward across a window boundary instead of sleeping in real time.
 */
@Component
public class AuthRateLimiter {

    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${smartcampus.security.auth-rate-limit.per-minute:20}")
    private int limit;

    @Value("${smartcampus.security.auth-rate-limit.window-seconds:60}")
    private int windowSeconds;

    @Autowired
    public AuthRateLimiter() {
        this(Clock.systemDefaultZone());
    }

    public AuthRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Records one attempt for {@code key} and reports whether it is still within the
     * configured limit for the current window. Every call counts, allowed or not —
     * the same "an attempt still consumed a round trip" reasoning {@code
     * AIRateLimiter} documents.
     */
    public boolean tryConsume(String key) {
        long windowSecondsLocal = Math.max(1, windowSeconds);
        long nowEpochSecond = Instant.now(clock).getEpochSecond();
        long windowStart = (nowEpochSecond / windowSecondsLocal) * windowSecondsLocal;

        Bucket bucket = buckets.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStart != windowStart) {
                return new Bucket(windowStart);
            }
            return existing;
        });

        int countSoFar = bucket.count.incrementAndGet();
        return countSoFar <= Math.max(1, limit);
    }

    /** Test/diagnostic helper: drops all recorded state. Never called in production code paths. */
    void reset() {
        buckets.clear();
    }

    private static final class Bucket {
        private final long windowStart;
        private final AtomicInteger count = new AtomicInteger(0);

        private Bucket(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
