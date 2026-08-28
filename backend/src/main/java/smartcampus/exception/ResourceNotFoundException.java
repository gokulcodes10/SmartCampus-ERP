package smartcampus.exception;

import org.springframework.http.HttpStatus;

/** A requested resource does not exist (or does not exist for this caller). */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
