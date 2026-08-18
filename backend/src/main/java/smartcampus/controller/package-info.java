/**
 * REST controllers — the HTTP layer.
 *
 * <p>Controllers handle HTTP concerns only: routing, request binding and validation of
 * request DTOs, authorization annotations, and mapping a service result onto a response
 * DTO and status code. They delegate all business logic to {@code smartcampus.service}
 * and must never talk to a repository or expose a JPA entity directly.
 */
package smartcampus.controller;
