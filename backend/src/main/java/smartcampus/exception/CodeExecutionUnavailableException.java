package smartcampus.exception;

import org.springframework.http.HttpStatus;

/**
 * The configured code-execution backend (Judge0) could not produce a real verdict:
 * connection failure, a non-2xx response, an unparseable body, a token count mismatch,
 * or a poll timeout. This is the ONLY outcome allowed when a verdict cannot be
 * obtained - {@code smartcampus.service.Judge0Service} never fabricates, pads, or
 * defaults a status. See PROJECT_PLAN.md §69 / §70 and G10 (Judge0 has no reachable
 * endpoint on the local build machine, so this exception is the expected, honest
 * outcome of any execution call there).
 *
 * <p>{@link GlobalExceptionHandler#handleApiException} already renders this into the
 * §47 envelope with status 503 and error code {@code EXECUTION_UNAVAILABLE} - no
 * handler change is needed.
 */
public class CodeExecutionUnavailableException extends ApiException {

    public CodeExecutionUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "EXECUTION_UNAVAILABLE", message);
    }

    public CodeExecutionUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "EXECUTION_UNAVAILABLE", message);
        initCause(cause);
    }
}
