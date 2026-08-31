package smartcampus.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown by {@code AIRateLimiter#checkAllowed} when a caller has exceeded either the
 * per-minute or per-day AI request cap (§61). {@code message} names the exact window
 * and limit that was crossed, e.g. {@code "AI request limit reached: 5 requests per
 * minute. Try again shortly."}.
 *
 * <p>{@link GlobalExceptionHandler#handleApiException} already renders this into the
 * §47 envelope with status 429 and error code {@code RATE_LIMIT_EXCEEDED} — no handler
 * change is needed.
 */
public class AIRateLimitExceededException extends ApiException {

    public AIRateLimitExceededException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", message);
    }
}
