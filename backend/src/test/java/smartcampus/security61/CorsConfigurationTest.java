package smartcampus.security61;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import smartcampus.TestcontainersConfiguration;

/**
 * §61 item 7 — CORS configuration.
 *
 * <p>Reads the real allowlist from {@code smartcampus.cors.allowed-origins} (never
 * hardcodes an origin — the property is env-driven and the default itself is two
 * origins, see {@code SecurityConfig} javadoc) and drives an actual CORS preflight
 * (an {@code OPTIONS} request carrying {@code Origin} + {@code
 * Access-Control-Request-Method}) through the real Spring Security filter chain,
 * exactly the way a browser would. An allowed origin gets the allow-origin header
 * echoed back; {@code https://evil.example} gets no such header at all; and
 * credentials are confirmed OFF, matching {@code SecurityConfig.corsConfigurationSource()}
 * (the token travels in the {@code Authorization} header, not a cookie, so credentialed
 * CORS is correctly never turned on).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CorsConfigurationTest {

    @Autowired private MockMvc mockMvc;

    @Value("${smartcampus.cors.allowed-origins:http://localhost:5173,http://localhost:5174}")
    private List<String> allowedOrigins;

    @Test
    void preflightFromAnAllowedOrigin_getsTheAllowOriginHeaderEchoedBack() throws Exception {
        assertThat(allowedOrigins).isNotEmpty();
        String allowedOrigin = allowedOrigins.get(0);

        MvcResult result = mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, allowedOrigin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andReturn();

        String echoedOrigin = result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertThat(echoedOrigin).isEqualTo(allowedOrigin);
    }

    @Test
    void preflightFromAnUnlistedOrigin_getsNoAllowOriginHeaderAtAll() throws Exception {
        String evilOrigin = "https://evil.example";
        assertThat(allowedOrigins).doesNotContain(evilOrigin);

        MvcResult result = mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, evilOrigin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    @Test
    void corsResponse_neverAllowsCredentials() throws Exception {
        String allowedOrigin = allowedOrigins.get(0);

        MvcResult result = mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, allowedOrigin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
    }
}
