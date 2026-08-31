package smartcampus.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * Phase 4 academic-operations routes. Grade bands are reference data: reads are open to
     * any authenticated role (a student must be able to see the scale their grade came from),
     * writes are ADMIN-only. Attendance, exams, marks and the faculty "my classes" lookup are
     * left at authenticated(): the faculty write rule is a (subject, academicYear, semester,
     * section) assignment check that a URL pattern cannot express, so it is enforced in the
     * service layer through ScopedWriteAuthorizer -> AcademicAccessGuard, the same way
     * Student/Faculty profile ownership already is.
     */
    private static final String ATTENDANCE = "/api/attendance/**";
    private static final String EXAMS = "/api/exams/**";
    private static final String MARKS = "/api/marks/**";
    private static final String TEACHING = "/api/teaching/**";
    private static final String GRADE_BANDS = "/api/grade-bands";
    private static final String GRADE_BANDS_SUBPATHS = "/api/grade-bands/**";

    /**
     * Phase 7 coding routes. Hidden test cases are ADMIN-only for EVERY method, including
     * GET, so that matcher MUST precede the general problems GET rule below or hidden test
     * cases become readable by any authenticated student (would defeat clarification G3).
     * Students register themselves for a contest; the service layer enforces "must be a
     * student". STUDENT-vs-ADMIN-vs-FACULTY and per-row ownership for submissions are
     * enforced centrally in CodingSubmissionService, the same pattern
     * StudentService/FacultyService already use.
     */
    private static final String PROBLEMS = "/api/problems";
    private static final String PROBLEMS_SUBPATHS = "/api/problems/**";
    private static final String PROBLEM_TEST_CASES = "/api/problems/*/test-cases";
    private static final String PROBLEM_TEST_CASES_SUBPATHS = "/api/problems/*/test-cases/**";
    private static final String CODING_SUBPATHS = "/api/coding/**";
    private static final String CONTESTS = "/api/contests";
    private static final String CONTESTS_SUBPATHS = "/api/contests/**";
    private static final String CONTEST_REGISTER = "/api/contests/*/register";
    private static final String CONTEST_RECOMPUTE = "/api/contests/*/recompute";
    private static final String LEADERBOARD_SUBPATHS = "/api/leaderboard/**";

    /**
     * Phase 5 analytics routes. Performance bands are reference data, exactly like
     * grade bands: reads are open to any authenticated role (a student must be able
     * to see the thresholds they were judged against), writes are ADMIN-only. There
     * is no POST and no DELETE - the four categories are a closed set, only their
     * thresholds are configurable. /api/analytics/** stays at authenticated(): the
     * faculty rule is an assignment-tuple check no URL pattern can express, and is
     * enforced in AnalyticsScopeResolver -> AcademicAccessGuard.
     */
    private static final String ANALYTICS = "/api/analytics/**";
    private static final String PERFORMANCE_BANDS = "/api/performance-bands";
    private static final String PERFORMANCE_BANDS_SUBPATHS = "/api/performance-bands/**";

    /**
     * Phase 6 AI routes. {@code GET /api/ai/models} is ADMIN-only (it is a diagnostic
     * over the provider's live model list) and MUST be matched before the general
     * /api/ai/** rule below or it would just fall through to authenticated(). Everything
     * else under /api/ai is STUDENT-only, but "is this caller a student and does this
     * conversation belong to them" is a service-layer question, enforced in
     * AIAssistantService/AIStudyPlanService via ScopedWriteAuthorizer - the same pattern
     * as attendance/marks.
     */
    private static final String AI_MODELS = "/api/ai/models";
    private static final String AI_SUBPATHS = "/api/ai/**";

    /**
     * Phase 8 placement routes. {@code GET /api/jobs/*}/eligible-students} is ADMIN-only
     * and MUST be matched before the general jobs GET rule below, or it would fall
     * through to authenticated() and leak a CGPA-driven roster to any authenticated
     * caller. {@code /api/applications/**} stays at authenticated(): a student may read
     * their OWN application but not another's, which is a per-row check a route matcher
     * cannot express, so it is enforced in PlacementApplicationService via
     * ScopedWriteAuthorizer, the same pattern already used for attendance/marks/coding.
     */
    private static final String COMPANIES = "/api/companies";
    private static final String COMPANIES_SUBPATHS = "/api/companies/**";
    private static final String JOBS = "/api/jobs";
    private static final String JOBS_SUBPATHS = "/api/jobs/**";
    private static final String JOB_ELIGIBLE_STUDENTS = "/api/jobs/*/eligible-students";
    private static final String APPLICATIONS = "/api/applications";
    private static final String APPLICATIONS_SUBPATHS = "/api/applications/**";
    private static final String PLACEMENT_SUBPATHS = "/api/placement/**";

    /**
     * Phase 10 interview routes. ORDER IS LOAD-BEARING, exactly like the Phase 7
     * hidden-test-case rule: {@code POST .../generate} and {@code PUT .../{id}/progress}
     * are STUDENT actions living under the same base path as the ADMIN-only bank
     * authoring routes, so they MUST be matched BEFORE the broader ADMIN matchers or a
     * student can never generate or mark a question. Role and per-row ownership are
     * enforced in InterviewQuestionService / InterviewQuestionGenerationService /
     * InterviewSchedulingService, the same pattern StudentService and AIAssistantService
     * already use.
     */
    private static final String INTERVIEW_QUESTIONS = "/api/interview-questions";
    private static final String INTERVIEW_QUESTIONS_SUBPATHS = "/api/interview-questions/**";
    private static final String INTERVIEW_QUESTIONS_GENERATE = "/api/interview-questions/generate";
    private static final String INTERVIEW_QUESTION_PROGRESS = "/api/interview-questions/*/progress";
    private static final String INTERVIEWS = "/api/interviews";
    private static final String INTERVIEWS_SUBPATHS = "/api/interviews/**";

    /**
     * Phase 9 resume routes. Role and per-row ownership (a student may only touch their
     * own resumes; ADMIN may read a PDF for an applicant) are enforced in ResumeService,
     * the same pattern as attendance/marks/coding/applications above.
     */
    private static final String RESUMES = "/api/resumes";
    private static final String RESUMES_SUBPATHS = "/api/resumes/**";

    /**
     * Phase 11 real-time routes. ORDER IS LOAD-BEARING twice over:
     * (1) /ws/** MUST be permitAll — a browser cannot set an Authorization header on a
     *     WebSocket handshake, so the upgrade request arrives with no bearer token and
     *     anyRequest().authenticated() would 401 it before JwtHandshakeInterceptor ever
     *     runs. The socket is NOT unauthenticated: JwtHandshakeInterceptor validates the
     *     ?token= JWT, resolves the user, requires enabled=true, and returns 401 itself
     *     otherwise. permitAll here means "Spring Security does not gate the upgrade",
     *     not "anyone may connect".
     * (2) GET /api/announcements/manage is ADMIN-only and MUST precede the general
     *     announcements GET rule, exactly like the job-eligible-students matcher does.
     */
    private static final String WEBSOCKET = "/ws/**";
    private static final String NOTIFICATIONS = "/api/notifications";
    private static final String NOTIFICATIONS_SUBPATHS = "/api/notifications/**";
    private static final String ANNOUNCEMENTS = "/api/announcements";
    private static final String ANNOUNCEMENTS_SUBPATHS = "/api/announcements/**";
    private static final String ANNOUNCEMENTS_MANAGE = "/api/announcements/manage";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final List<String> allowedOrigins;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            JwtAccessDeniedHandler jwtAccessDeniedHandler,
            @Value("${smartcampus.cors.allowed-origins:http://localhost:5173,http://localhost:5174}")
                    List<String> allowedOrigins) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
        this.allowedOrigins = allowedOrigins;
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
                        .requestMatchers(HttpMethod.POST, GRADE_BANDS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, GRADE_BANDS_SUBPATHS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, GRADE_BANDS_SUBPATHS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, GRADE_BANDS, GRADE_BANDS_SUBPATHS).authenticated()
                        .requestMatchers(ATTENDANCE, EXAMS, MARKS, TEACHING).authenticated()
                        // Hidden test cases are ADMIN-only for EVERY method, including GET. This must
                        // precede the general /api/problems GET rule below.
                        .requestMatchers(PROBLEM_TEST_CASES, PROBLEM_TEST_CASES_SUBPATHS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, PROBLEMS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, PROBLEMS_SUBPATHS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, PROBLEMS_SUBPATHS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, PROBLEMS, PROBLEMS_SUBPATHS).authenticated()
                        // Students register themselves; the service layer enforces "must be a student".
                        .requestMatchers(HttpMethod.POST, CONTEST_REGISTER).authenticated()
                        .requestMatchers(HttpMethod.POST, CONTEST_RECOMPUTE).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, CONTESTS, CONTESTS_SUBPATHS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, CONTESTS_SUBPATHS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, CONTESTS_SUBPATHS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, CONTESTS, CONTESTS_SUBPATHS).authenticated()
                        .requestMatchers(LEADERBOARD_SUBPATHS).authenticated()
                        // /api/coding/** is authenticated at the route level; STUDENT-vs-ADMIN-vs-FACULTY
                        // and per-row ownership are enforced centrally in CodingSubmissionService, the
                        // same pattern StudentService/FacultyService already use.
                        .requestMatchers(CODING_SUBPATHS).authenticated()
                        .requestMatchers(HttpMethod.PUT, PERFORMANCE_BANDS_SUBPATHS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, PERFORMANCE_BANDS, PERFORMANCE_BANDS_SUBPATHS)
                                .authenticated()
                        .requestMatchers(ANALYTICS).authenticated()
                        .requestMatchers(HttpMethod.GET, AI_MODELS).hasRole("ADMIN")
                        .requestMatchers(AI_SUBPATHS).authenticated()
                        .requestMatchers(HttpMethod.POST, COMPANIES).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, COMPANIES_SUBPATHS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, COMPANIES_SUBPATHS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, COMPANIES, COMPANIES_SUBPATHS).authenticated()
                        .requestMatchers(HttpMethod.GET, JOB_ELIGIBLE_STUDENTS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, JOBS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, JOBS_SUBPATHS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, JOBS_SUBPATHS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, JOBS_SUBPATHS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, JOBS, JOBS_SUBPATHS).authenticated()
                        .requestMatchers(PLACEMENT_SUBPATHS).hasRole("ADMIN")
                        .requestMatchers(APPLICATIONS, APPLICATIONS_SUBPATHS).authenticated()
                        // STUDENT actions first - they sit under the ADMIN-only bank base path.
                        .requestMatchers(HttpMethod.POST, INTERVIEW_QUESTIONS_GENERATE).authenticated()
                        .requestMatchers(HttpMethod.PUT, INTERVIEW_QUESTION_PROGRESS).authenticated()
                        .requestMatchers(HttpMethod.POST, INTERVIEW_QUESTIONS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, INTERVIEW_QUESTIONS_SUBPATHS).hasRole("ADMIN")
                        // DELETE is ADMIN for a global question and owner-STUDENT for an
                        // AI-generated one; that distinction is a row-level question no URL
                        // pattern can express.
                        .requestMatchers(HttpMethod.DELETE, INTERVIEW_QUESTIONS_SUBPATHS).authenticated()
                        .requestMatchers(HttpMethod.GET, INTERVIEW_QUESTIONS, INTERVIEW_QUESTIONS_SUBPATHS)
                                .authenticated()
                        .requestMatchers(INTERVIEWS, INTERVIEWS_SUBPATHS).authenticated()
                        .requestMatchers(RESUMES, RESUMES_SUBPATHS).authenticated()
                        .requestMatchers(WEBSOCKET).permitAll()
                        .requestMatchers(NOTIFICATIONS, NOTIFICATIONS_SUBPATHS).authenticated()
                        .requestMatchers(HttpMethod.GET, ANNOUNCEMENTS_MANAGE).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, ANNOUNCEMENTS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, ANNOUNCEMENTS_SUBPATHS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, ANNOUNCEMENTS_SUBPATHS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, ANNOUNCEMENTS, ANNOUNCEMENTS_SUBPATHS).authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Allows the Vite dev server to call the API with a Bearer token.
     *
     * <p>Env-driven rather than hardcoded, because §71 requires production to restrict
     * the allowlist to the deployed frontend origin — that must be a config change, not
     * a code change. The default covers both Vite dev ports: Vite silently falls forward
     * to 5174 when 5173 is already taken by another project, and a missing origin
     * surfaces as a CORS error that looks like a broken backend rather than a port
     * collision.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
