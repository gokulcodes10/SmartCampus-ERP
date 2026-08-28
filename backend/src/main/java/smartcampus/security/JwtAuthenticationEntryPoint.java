package smartcampus.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import smartcampus.exception.ErrorResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the §47 error envelope for a request rejected before it ever reaches a
 * controller: no bearer token, or one that {@link JwtAuthenticationFilter} could not
 * turn into an authentication (missing, malformed, expired, tampered, or naming an
 * unknown/disabled user).
 *
 * <p>{@code GlobalExceptionHandler} cannot see this case - it only runs for
 * exceptions raised while Spring MVC is dispatching to a controller, and an
 * unauthenticated request never gets that far. {@code SecurityConfig} must wire this
 * bean in via {@code .exceptionHandling(e -> e.authenticationEntryPoint(...))}.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter()
                .write(
                        objectMapper.writeValueAsString(
                                ErrorResponse.of(
                                        HttpStatus.UNAUTHORIZED.value(),
                                        "UNAUTHORIZED",
                                        "Authentication is required to access this resource.",
                                        request.getRequestURI())));
    }
}
