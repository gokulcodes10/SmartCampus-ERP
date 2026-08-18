/**
 * Stateless helpers shared across layers.
 *
 * <p>Small, dependency-free utilities — date and academic-term arithmetic, formatting,
 * secure random token generation, common constants. Anything here must be free of
 * business rules and of Spring context dependencies; if it needs either, it belongs in
 * {@code smartcampus.service} or {@code smartcampus.config}.
 */
package smartcampus.util;
