package smartcampus.security61;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
 * §61 item 2 — JWT authentication.
 *
 * <p>Six sub-requirements, each executed against the real {@code SecurityConfig}
 * filter chain (not mocked): a valid token is accepted; a tampered signature is 401; an
 * expired-but-validly-signed token is 401; a well-signed token naming a subject that
 * does not exist is 401; {@code Authorization: Basic} is rejected (httpBasic is
 * disabled — see {@code SecurityConfig} javadoc — so a Basic header simply never
 * authenticates); a missing header is 401. Every rejection is asserted to carry the
 * §47 envelope (a real {@code status}/{@code error} body) and to be 401, never a raw
 * 403 or an unhandled 500.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class JwtAuthenticationSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${smartcampus.jwt.secret}")
    private String jwtSecret;

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private String unique(String prefix) {
        return prefix + "-" + COUNTER.incrementAndGet() + "-" + System.nanoTime() + "@example.com";
    }

    private String persistUserAndLogin(String password) throws Exception {
        String email = unique("jwt");
        userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .fullName("JWT Check")
                .role(Role.STUDENT)
                .build());
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.replaceAll("(?s).*\"token\":\"([^\"]+)\".*", "$1");
    }

    // ------------------------------------------------------------------
    // 1) A valid token is accepted
    // ------------------------------------------------------------------

    @Test
    void validToken_isAccepted() throws Exception {
        String token = persistUserAndLogin("ValidTokenPass1!");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // 2) Tampered signature -> 401, never 500
    // ------------------------------------------------------------------

    @Test
    void tamperedSignature_isRejectedWith401_carryingTheEnvelope() throws Exception {
        String token = persistUserAndLogin("TamperPass1!");

        int lastDot = token.lastIndexOf('.');
        String headerPayload = token.substring(0, lastDot);
        String signature = token.substring(lastDot + 1);
        char lastChar = signature.charAt(signature.length() - 1);
        char replacement = (lastChar == 'A') ? 'B' : 'A';
        String tampered = headerPayload + "." + signature.substring(0, signature.length() - 1) + replacement;

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/auth/me"));
    }

    // ------------------------------------------------------------------
    // 3) Expired but validly-signed token -> 401
    // ------------------------------------------------------------------

    @Test
    void expiredToken_validSignature_isRejectedWith401() throws Exception {
        String email = unique("expired");
        userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode("ExpiredPass1!"))
                .fullName("Expired Check")
                .role(Role.STUDENT)
                .build());

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant issuedAt = Instant.now().minusSeconds(120);
        Instant expiry = issuedAt.plusSeconds(1);
        String expiredToken = Jwts.builder()
                .subject(email)
                .claim("role", "STUDENT")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiry))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // ------------------------------------------------------------------
    // 4) Well-signed token for a subject that does not exist -> 401
    // ------------------------------------------------------------------

    @Test
    void wellSignedToken_forAnUnknownSubject_isRejectedWith401() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant issuedAt = Instant.now();
        String ghostEmail = unique("ghost-does-not-exist");
        String tokenForNobody = Jwts.builder()
                .subject(ghostEmail)
                .claim("role", "STUDENT")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(3600)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokenForNobody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // ------------------------------------------------------------------
    // 5) Authorization: Basic is rejected (httpBasic is disabled in SecurityConfig)
    // ------------------------------------------------------------------

    @Test
    void basicAuthHeader_isRejectedWith401_neverAuthenticated() throws Exception {
        String credentials = Base64.getEncoder().encodeToString("someone@example.com:whatever".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Basic " + credentials))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // ------------------------------------------------------------------
    // 6) Missing header -> 401
    // ------------------------------------------------------------------

    @Test
    void missingAuthorizationHeader_isRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").isNotEmpty());
    }
}
