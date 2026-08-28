package smartcampus.exception;

import org.springframework.http.HttpStatus;

/**
 * Attempted to create a resource that violates a uniqueness rule - e.g. registering
 * with an email address that already has an account.
 */
public class DuplicateResourceException extends ApiException {

    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }
}
