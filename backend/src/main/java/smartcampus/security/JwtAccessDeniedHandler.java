package smartcampus.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import smartcampus.exception.ErrorResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the §47 error envelope for a request an <em>authenticated</em> principal is
 * not allowed to make - a role-based {@code authorizeHttpRequests} rule at the
 * filter-chain level, rather than an in-controller {@code @PreAuthorize} check (which
 * {@code GlobalExceptionHandler} already covers, since it happens inside MVC
 * dispatch). {@code SecurityConfig} must wire this bean in via
 * {@code .exceptionHandling(e -> e.accessDeniedHandler(...))}.
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter()
                .write(
                        objectMapper.writeValueAsString(
                                ErrorResponse.of(
                                        HttpStatus.FORBIDDEN.value(),
                                        "FORBIDDEN",
                                        "You do not have permission to perform this action.",
                                        request.getRequestURI())));
    }
}
