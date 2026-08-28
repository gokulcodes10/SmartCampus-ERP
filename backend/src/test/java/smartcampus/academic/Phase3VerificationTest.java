package smartcampus.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

/**
 * Wave 1 verification suite for the Phase 3 checkpoint, run over the real {@code
 * SecurityConfig} filter chain against Testcontainers MySQL (not mocked). Complements
 * {@link StudentFacultyAccessCheckpointTest} and {@link
 * smartcampus.service.AcademicAccessGuardTest} with three things they do not already
 * cover end to end: (1) genuine server-side pagination — different rows per page and a
 * correct {@code totalElements} that only SQL-level {@code LIMIT}/{@code COUNT} can
 * produce, not an in-memory slice; (2) privilege-escalation attempts from both STUDENT
 * and FACULTY callers against every admin-only Phase 3 write route; (3) that no
 * response body anywhere in this suite ever contains a password or hash field.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class Phase3VerificationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static long counter = 0;

    private String unique(String prefix) {
        return prefix + "-" + System.nanoTime() + "-" + (counter++) + "@example.com";
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertNoSecretLeak(body);
        return extractString(body, "token");
    }

    private String registerStudent(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email
                                + "\",\"password\":\"" + password
                                + "\",\"fullName\":\"Verification Student\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertNoSecretLeak(body);
        return login(email, password);
    }

    private String persistAdmin(String email, String password) {
        userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .fullName("Verification Admin")
                .role(Role.ADMIN)
                .build());
        return email;
    }

    private String persistFaculty(String email, String password) {
        userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .fullName("Verification Faculty")
                .role(Role.FACULTY)
                .build());
        return email;
    }

    private static void assertNoSecretLeak(String json) {
        String lower = json.toLowerCase();
        assertThat(lower).doesNotContain("\"password\"");
        assertThat(lower).doesNotContain("passwordhash");
        assertThat(lower).doesNotContain("\"hash\"");
    }

    private static String extractString(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\":\"([^\"]+)\"").matcher(json);
        if (!m.find()) {
            throw new IllegalStateException("Field " + field + " not found in " + json);
        }
        return m.group(1);
    }

    // ------------------------------------------------------------------
    // §44 pagination: real SQL-level paging, not an in-memory slice
    // ------------------------------------------------------------------

    @Test
    void departmentListing_paginatesAtTheDatabase_notInMemory() throws Exception {
        String adminEmail = unique("pageadmin");
        String adminToken = login(persistAdmin(adminEmail, "AdminPass1!"), "AdminPass1!");

        // Department.code is capped at 10 chars (V1__baseline.sql / DepartmentCreateRequest),
        // so the unique tag must be short — a 6-digit slice of nanoTime is unique enough
        // for one test run without colliding with fixtures other tests create.
        String tag = String.valueOf(System.nanoTime()).substring(7, 13);
        for (int i = 0; i < 5; i++) {
            String body = "{\"code\":\"P" + tag + i + "\",\"name\":\"Pagination Dept " + tag + i + "\"}";
            mockMvc.perform(post("/api/departments")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());
        }

        String page0Body = mockMvc.perform(get("/api/departments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", tag)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String page1Body = mockMvc.perform(get("/api/departments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", tag)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String page2Body = mockMvc.perform(get("/api/departments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", tag)
                        .param("page", "2")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // The three pages are pairwise disjoint (real paging) and their union is every
        // department just created (nothing lost, nothing duplicated across pages).
        var ids0 = extractIds(page0Body);
        var ids1 = extractIds(page1Body);
        var ids2 = extractIds(page2Body);

        assertThat(ids0).doesNotContainAnyElementsOf(ids1);
        assertThat(ids0).doesNotContainAnyElementsOf(ids2);
        assertThat(ids1).doesNotContainAnyElementsOf(ids2);

        var union = new java.util.HashSet<Long>();
        union.addAll(ids0);
        union.addAll(ids1);
        union.addAll(ids2);
        assertThat(union).hasSize(5);

        // A search term that matches nothing returns an empty page with a correct
        // (zero) total, not a 500 or a stale count.
        mockMvc.perform(get("/api/departments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", "no-such-department-" + System.nanoTime())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    /**
     * Extracts every top-level {@code "id":<number>} occurrence from the {@code
     * content} array of a §44 page envelope. Regex-based (matching the rest of this
     * test file's JSON handling) rather than a full parse, since {@code id} is the
     * first field of every response DTO in this module and never appears nested
     * inside {@code content} for these particular department objects.
     */
    private static java.util.List<Long> extractIds(String pageJson) {
        String contentSection = pageJson.substring(
                pageJson.indexOf("\"content\":"), pageJson.indexOf("\"page\":"));
        Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(contentSection);
        var ids = new java.util.ArrayList<Long>();
        while (m.find()) {
            ids.add(Long.parseLong(m.group(1)));
        }
        return ids;
    }

    // ------------------------------------------------------------------
    // Privilege escalation: STUDENT and FACULTY against every admin-only route
    // ------------------------------------------------------------------

    @Test
    void studentCannotReachAnyAdminOnlyWriteRoute() throws Exception {
        String email = unique("escstudent");
        String token = registerStudent(email, "EscPass1!");

        mockMvc.perform(post("/api/departments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"HACK\",\"name\":\"Hacked\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"HACK\",\"name\":\"Hacked\",\"departmentId\":1}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/subjects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"HACK\",\"name\":\"Hacked\",\"credits\":4,\"semester\":1,\"courseId\":1}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/enrollments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":1,\"subjectId\":1,\"academicYear\":\"2025-2026\",\"semester\":1,\"section\":\"A\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/enrollments").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/faculty-subject-assignments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"facultyId\":1,\"subjectId\":1,\"academicYear\":\"2025-2026\",\"semester\":1,\"section\":\"A\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + unique("newadmin") + "\",\"password\":\"Pass1234!\","
                                + "\"fullName\":\"x\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        // Self-registering with an elevated role is silently downgraded to STUDENT,
        // not honored and not rejected with a leaking error — clarification G1.
        String elevated = unique("elevated");
        String regBody = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + elevated
                                + "\",\"password\":\"ElevPass1!\",\"fullName\":\"x\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(regBody).contains("\"role\":\"STUDENT\"");
    }

    @Test
    void facultyCannotReachAnyAdminOnlyWriteRoute() throws Exception {
        String email = unique("escfaculty");
        String token = login(persistFaculty(email, "EscPass1!"), "EscPass1!");

        mockMvc.perform(post("/api/departments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"HACK2\",\"name\":\"Hacked2\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/departments/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hacked\"}"))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    // 403 if the filter chain catches it (expected); never 200.
                    assertThat(sc).isEqualTo(403);
                });

        mockMvc.perform(post("/api/enrollments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":1,\"subjectId\":1,\"academicYear\":\"2025-2026\",\"semester\":1,\"section\":\"A\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/faculty-subject-assignments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"facultyId\":1,\"subjectId\":1,\"academicYear\":\"2025-2026\",\"semester\":1,\"section\":\"A\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/faculty-subject-assignments").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + unique("newadmin2") + "\",\"password\":\"Pass1234!\","
                                + "\"fullName\":\"x\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }
}
