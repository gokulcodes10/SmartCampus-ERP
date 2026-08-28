package smartcampus.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

    /**
     * Phase 3 core-academic routes. Department/Course/Subject reads are open to any
     * authenticated role (students and faculty browse the catalog); writes are
     * ADMIN-only. Enrollment and faculty-subject-assignment management is admin-only
     * end to end — faculty authorization for their own assignments is a service-layer
     * concern ({@code AcademicAccessGuard}, from Phase 4 onward), not a route rule here.
     * Student and Faculty profile routes ({@code /api/students/**}, {@code
     * /api/faculty/**}) are deliberately left off the matcher list below: role and
     * ownership are enforced centrally in {@code StudentService}/{@code FacultyService}
     * (see their javadoc) and both fall through to the {@code anyRequest().authenticated()}
     * rule, same as today.
     */
    private static final String DEPARTMENTS = "/api/departments";
    private static final String DEPARTMENTS_SUBPATHS = "/api/departments/**";
    private static final String COURSES = "/api/courses";
    private static final String COURSES_SUBPATHS = "/api/courses/**";
    private static final String SUBJECTS = "/api/subjects";
    private static final String SUBJECTS_SUBPATHS = "/api/subjects/**";
    private static final String ENROLLMENTS_ADMIN = "/api/enrollments/**";
    private static final String FACULTY_SUBJECT_ASSIGNMENTS_ADMIN = "/api/faculty-subject-assignments/**";

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
                        .requestMatchers(HttpMethod.POST, DEPARTMENTS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, DEPARTMENTS_SUBPATHS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, DEPARTMENTS_SUBPATHS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, DEPARTMENTS, DEPARTMENTS_SUBPATHS).authenticated()
                        .requestMatchers(HttpMethod.POST, COURSES).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, COURSES_SUBPATHS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, COURSES_SUBPATHS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, COURSES, COURSES_SUBPATHS).authenticated()
                        .requestMatchers(HttpMethod.POST, SUBJECTS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, SUBJECTS_SUBPATHS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, SUBJECTS_SUBPATHS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, SUBJECTS, SUBJECTS_SUBPATHS).authenticated()
                        .requestMatchers(ENROLLMENTS_ADMIN).hasRole("ADMIN")
                        .requestMatchers(FACULTY_SUBJECT_ASSIGNMENTS_ADMIN).hasRole("ADMIN")
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
