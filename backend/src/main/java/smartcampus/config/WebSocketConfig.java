package smartcampus.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import smartcampus.realtime.JwtHandshakeInterceptor;
import smartcampus.realtime.NotificationWebSocketHandler;

/**
 * Registers the raw {@code /ws/notifications} WebSocket endpoint.
 *
 * <p>Deliberately NOT {@code @EnableWebSocketMessageBroker} / STOMP - see {@code
 * smartcampus.realtime} package-info for why. Authentication happens entirely in
 * {@link JwtHandshakeInterceptor} during the handshake; WebSocket upgrades are not
 * subject to browser CORS, so {@link #setAllowedOrigins} below IS the origin check for
 * this endpoint, not the servlet CORS filter that protects the REST API.
 *
 * <p>Reuses the existing {@code smartcampus.cors.allowed-origins} property (the same
 * one {@code SecurityConfig} reads for the REST API) rather than inventing a second
 * origin list that could drift out of sync.
 *
 * <p><strong>Integrator action required:</strong> {@code SecurityConfig}'s filter
 * chain currently ends in {@code anyRequest().authenticated()}, which 401s this
 * handshake before it ever reaches {@link JwtHandshakeInterceptor} - a WebSocket
 * upgrade carries no {@code Authorization} header for the JWT filter to find. Add
 * {@code .requestMatchers("/ws/**").permitAll()} to the {@code authorizeHttpRequests}
 * block. The endpoint is still fully authenticated - by this interceptor, not by the
 * filter chain.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final List<String> allowedOrigins;

    public WebSocketConfig(
            NotificationWebSocketHandler notificationWebSocketHandler,
            JwtHandshakeInterceptor jwtHandshakeInterceptor,
            @Value("${smartcampus.cors.allowed-origins:http://localhost:5173,http://localhost:5174}")
                    List<String> allowedOrigins) {
        this.notificationWebSocketHandler = notificationWebSocketHandler;
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
                .addHandler(notificationWebSocketHandler, "/ws/notifications")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins(allowedOrigins.toArray(new String[0]));
    }
}
