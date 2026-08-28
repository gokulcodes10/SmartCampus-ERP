package smartcampus.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import smartcampus.security.JwtAccessDeniedHandler;
import smartcampus.security.JwtAuthenticationEntryPoint;
import smartcampus.security.JwtAuthenticationFilter;

/**
 * Phase 2 security configuration: stateless JWT authentication.
 *
 * <p>{@code /api/auth/register}, {@code /api/auth/login}, and every
 * {@code /api/auth/password-reset/**} step are public — they are how a caller obtains
 * a token or recovers access in the first place. {@code /api/auth/me} and everything
 * else require a valid JWT, established by {@link JwtAuthenticationFilter} running
 * before Spring Security's own {@link UsernamePasswordAuthenticationFilter}.
 * {@code /actuator/health} (and its sub-paths) stays public for the container/health
 * check. Both the {@link JwtAuthenticationEntryPoint} (no/invalid token) and
 * {@link JwtAccessDeniedHandler} (authenticated but not permitted) produce the same
 * §47 JSON error envelope as {@code GlobalExceptionHandler} does for in-controller
 * failures.
 *
 * <p>CORS is opened for the Vite dev origin only (§61) — credentials are not needed
 * since the token travels in the {@code Authorization} header, not a cookie.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String HEALTH = "/actuator/health";
    private static final String HEALTH_SUBPATHS = "/actuator/health/**";
    private static final String AUTH_REGISTER = "/api/auth/register";
    private static final String AUTH_LOGIN = "/api/auth/login";
    private static final String AUTH_PASSWORD_RESET = "/api/auth/password-reset/**";

    /** Admin-only user administration — staff provisioning per clarification G1. */
    private static final String USERS_ADMIN = "/api/users/**";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            JwtAccessDeniedHandler jwtAccessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HEALTH, HEALTH_SUBPATHS).permitAll()
                        .requestMatchers(AUTH_REGISTER, AUTH_LOGIN).permitAll()
                        .requestMatchers(AUTH_PASSWORD_RESET).permitAll()
                        .requestMatchers(USERS_ADMIN).hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Allows the Vite dev server (default port 5173) to call the API with a Bearer token. */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
