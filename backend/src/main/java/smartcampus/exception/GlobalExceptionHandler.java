package smartcampus.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates every exception that reaches a controller into the §47 JSON error
 * envelope ({@link ErrorResponse}). Nothing here ever returns a stack trace to the
 * caller - unexpected exceptions are logged server-side and reduced to a generic
 * message, backing up {@code server.error.include-stacktrace=never}.
 *
 * <p>This only covers exceptions raised while Spring MVC is dispatching to a
 * controller. A request rejected by the security filter chain before it gets that far
 * - no token, or one {@code JwtAuthenticationFilter} could not authenticate - never
 * reaches this class; {@code smartcampus.security.JwtAuthenticationEntryPoint} and
 * {@code JwtAccessDeniedHandler} produce the same envelope for that path instead, once
 * {@code SecurityConfig} wires them in.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Every domain exception in {@code smartcampus.exception} carries its own status and code. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(
            ApiException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), request);
    }

    /**
     * {@code @Valid} request-body failures. Every invalid field is reported, not just
     * the first, so the message is actually useful to a caller.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Validation failed.";
        }
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request);
    }

    /** Missing, empty, or syntactically invalid JSON request body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_REQUEST",
                "Request body is missing or malformed.",
                request);
    }

    /** Thrown by {@code AuthenticationManager} during login - wrong password or unknown email. */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {
        return build(
                HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid email or password.", request);
    }

    /** Thrown by method security (e.g. {@code @PreAuthorize}) once inside the controller layer. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        return build(
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "You do not have permission to perform this action.",
                request);
    }

    /**
     * No handler matched the request path. Spring raises this for any unmapped route;
     * without an explicit handler the catch-all below would swallow it and answer a
     * misleading 500 for what is simply a wrong URL.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource does not exist.", request);
    }

    /**
     * Database constraint violations (e.g. FK constraint violated when trying to delete
     * an entity with dependents). Produces a useful message to the caller instead of a
     * raw database error.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        String message = "Operation failed due to data integrity constraints. "
                + "The entity may have dependents that prevent this operation.";
        return build(HttpStatus.CONFLICT, "CONFLICT", message, request);
    }

    /**
     * A client-supplied {@code sort} (or other query-bound {@code Pageable}) property
     * that does not exist on the target entity. Spring Data JPA validates {@code sort}
     * against the JPA metamodel inside {@code SimpleJpaRepository} and throws this
     * (frequently wrapping a {@code PropertyReferenceException}) before any SQL is
     * built — no injection ever reaches the database, but an unknown/malicious column
     * name is a caller input error, not a server fault, so it belongs at 400, not the
     * 500 the catch-all below would otherwise produce (§61 input validation /
     * "SQL injection protection... reject an arbitrary column name").
     *
     * <p>Which of the two exception types below is thrown depends on the shape of the
     * offending value: a {@code sort} value containing characters that fail Spring
     * Data's own sort-expression syntax check (e.g. punctuation from an injection
     * payload) throws {@link InvalidDataAccessApiUsageException}; a syntactically
     * ordinary but nonexistent property name (e.g. {@code thisColumnDoesNotExist})
     * instead resolves as far as the JPA metamodel lookup and throws {@link
     * PropertyReferenceException} directly - confirmed live, both are exercised by
     * {@code smartcampus.security61.SqlInjectionProtectionTest}. Both are handled
     * identically here: neither is a server fault.
     */
    @ExceptionHandler({InvalidDataAccessApiUsageException.class, PropertyReferenceException.class})
    public ResponseEntity<ErrorResponse> handleInvalidDataAccessApiUsage(
            RuntimeException ex, HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Invalid request parameter (e.g. an unknown sort/column name).",
                request);
    }

    /** A required {@code @RequestParam} the caller did not supply (e.g. omitting {@code date}
     * on {@code GET /api/attendance/roster}) - a caller input error, not a server fault. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Missing required parameter '" + ex.getParameterName() + "'.",
                request);
    }

    /** A query/path value that cannot be converted to the parameter's type
     * (e.g. {@code ?subjectId=abc} where a numeric id is expected). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Invalid value for parameter '" + ex.getName() + "'.",
                request);
    }

    /** Last resort: never let an unmapped exception leak internals or a stack trace to the caller. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred.",
                request);
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String error, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), error, message, request.getRequestURI()));
    }
}
