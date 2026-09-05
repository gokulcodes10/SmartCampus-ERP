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
import smartcampus.entity.User;
import smartcampus.repository.UserRepository;

/**
 * §61 item 1 — BCrypt password hashing.
 *
 * <p>Registers a real user through the production {@code POST /api/auth/register}
 * endpoint (not a repository shortcut), then reads the stored row straight back out of
 * {@link UserRepository} to prove — over the real persistence path, not by reading
 * {@code PasswordEncoderConfig} and assuming — that what actually lands in the
 * database is a BCrypt hash and never the plaintext password.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class BcryptHashingTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private String unique(String prefix) {
        return prefix + "-" + COUNTER.incrementAndGet() + "-" + System.nanoTime() + "@example.com";
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password
                                + "\",\"fullName\":\"BCrypt Check\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void registeredPassword_isStoredAsABCryptHash_neverThePlaintext() throws Exception {
        String email = unique("bcrypt");
        String rawPassword = "PlainTextPass1!";

        register(email, rawPassword);

        User stored = userRepository.findByEmail(email).orElseThrow();
        String storedHash = stored.getPassword();

        // BCrypt's own encoded-hash format: $2a$ / $2b$ / $2y$ followed by a cost factor.
        assertThat(storedHash).startsWith("$2");
        assertThat(storedHash).isNotEqualTo(rawPassword);
        assertThat(storedHash).doesNotContain(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, storedHash)).isTrue();
        assertThat(passwordEncoder.matches("SomeOtherPass1!", storedHash)).isFalse();
    }

    @Test
    void twoAccountsWithTheIdenticalPassword_produceDifferentHashes_provingItIsSalted() throws Exception {
        String sharedPassword = "SharedPlainPass1!";
        String emailA = unique("salt-a");
        String emailB = unique("salt-b");

        register(emailA, sharedPassword);
        register(emailB, sharedPassword);

        String hashA = userRepository.findByEmail(emailA).orElseThrow().getPassword();
        String hashB = userRepository.findByEmail(emailB).orElseThrow().getPassword();

        assertThat(hashA).isNotEqualTo(hashB);
        // Both still verify against the same plaintext - different salts, same secret.
        assertThat(passwordEncoder.matches(sharedPassword, hashA)).isTrue();
        assertThat(passwordEncoder.matches(sharedPassword, hashB)).isTrue();
    }
}
