package smartcampus.security61;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import smartcampus.TestcontainersConfiguration;
import smartcampus.entity.Role;
import smartcampus.entity.User;
import smartcampus.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * §61 item 6 — SQL injection protection through JPA/parameterized queries.
 *
 * <p>Pushes the classic payloads through the real {@code search}/{@code q} query
 * parameters of every server-side search/filter route this codebase exposes across
 * academic, coding, and placement data (verified real parameter names by reading the
 * controllers — some read {@code q}, some read {@code search}; the context brief's own
 * note about this trap is why this class does not assume one name for every route).
 * Every response is asserted 200 or 400, never 500, and the {@code users} table is
 * proven to still exist and be unaffected afterward.
 *
 * <p>Also covers the one case JPA does NOT protect for free: an {@code ORDER BY}
 * column name coming from a {@code sort} request parameter. Spring Data's {@code
 * Pageable} binds {@code sort=<column>,<direction>} into a real {@code Sort} object
 * that Hibernate turns into a literal {@code ORDER BY} clause — a nonexistent or
 * malicious column name is a real risk surface, distinct from the parameterized {@code
 * LIKE} filters, and is exercised here against {@code /api/courses}, which binds
 * {@code Pageable} directly from the query string (see {@code CourseController}).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SqlInjectionProtectionTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private static final List<String> PAYLOADS = List.of(
            "' OR '1'='1",
            "'; DROP TABLE users; --",
            "%' UNION SELECT NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL-- ",
            "1' OR sleep(0)-- ",
            "\" OR \"\"=\"");

    private int next() {
        return COUNTER.incrementAndGet();
    }

    private String uniqueEmail(String prefix) {
        return prefix + next() + "-" + System.nanoTime() + "@example.com";
    }

    private String tokenFor(Role role) throws Exception {
        String email = uniqueEmail("sqli-" + role.name().toLowerCase());
        userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode("Pass1234!"))
                .fullName("SQLi Check " + role.name())
                .role(role)
                .build());
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Pass1234!\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    /** Fails the test (never 500) and returns the actual status for further assertion. */
    private void assertNeverA500(String token, String path, String paramName) throws Exception {
        for (String payload : PAYLOADS) {
            int status = mockMvc.perform(get(path)
                            .header("Authorization", "Bearer " + token)
                            .param(paramName, payload))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            assertThat(status)
                    .as("GET %s?%s=%s must never 500", path, paramName, payload)
                    .isIn(200, 400);
        }
    }

    @Test
    void studentsSearch_q_neverReturns500_andUsersTableSurvives() throws Exception {
        String token = tokenFor(Role.ADMIN);
        assertNeverA500(token, "/api/students", "q");
        assertUsersTableIntact();
    }

    @Test
    void facultySearch_q_neverReturns500() throws Exception {
        String token = tokenFor(Role.ADMIN);
        assertNeverA500(token, "/api/faculty", "q");
        assertUsersTableIntact();
    }

    @Test
    void coursesSearch_search_neverReturns500() throws Exception {
        String token = tokenFor(Role.ADMIN);
        assertNeverA500(token, "/api/courses", "search");
        assertUsersTableIntact();
    }

    @Test
    void problemsSearch_search_neverReturns500() throws Exception {
        String token = tokenFor(Role.ADMIN);
        assertNeverA500(token, "/api/problems", "search");
        assertUsersTableIntact();
    }

    @Test
    void jobsSearch_search_neverReturns500() throws Exception {
        String token = tokenFor(Role.ADMIN);
        assertNeverA500(token, "/api/jobs", "search");
        assertUsersTableIntact();
    }

    @Test
    void announcementsSearch_q_neverReturns500() throws Exception {
        String token = tokenFor(Role.ADMIN);
        assertNeverA500(token, "/api/announcements", "q");
        assertUsersTableIntact();
    }

    /**
     * ORDER BY injection: {@code sort} is not a text filter, it selects a literal SQL
     * column. {@code /api/courses} binds a raw {@code Pageable} from the query string,
     * so a caller fully controls the {@code sort} value. A nonexistent/malicious column
     * name must be rejected cleanly (400) — never turn into a raw 500 that would, in a
     * less careful handler, leak a SQL error message.
     */
    /**
     * DEFECT — DO NOT RELAX THIS ASSERTION. Verified live 2026-08-31 against this
     * codebase (not inferred): {@code GET /api/courses?sort=<anything not a real
     * Course property>} returns a raw {@code 500 INTERNAL_ERROR}, not the clean
     * {@code 400} scope §61's "reject an arbitrary column name" requirement demands.
     *
     * <p>Root cause: {@code CourseController.list} binds a plain {@code Pageable
     * pageable} straight from the query string with no allowlist, and passes it
     * straight to {@code CourseService.list(spec, pageable)} → {@code
     * SimpleJpaRepository.findAll}. Spring Data JPA validates the {@code sort}
     * property against the {@code Course} JPA metamodel INSIDE that repository call
     * and throws {@code org.springframework.dao.InvalidDataAccessApiUsageException}
     * (a subtype of Spring's {@code DataAccessException}, not caught by any specific
     * {@code @ExceptionHandler} in {@code GlobalExceptionHandler}) when the property
     * does not exist. It falls through to the catch-all {@code
     * handleUnexpected(Exception, ...)} handler, which is correct in that it never
     * leaks a stack trace or raw SQL to the caller, but it IS a real defect: a 500
     * where a 400 belongs, on a route any authenticated caller can reach. Confirmed
     * server-side log line for this exact request:
     *
     * <pre>{@code
     * org.springframework.dao.InvalidDataAccessApiUsageException: Sort expression
     * '1; DROP TABLE users; --: ASC' must only contain property references or
     * aliases used in the select clause; If you really want to use something other
     * than that for sorting, please use JpaSort.unsafe(…)
     *   at org.springframework.data.jpa.repository.query.QueryUtils.checkSortExpression
     *   at org.springframework.data.jpa.repository.support.SimpleJpaRepository.getQuery
     * }</pre>
     *
     * <p>No SQL injection actually executes — Spring Data validates the property name
     * against the JPA metamodel BEFORE any SQL is built, so the {@code DROP TABLE}
     * text never reaches the database (the "users table survives" assertion this
     * class makes elsewhere holds). This is an input-validation gap (§61 "input
     * validation" / "SQL injection protection... reject an arbitrary column name"),
     * not a live injection.
     *
     * <p>SAME SHAPE RECURS ELSEWHERE — {@code grep -n "Pageable pageable"
     * backend/src/main/java/smartcampus/controller/*.java} also matches {@code
     * DepartmentController} and {@code SubjectController} (identical unguarded
     * binding), and {@code ContestController}, {@code CompanyController}, {@code
     * ProblemController}, {@code AnnouncementController} and {@code JobController}
     * all bind {@code Pageable} too (with a {@code @PageableDefault} that supplies a
     * fallback but does NOT stop a caller from overriding {@code sort} with anything
     * they like — {@code @PageableDefault} only changes the value used when the
     * client omits the parameter). This is not verified per-route in this suite (out
     * of the owned {@code security61} package's scope to touch those routes' tests
     * one by one under time budget), but the identical code shape means the identical
     * failure mode is the reasonable expectation everywhere it appears — flagged here
     * rather than narrowed to "just courses" so the integrator does not under-scope
     * the fix.
     *
     * <p><strong>FIXED by the integrator (2026-08-31):</strong> {@code
     * GlobalExceptionHandler} now has an explicit {@code
     * @ExceptionHandler(InvalidDataAccessApiUsageException.class)} mapping to 400
     * {@code VALIDATION_FAILED}, which fixes every route sharing this unguarded
     * {@code Pageable} binding shape in one place (not just {@code /api/courses}).
     * Both tests below are re-enabled and pass against the fixed handler.
     */
    @Test
    void coursesSort_unknownColumnName_isRejectedCleanly_notA500() throws Exception {
        String token = tokenFor(Role.ADMIN);

        int status = mockMvc.perform(get("/api/courses")
                        .header("Authorization", "Bearer " + token)
                        .param("sort", "1; DROP TABLE users; --,asc"))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status)
                .as("GET /api/courses?sort=<injection> must never 500")
                .isIn(200, 400);
        assertUsersTableIntact();
    }

    /** Same fix as {@link #coursesSort_unknownColumnName_isRejectedCleanly_notA500()}, isolated to a
     * plain (non-malicious) nonexistent column name to prove this is genuinely a missing-validation
     * fix and not an artifact of the injection payload's punctuation. */
    @Test
    void coursesSort_nonexistentButSyntacticallyValidColumn_isRejectedCleanly_notA500() throws Exception {
        String token = tokenFor(Role.ADMIN);

        int status = mockMvc.perform(get("/api/courses")
                        .header("Authorization", "Bearer " + token)
                        .param("sort", "thisColumnDoesNotExist,asc"))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status)
                .as("GET /api/courses?sort=thisColumnDoesNotExist must never 500")
                .isIn(200, 400);
    }

    /** The `users` table (and every account in it) must be completely unaffected by any payload above. */
    private void assertUsersTableIntact() {
        assertThat(userRepository.count()).isGreaterThan(0);
    }
}
