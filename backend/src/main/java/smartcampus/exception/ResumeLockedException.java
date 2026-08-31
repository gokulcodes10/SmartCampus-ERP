package smartcampus.exception;

import org.springframework.http.HttpStatus;

/**
 * A resume version with {@code lockedAt != null} - one that has been attached to a
 * placement application - was targeted by an update or delete. The §35 artifact
 * guarantee makes such a version permanently read-only; {@code duplicate()} is the
 * escape hatch, so the message should point the caller there.
 */
public class ResumeLockedException extends ApiException {

    public ResumeLockedException(String message) {
        super(HttpStatus.CONFLICT, "RESUME_LOCKED", message);
    }
}
