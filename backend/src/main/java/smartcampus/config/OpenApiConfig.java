package smartcampus.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * §63 API documentation: OpenAPI/Swagger UI, JWT-authorized live testing, and the 13
 * documentation groups §63 names (Authentication, Student APIs, Faculty APIs, Admin
 * APIs, Attendance, Marks, Analytics, AI, Coding, Placement, Resume, Interview,
 * Notifications).
 *
 * <p>Reachability is controlled entirely by {@code SecurityConfig} (permitAll on
 * {@code /v3/api-docs/**} and {@code /swagger-ui/**}, placed before {@code
 * anyRequest().authenticated()}) and by {@code springdoc.api-docs.enabled} /
 * {@code springdoc.swagger-ui.enabled} in {@code application.properties}, both wired
 * to {@code SWAGGER_ENABLED} so an operator can turn the docs off in production
 * without a code change.
 *
 * <p>The {@code bearerAuth} security scheme below is what makes the Swagger UI
 * "Authorize" button appear and injects {@code Authorization: Bearer <token>} into
 * every Try-it-out call — registered as a global {@link SecurityRequirement} so every
 * documented operation carries it, not just the ones that opt in individually.
 *
 * <p>Group membership is <strong>path-based</strong> ({@link
 * GroupedOpenApi#pathsToMatch(String...)}), deliberately, rather than requiring a
 * class-level {@code @Tag} on all 33 controllers: one file of churn instead of 33.
 * The path patterns below were read directly off the real {@code @RequestMapping}
 * values in {@code smartcampus.controller} (see that package for the source of
 * truth) — every controller's base path appears in at least one group here, which
 * {@code smartcampus.apidocs.OpenApiDocumentationTest} asserts by taking the union of
 * every group's generated {@code /v3/api-docs/{group}} document and comparing it
 * against the full, ungrouped {@code /v3/api-docs} path set, so a future controller
 * added without a matching group pattern fails that test rather than shipping
 * undocumented.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI smartCampusOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartCampus ERP API")
                        .version("v1")
                        .description(
                                "College ERP backend: academic records, attendance, marks, analytics, "
                                        + "AI study assistance, coding practice, placements, resumes, mock "
                                        + "interviews and real-time notifications. Authenticate with "
                                        + "POST /api/auth/login, then click Authorize and paste the JWT "
                                        + "(without the 'Bearer ' prefix — Swagger UI adds it)."))
                .components(new Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    public GroupedOpenApi authenticationGroup() {
        return GroupedOpenApi.builder()
                .group("Authentication")
                .pathsToMatch("/api/auth/**")
                .build();
    }

    @Bean
    public GroupedOpenApi studentApisGroup() {
        return GroupedOpenApi.builder()
                .group("Student APIs")
                .pathsToMatch("/api/students/**")
                .build();
    }

    @Bean
    public GroupedOpenApi facultyApisGroup() {
        return GroupedOpenApi.builder()
                .group("Faculty APIs")
                .pathsToMatch("/api/faculty/**", "/api/teaching/**")
                .build();
    }

    /**
     * Reference-data and account-provisioning routes that are ADMIN-authored end to
     * end: users, departments, courses, subjects, enrollments, faculty-subject
     * assignments and grade bands.
     */
    @Bean
    public GroupedOpenApi adminApisGroup() {
        return GroupedOpenApi.builder()
                .group("Admin APIs")
                .pathsToMatch(
                        "/api/users/**",
                        "/api/departments/**",
                        "/api/courses/**",
                        "/api/subjects/**",
                        "/api/enrollments/**",
                        "/api/faculty-subject-assignments/**",
                        "/api/grade-bands/**")
                .build();
    }

    @Bean
    public GroupedOpenApi attendanceGroup() {
        return GroupedOpenApi.builder()
                .group("Attendance")
                .pathsToMatch("/api/attendance/**")
                .build();
    }

    /** Exams are grouped with Marks: every {@code Mark} row is scored against an {@code Exam}. */
    @Bean
    public GroupedOpenApi marksGroup() {
        return GroupedOpenApi.builder()
                .group("Marks")
                .pathsToMatch("/api/marks/**", "/api/exams/**")
                .build();
    }

    /** Performance bands are analytics reference data (the thresholds analytics classifies against). */
    @Bean
    public GroupedOpenApi analyticsGroup() {
        return GroupedOpenApi.builder()
                .group("Analytics")
                .pathsToMatch("/api/analytics/**", "/api/performance-bands/**")
                .build();
    }

    @Bean
    public GroupedOpenApi aiGroup() {
        return GroupedOpenApi.builder()
                .group("AI")
                .pathsToMatch("/api/ai/**")
                .build();
    }

    @Bean
    public GroupedOpenApi codingGroup() {
        return GroupedOpenApi.builder()
                .group("Coding")
                .pathsToMatch(
                        "/api/coding/**", "/api/problems/**", "/api/contests/**", "/api/leaderboard/**")
                .build();
    }

    @Bean
    public GroupedOpenApi placementGroup() {
        return GroupedOpenApi.builder()
                .group("Placement")
                .pathsToMatch(
                        "/api/companies/**", "/api/jobs/**", "/api/applications/**", "/api/placement/**")
                .build();
    }

    @Bean
    public GroupedOpenApi resumeGroup() {
        return GroupedOpenApi.builder()
                .group("Resume")
                .pathsToMatch("/api/resumes/**")
                .build();
    }

    @Bean
    public GroupedOpenApi interviewGroup() {
        return GroupedOpenApi.builder()
                .group("Interview")
                .pathsToMatch("/api/interviews/**", "/api/interview-questions/**")
                .build();
    }

    @Bean
    public GroupedOpenApi notificationsGroup() {
        return GroupedOpenApi.builder()
                .group("Notifications")
                .pathsToMatch("/api/notifications/**", "/api/announcements/**")
                .build();
    }
}
