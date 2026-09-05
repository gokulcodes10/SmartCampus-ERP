package smartcampus.security61;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/**
 * §61 item 11 — login error handling.
 *
 * <p>Wrong password against a real account and login against an email that has never
 * been registered must be byte-for-byte identical apart from the timestamp: same HTTP
 * status, same body shape, no "user not found", no stack trace, nothing an attacker
 * could use to enumerate which emails have accounts. This is the same account-
 * enumeration property {@code AuthenticationCheckpointTest} proves for Phase 2; this
 * class re-derives it independently (a different agent, a different suite) and adds
 * the explicit stack-trace/leak checks the §61 checklist calls out by name.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class LoginErrorHandlingTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private String uniqueEmail(String prefix) {
        return prefix + "-" + COUNTER.incrementAndGet() + "-" + System.nanoTime() + "@example.com";
    }

    @Test
    void wrongPassword_andUnknownEmail_produceByteIdenticalResponses_moduloTimestamp() throws Exception {
        String email = uniqueEmail("login-err");
        userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode("CorrectPass1!"))
                .fullName("Login Error Check")
                .role(Role.STUDENT)
                .build());

        String wrongPasswordBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"WrongPass1!\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String unknownEmailBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + uniqueEmail("login-err-nobody")
                                + "\",\"password\":\"WhateverPass1!\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String normalizedWrong = wrongPasswordBody.replaceAll("\"timestamp\":\"[^\"]*\",?", "");
        String normalizedUnknown = unknownEmailBody.replaceAll("\"timestamp\":\"[^\"]*\",?", "");
        assertThat(normalizedWrong).isEqualTo(normalizedUnknown);

        for (String body : new String[] {wrongPasswordBody, unknownEmailBody}) {
            String lower = body.toLowerCase();
            assertThat(lower).doesNotContain("user not found");
            assertThat(lower).doesNotContain("no such user");
            assertThat(lower).doesNotContain("does not exist");
            assertThat(lower).doesNotContain("exception");
            assertThat(lower).doesNotContain(".java:");
            assertThat(lower).doesNotContain("stacktrace");
            assertThat(lower).doesNotContain("at smartcampus.");
        }
    }

    @Test
    void wrongPassword_neverReturns500_alwaysAClean401Envelope() throws Exception {
        String email = uniqueEmail("login-err-500check");
        userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode("CorrectPass1!"))
                .fullName("Login Error Check")
                .role(Role.STUDENT)
                .build());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"WrongPass1!\"}"))
                .andExpect(status().isUnauthorized());
    }
}
