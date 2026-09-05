package smartcampus.apidocs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import smartcampus.TestcontainersConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * §63 verification: {@code GET /v3/api-docs} is reachable unauthenticated, is valid
 * OpenAPI JSON, carries the {@code bearerAuth} JWT security scheme, and the 13 named
 * documentation groups ({@link #GROUP_NAMES}) between them cover every path the full,
 * ungrouped document exposes — so a controller added later without a matching {@code
 * GroupedOpenApi} pattern in {@code OpenApiConfig} fails this test instead of shipping
 * silently undocumented.
 *
 * <p>Run over the real {@code SecurityConfig} filter chain (security filters are NOT
 * disabled), the same pattern {@code smartcampus.auth.AuthenticationCheckpointTest}
 * and {@code smartcampus.academic.Phase3VerificationTest} use, so "reachable without a
 * token" is actually proven by the permitAll matcher, not assumed.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    /** Must match {@code OpenApiConfig}'s 13 {@code GroupedOpenApi} bean group names exactly. */
    private static final List<String> GROUP_NAMES = List.of(
            "Authentication",
            "Student APIs",
            "Faculty APIs",
            "Admin APIs",
            "Attendance",
            "Marks",
            "Analytics",
            "AI",
            "Coding",
            "Placement",
            "Resume",
            "Interview",
            "Notifications");

    @Autowired private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void apiDocsIsReachableUnauthenticatedAndIsValidJson() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn();

        // "is valid JSON" - parse it; a malformed document throws here rather than
        // being asserted only by content-type.
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(root.get("openapi")).isNotNull();
        assertThat(root.get("paths")).isNotNull();
    }

    @Test
    void bearerAuthSecuritySchemeIsRegisteredCorrectly() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                // Registered as a global requirement, not opt-in per operation.
                .andExpect(jsonPath("$.security[0].bearerAuth").exists());
    }

    @Test
    void everyGroupDocumentIsReachableAndNonEmpty() throws Exception {
        for (String group : GROUP_NAMES) {
            MvcResult result = mockMvc.perform(get("/v3/api-docs/{group}", group))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
            JsonNode paths = root.get("paths");
            assertThat(paths).as("group '%s' has a paths object", group).isNotNull();
            assertThat(paths.propertyNames()).as("group '%s' documents at least one path", group)
                    .isNotEmpty();
        }
    }

    @Test
    void theThirteenGroupsUnionCoversEveryPathTheFullDocumentExposes() throws Exception {
        MvcResult fullResult = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        JsonNode fullPaths = objectMapper.readTree(fullResult.getResponse().getContentAsByteArray()).get("paths");
        Set<String> fullPathSet = new HashSet<>(fullPaths.propertyNames());
        assertThat(fullPathSet).as("the full document must actually document some paths").isNotEmpty();

        Set<String> unionOfGroupPaths = new HashSet<>();
        for (String group : GROUP_NAMES) {
            MvcResult groupResult =
                    mockMvc.perform(get("/v3/api-docs/{group}", group)).andExpect(status().isOk()).andReturn();
            JsonNode groupPaths =
                    objectMapper.readTree(groupResult.getResponse().getContentAsByteArray()).get("paths");
            unionOfGroupPaths.addAll(groupPaths.propertyNames());
        }

        Set<String> undocumentedByAnyGroup = new HashSet<>(fullPathSet);
        undocumentedByAnyGroup.removeAll(unionOfGroupPaths);

        assertThat(undocumentedByAnyGroup)
                .as(
                        "every path in the full /v3/api-docs document must be covered by at least one "
                                + "of the 13 §63 groups - a controller mapped here but not covered means a "
                                + "GroupedOpenApi pathsToMatch pattern in OpenApiConfig needs updating")
                .isEmpty();
    }
}
