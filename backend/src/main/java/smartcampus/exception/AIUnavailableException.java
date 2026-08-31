package smartcampus.exception;

import org.springframework.http.HttpStatus;

/**
 * The configured AI provider (Groq, see {@code smartcampus.service.GroqAIService})
 * could not produce a real completion: not configured, connection failure, a non-2xx
 * response, an unparseable body, or an empty/blank response. This is the ONLY outcome
 * allowed when a real answer cannot be obtained - {@code GroqAIService} never
 * fabricates, caches, or defaults a reply. See PROJECT_PLAN.md §69 / §70.
 *
 * <p>{@link GlobalExceptionHandler#handleApiException} already renders this into the
 * §47 envelope with status 503 and error code {@code AI_UNAVAILABLE} - no handler
 * change is needed.
 */
public class AIUnavailableException extends ApiException {

    public AIUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", message);
    }

    public AIUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", message);
        initCause(cause);
    }
}
