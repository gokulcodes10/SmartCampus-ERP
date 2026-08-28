package smartcampus.exception;

import org.springframework.http.HttpStatus;

/**
 * Login failed. Deliberately generic - used whether the email is unknown, the password
 * is wrong, or the account is disabled, so a failed login never reveals which of those
 * was the cause (no user enumeration via the error message).
 */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException(String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }
}
