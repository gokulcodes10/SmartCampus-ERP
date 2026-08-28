package smartcampus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
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
 * Exercises the PROJECT_PLAN.md Phase 2 checkpoint end to end through the real
 * {@code SecurityConfig} filter chain (security filters are NOT disabled here, unlike
 * {@code PasswordResetFlowIntegrationTest}): "three real users (student, faculty,
 * admin) log in and land on their own dashboards. Tests cover duplicate email,
 * invalid password, expired/tampered JWT, and role denial."
 *
 * <p><strong>Role denial</strong> is asserted against a real production endpoint:
 * {@code POST /api/users}, the admin-only account-provisioning route that clarification
 * G1 requires (self-registration can only ever create a {@code STUDENT}, so
 * {@code FACULTY} and {@code ADMIN} accounts must come from an admin). Its
 * {@code hasRole("ADMIN")} rule lives in {@code SecurityConfig}, so a non-admin is
 * rejected by the filter chain before any controller code runs. All three directions are
 * covered — student denied, faculty denied, admin allowed — because a 403 alone would
 * also be produced by an endpoint that is simply broken for everyone.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationCheckpointTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${smartcampus.jwt.secret}")
    private String jwtSecret;

    private String unique(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }

    private User persistUser(String email, String rawPassword, Role role) {
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .fullName("Checkpoint " + role.name())
                .role(role)
                .build();
        return userRepository.save(user);
    }

    // ------------------------------------------------------------------
    // Three real users log in and /me resolves the right identity + role
    // ------------------------------------------------------------------

    @Test
    void threeRealUsers_registerOrProvision_thenLoginAndResolveTheirOwnIdentity() throws Exception {
        String studentEmail = unique("student");
        // STUDENT via the real self-registration endpoint.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + studentEmail
                                + "\",\"password\":\"StudentPass1!\",\"fullName\":\"Student One\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.password").doesNotExist());

        // FACULTY and ADMIN are admin-provisioned (G1) - simulated here the way the
        // app actually supports it: a row inserted directly with a real BCrypt hash,
        // exactly as this verification pass did against the live database over HTTP.
        String facultyEmail = unique("faculty");
        String adminEmail = unique("admin");
        persistUser(facultyEmail, "FacultyPass1!", Role.FACULTY);
        persistUser(adminEmail, "AdminPass1!", Role.ADMIN);

        assertLoginAndMeResolveCorrectly(studentEmail, "StudentPass1!", "STUDENT");
        assertLoginAndMeResolveCorrectly(facultyEmail, "FacultyPass1!", "FACULTY");
        assertLoginAndMeResolveCorrectly(adminEmail, "AdminPass1!", "ADMIN");
    }

    private void assertLoginAndMeResolveCorrectly(String email, String password, String role)
            throws Exception {
        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value(role))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = loginBody.replaceAll("(?s).*\"token\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value(role))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    // ------------------------------------------------------------------
    // Duplicate email
    // ------------------------------------------------------------------

    @Test
    void duplicateEmailRegistration_returns409WithCleanEnvelope_notA500() throws Exception {
        String email = unique("dup");
        String body = "{\"email\":\"" + email
                + "\",\"password\":\"SomePass1!\",\"fullName\":\"Dup User\"}";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/auth/register"));
    }

    @Test
    void registration_ignoresClientSuppliedRole_alwaysCreatesStudent() throws Exception {
        String email = unique("roleinject");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email
                                + "\",\"password\":\"SomePass1!\",\"fullName\":\"Sneaky\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    // ------------------------------------------------------------------
    // Invalid password / non-enumeration
    // ------------------------------------------------------------------

    @Test
    void wrongPassword_andNonexistentEmail_returnIdenticalNonEnumeratingResponses() throws Exception {
        String email = unique("wrongpass");
        persistUser(email, "CorrectPass1!", Role.STUDENT);

        String wrongPasswordBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"WrongPass1!\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String nonexistentEmailBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + unique("nobody")
                                + "\",\"password\":\"WhateverPass1!\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String wrongMessage = wrongPasswordBody.replaceAll("\"timestamp\":\"[^\"]*\",?", "");
        String nonexistentMessage = nonexistentEmailBody.replaceAll("\"timestamp\":\"[^\"]*\",?", "");
        assertThat(wrongMessage).isEqualTo(nonexistentMessage);
    }

    // ------------------------------------------------------------------
    // Tampered / expired JWT
    // ------------------------------------------------------------------

    @Test
    void tamperedJwt_isRejectedWith401_notA500() throws Exception {
        String email = unique("tamper");
        persistUser(email, "TamperPass1!", Role.STUDENT);

        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"TamperPass1!\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = loginBody.replaceAll("(?s).*\"token\":\"([^\"]+)\".*", "$1");

        // Flip the last character of the signature segment.
        int lastDot = token.lastIndexOf('.');
        String headerPayload = token.substring(0, lastDot);
        String signature = token.substring(lastDot + 1);
        char lastChar = signature.charAt(signature.length() - 1);
        char replacement = (lastChar == 'A') ? 'B' : 'A';
        String tamperedSignature = signature.substring(0, signature.length() - 1) + replacement;
        String tamperedToken = headerPayload + "." + tamperedSignature;

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void expiredJwt_validSignature_isRejectedWith401() throws Exception {
        String email = unique("expired");
        persistUser(email, "ExpiredPass1!", Role.STUDENT);

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant issuedAt = Instant.now().minusSeconds(120);
        Instant expiry = issuedAt.plusSeconds(1); // expired long before this request
        String expiredToken = Jwts.builder()
                .subject(email)
                .claim("role", "STUDENT")
                .claim("fullName", "Expired Test")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiry))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void missingToken_isRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // ------------------------------------------------------------------
    // Role denial (see class javadoc for why this uses a test-only endpoint)
    // ------------------------------------------------------------------

    @Test
    void roleDenial_studentTokenAgainstAdminOnlyEndpoint_is403() throws Exception {
        String email = unique("roledenial");
        persistUser(email, "RoleDenialPass1!", Role.STUDENT);

        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"RoleDenialPass1!\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = loginBody.replaceAll("(?s).*\"token\":\"([^\"]+)\".*", "$1");

        // The real ADMIN-only production endpoint: admin account provisioning (G1).
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"escalation@smartcampus.local\","
                                + "\"password\":\"Escalation1!\","
                                + "\"fullName\":\"Privilege Escalation\","
                                + "\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        // ...and the account the denied request tried to create must not exist.
        assertThat(userRepository.existsByEmail("escalation@smartcampus.local")).isFalse();
    }

    /**
     * The same ADMIN-only route, reached with a {@code FACULTY} token. Staff are not
     * admins: only an {@code ADMIN} may provision accounts (clarification G1), so this
     * guards against the role check being written as a mere "is not a student" test.
     */
    @Test
    void roleDenial_facultyTokenAgainstAdminOnlyEndpoint_is403() throws Exception {
        String email = unique("facultydenial");
        persistUser(email, "FacultyDenialPass1!", Role.FACULTY);

        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"FacultyDenialPass1!\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = loginBody.replaceAll("(?s).*\"token\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"faculty.escalation@smartcampus.local\","
                                + "\"password\":\"Escalation1!\","
                                + "\"fullName\":\"Faculty Escalation\","
                                + "\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertThat(userRepository.existsByEmail("faculty.escalation@smartcampus.local")).isFalse();
    }

    /** An {@code ADMIN} token against the same route succeeds — proving the 403s above
     * come from the role rule and not from the endpoint being broken for everyone. */
    @Test
    void adminToken_againstAdminOnlyEndpoint_provisionsAccount() throws Exception {
        String email = unique("provisioner");
        persistUser(email, "ProvisionPass1!", Role.ADMIN);

        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"ProvisionPass1!\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = loginBody.replaceAll("(?s).*\"token\":\"([^\"]+)\".*", "$1");

        String provisioned = unique("newfaculty");
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + provisioned + "\","
                                + "\"password\":\"NewFaculty1!\","
                                + "\"fullName\":\"New Faculty\","
                                + "\"role\":\"FACULTY\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("FACULTY"))
                .andExpect(jsonPath("$.password").doesNotExist());

        assertThat(userRepository.existsByEmail(provisioned)).isTrue();
    }
}
