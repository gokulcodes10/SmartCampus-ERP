package smartcampus.realtime;

import io.jsonwebtoken.Claims;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import smartcampus.entity.User;
import smartcampus.repository.UserRepository;
import smartcampus.security.JwtService;

/**
 * Authenticates the {@code /ws/notifications} upgrade using the existing JWT stack.
 *
 * <p>A browser cannot set an {@code Authorization} header on a WebSocket upgrade, so
 * the token travels in the {@code token} query parameter instead. This class is the
 * ENTIRE authentication boundary for the socket: {@code SecurityConfig}'s filter chain
 * must {@code permitAll()} this path (there is no bearer header for it to check), so
 * everything from "is this token valid" to "is this account enabled" happens here,
 * once, before the handshake completes. The resolved identity is written into the
 * handshake attributes and is the ONLY source of truth the handler and registry ever
 * consult - nothing arriving later, on the open socket, is ever treated as identity.
 *
 * <p>Never logs the token, the query string, or the raw request URI (it carries the
 * token) - only the resolved userId, and only after successful resolution.
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    public static final String ATTR_USER_ID = "smartcampus.userId";
    public static final String ATTR_EMAIL = "smartcampus.email";
    public static final String ATTR_ROLE = "smartcampus.role";

    private static final String TOKEN_PARAM = "token";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtHandshakeInterceptor(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String token =
                UriComponentsBuilder.fromUri(request.getURI())
                        .build()
                        .getQueryParams()
                        .getFirst(TOKEN_PARAM);

        if (token == null || token.isBlank()) {
            return reject(response);
        }

        Optional<Claims> claims = jwtService.tryParse(token);
        if (claims.isEmpty()) {
            return reject(response);
        }

        String email = claims.get().getSubject();
        if (email == null || email.isBlank()) {
            return reject(response);
        }

        Optional<User> user = userRepository.findByEmail(email);
        if (user.isEmpty() || !user.get().isEnabled()) {
            return reject(response);
        }

        User resolved = user.get();
        attributes.put(ATTR_USER_ID, resolved.getId());
        attributes.put(ATTR_EMAIL, resolved.getEmail());
        attributes.put(ATTR_ROLE, resolved.getRole().name());
        log.debug("WebSocket handshake authenticated for userId={}", resolved.getId());
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // no-op
    }

    private boolean reject(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }
}
