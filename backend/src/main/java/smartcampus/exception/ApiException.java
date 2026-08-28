package smartcampus.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for exceptions that map directly to an HTTP status and the §47 error
 * envelope. {@link GlobalExceptionHandler} catches this one type and reads the status
 * and error code straight off it, so new domain exceptions in later phases only need
 * to extend this class - no change to the handler is required.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
