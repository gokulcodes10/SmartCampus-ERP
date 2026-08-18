package smartcampus.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Phase 1 security baseline.
 *
 * <p>Spring Security locks down every endpoint and prints a generated password when no
 * {@link SecurityFilterChain} bean is present. That would break the Phase 1 checkpoint,
 * which requires an unauthenticated {@code GET /actuator/health}. This chain therefore
 * opens exactly that endpoint and leaves <em>everything else</em> authenticated.
 *
 * <p>The API is stateless, so the servlet session and CSRF token are both disabled and
 * an unauthenticated request gets a bare {@code 401} instead of a login-form redirect.
 *
 * <p>Phase 2 replaces this class with the real configuration: JWT filter, public
 * {@code /api/auth/**} endpoints, role-based rules and method security. Nothing here
 * grants access to application data.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Actuator health, including the component sub-paths such as {@code /actuator/health/db}. */
    private static final String HEALTH = "/actuator/health";
    private static final String HEALTH_SUBPATHS = "/actuator/health/**";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HEALTH, HEALTH_SUBPATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

        return http.build();
    }
}
