package smartcampus.exception;

import org.springframework.http.HttpStatus;

/**
 * A request violates a domain rule that field-level Bean Validation cannot express -
 * e.g. self-registration attempting a role other than STUDENT (PROJECT_PLAN.md
 * clarification G1). Field-level validation failures instead go through
 * {@code MethodArgumentNotValidException}, handled separately in
 * {@link GlobalExceptionHandler} so it can report every invalid field, not just one.
 */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }
}
