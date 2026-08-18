/**
 * Application exceptions and the global exception handler.
 *
 * <p>Domain and API exceptions are declared here alongside the
 * {@code @RestControllerAdvice} that translates them — plus validation and security
 * failures — into the single consistent JSON error envelope used by every endpoint.
 * Centralising this keeps error handling out of controllers and services.
 */
package smartcampus.exception;
