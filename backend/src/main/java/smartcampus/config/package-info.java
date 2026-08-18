/**
 * Spring {@code @Configuration} classes and externalised configuration binding.
 *
 * <p>Holds application-wide wiring — CORS, OpenAPI, WebSocket, mail, HTTP clients and
 * {@code @ConfigurationProperties} types. Configuration classes declare beans and read
 * environment-driven settings; they never contain business logic, which belongs in
 * {@code smartcampus.service}.
 */
package smartcampus.config;
