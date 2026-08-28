package smartcampus.exception;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * The §47 API error envelope, produced by {@link GlobalExceptionHandler} (and by
 * {@code smartcampus.security.JwtAuthenticationEntryPoint} /
 * {@code JwtAccessDeniedHandler} for failures that never reach a controller):
 *
 * <pre>{@code
 * {
 *   "timestamp": "2026-08-18T10:15:30Z",
 *   "status": 404,
 *   "error": "NOT_FOUND",
 *   "message": "Student not found",
 *   "path": "/api/students/10"
 * }
 * }</pre>
 */
public record ErrorResponse(String timestamp, int status, String error, String message, String path) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(
                Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
                status,
                error,
                message,
                path);
    }
}
