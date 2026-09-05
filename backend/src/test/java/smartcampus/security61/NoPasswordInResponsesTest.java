package smartcampus.security61;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import smartcampus.TestcontainersConfiguration;
import smartcampus.entity.Role;
import smartcampus.entity.User;
import smartcampus.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * §61 item 12 — no password in API responses. Two independent layers.
 *
 * <p>(a) A compile-time-shaped check over every {@code *Response} record in {@code
 * smartcampus.dto} (84 classes at time of writing), asserted by reflection rather than
 * eyeballing: no record component is named {@code password}, {@code passwordHash},
 * {@code hash} or {@code secret} (case-insensitive, exact match — not a substring
 * search, since a substring rule would also flag legitimate fields like {@code
 * AuthResponse.token}, which is not a password or a hash and is exactly what a login
 * response must return). This proves the DTO layer as a whole can never leak one,
 * independent of what any particular test happens to call.
 *
 * <p>(b) A live check of the actual JSON five real endpoints return, which is what
 * ultimately matters — a DTO with no password field is only safe if nothing upstream
 * (a generic map, a raw entity, a debug field) reintroduces one at serialization time.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class NoPasswordInResponsesTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final Set<String> FORBIDDEN_COMPONENT_NAMES =
            Set.of("password", "passwordhash", "hash", "secret");

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private String uniqueEmail(String prefix) {
        return prefix + "-" + COUNTER.incrementAndGet() + "-" + System.nanoTime() + "@example.com";
    }

    // ------------------------------------------------------------------
    // (a) Reflection over every *Response DTO
    // ------------------------------------------------------------------

    @Test
    void everyResponseDto_hasNoComponentNamedLikeAPasswordOrHashOrSecret() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:smartcampus/dto/*Response.class");
        assertThat(resources)
                .as("expected to find *Response DTO classes on the classpath under smartcampus.dto")
                .isNotEmpty();

        List<String> violations = new ArrayList<>();
        int classesChecked = 0;

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null || !filename.endsWith(".class")) {
                continue;
            }
            String simpleName = filename.substring(0, filename.length() - ".class".length());
            if (simpleName.contains("$")) {
                continue; // skip synthetic/inner classes, if any
            }
            Class<?> clazz = Class.forName("smartcampus.dto." + simpleName);
            classesChecked++;

            if (!clazz.isRecord()) {
                continue; // every *Response DTO in this codebase is a record; nothing to reflect on otherwise
            }
            for (RecordComponent component : clazz.getRecordComponents()) {
                String lowerName = component.getName().toLowerCase(Locale.ROOT);
                if (FORBIDDEN_COMPONENT_NAMES.contains(lowerName)) {
                    violations.add(clazz.getSimpleName() + "." + component.getName());
                }
            }
        }

        assertThat(classesChecked).isGreaterThanOrEqualTo(80); // sanity: we actually scanned the real package
        assertThat(violations)
                .as("no *Response DTO may carry a field named password/passwordHash/hash/secret")
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // (b) Live JSON check of five real endpoints
    // ------------------------------------------------------------------

    @Test
    void authMeAndLoginResponses_containNoPasswordKey() throws Exception {
        String email = uniqueEmail("nopass-auth");
        String password = "LiveCheckPass1!";

        String registerBody = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password
                                + "\",\"fullName\":\"No Password Check\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertNoPasswordKey(registerBody);

        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertNoPasswordKey(loginBody);

        String token = objectMapper.readTree(loginBody).get("token").asText();
        String meBody = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertNoPasswordKey(meBody);
    }

    @Test
    void studentsAndFacultyListings_containNoPasswordKey() throws Exception {
        String adminEmail = uniqueEmail("nopass-admin");
        userRepository.save(User.builder()
                .email(adminEmail)
                .password(passwordEncoder.encode("AdminPass1!"))
                .fullName("No Password Admin")
                .role(Role.ADMIN)
                .build());
        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + adminEmail + "\",\"password\":\"AdminPass1!\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String adminToken = objectMapper.readTree(loginBody).get("token").asText();

        String studentsBody = mockMvc.perform(get("/api/students")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertNoPasswordKey(studentsBody);

        String facultyBody = mockMvc.perform(get("/api/faculty")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertNoPasswordKey(facultyBody);
    }

    /**
     * {@code GET /api/users} does not exist in this codebase — {@code
     * UserAdminController} exposes only {@code POST /api/users} (account
     * provisioning). This checks the JSON that route DOES return instead, which is the
     * closest live equivalent to the task's "check /api/users" instruction; the
     * discrepancy is called out explicitly in the final report rather than silently
     * substituted without explanation.
     */
    @Test
    void userProvisioningResponse_containsNoPasswordKey() throws Exception {
        String adminEmail = uniqueEmail("nopass-provisioner");
        userRepository.save(User.builder()
                .email(adminEmail)
                .password(passwordEncoder.encode("AdminPass1!"))
                .fullName("No Password Provisioner")
                .role(Role.ADMIN)
                .build());
        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + adminEmail + "\",\"password\":\"AdminPass1!\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String adminToken = objectMapper.readTree(loginBody).get("token").asText();

        String provisionedEmail = uniqueEmail("nopass-provisioned");
        String provisionBody = mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + provisionedEmail + "\",\"password\":\"ProvisionedPass1!\","
                                + "\"fullName\":\"Provisioned User\",\"role\":\"FACULTY\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertNoPasswordKey(provisionBody);
    }

    private static void assertNoPasswordKey(String json) {
        String lower = json.toLowerCase(Locale.ROOT);
        assertThat(lower).doesNotContain("\"password\"");
        assertThat(lower).doesNotContain("\"passwordhash\"");
        assertThat(lower).doesNotContain("\"hash\"");
    }
}
