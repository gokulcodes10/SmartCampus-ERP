package smartcampus.hardening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import smartcampus.TestcontainersConfiguration;

/**
 * §61 "Secure HTTP headers where appropriate" verification, over the real {@code
 * SecurityConfig} filter chain against Testcontainers MySQL — the same pattern {@code
 * smartcampus.auth.AuthenticationCheckpointTest} and {@code
 * smartcampus.academic.Phase3VerificationTest} use.
 *
 * <p>Asserts headers on TWO different response families deliberately, because they
 * must carry DIFFERENT Content-Security-Policy values (see {@code SecurityConfig}'s
 * javadoc on {@code CSP_API} / {@code CSP_SWAGGER}): a real {@code /api/**} JSON
 * response, and the Swagger UI HTML page, which would render blank under the strict
 * API policy.
 *
 * <p>Does not assert an HSTS header anywhere: Spring only ever emits it over HTTPS,
 * and every request in this suite (like every request this application serves today)
 * is plain HTTP, so asserting HSTS present here would either be a no-op or, worse, an
 * assertion that silently stops meaning anything the day someone "fixes" it to pass.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SecurityHeadersTest {

    @Autowired private MockMvc mockMvc;

    private String unique(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }

    private String registerAndLogin() throws Exception {
        String email = unique("headers");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email
                                + "\",\"password\":\"HeadersPass1!\",\"fullName\":\"Headers Tester\"}"))
                .andExpect(status().isCreated());

        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"HeadersPass1!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return loginBody.replaceAll("(?s).*\"token\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void realApiResponseCarriesTheExpectedSecureHeaders() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(get("/api/departments").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().exists("Permissions-Policy"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(result -> assertThat(
                                result.getResponse().getHeader("Content-Security-Policy"))
                        .as("the API CSP must be the strict, script/style-less policy")
                        .contains("default-src 'none'")
                        .contains("frame-ancestors 'none'"));
    }

    @Test
    void swaggerUiStillReturns200HtmlAfterTheCspIsAdded() throws Exception {
        var result = mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Security-Policy"))
                .andReturn();

        String contentType = result.getResponse().getContentType();
        assertThat(contentType).as("swagger-ui/index.html must be served as HTML").contains("text/html");

        String csp = result.getResponse().getHeader("Content-Security-Policy");
        assertThat(csp)
                .as("the Swagger path must NOT get the strict default-src 'none' API policy - that "
                        + "would blank the docs page")
                .doesNotContain("default-src 'none'")
                .contains("default-src 'self'");

        String body = result.getResponse().getContentAsString();
        assertThat(body).as("the actual HTML must be present, not an empty/blocked page").contains("<html");
    }

    @Test
    void unauthenticatedRequestIsRejectedWithTheSameSecureHeaders() throws Exception {
        // No Authorization header at all - JwtAuthenticationEntryPoint produces the
        // 401, but SecurityConfig's header writers must still run on that response.
        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("Content-Security-Policy"));
    }
}
