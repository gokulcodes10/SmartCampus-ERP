package smartcampus.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import smartcampus.TestcontainersConfiguration;
import smartcampus.entity.Role;
import smartcampus.entity.User;

/**
 * Verifies {@link AdminBootstrapRunner} — the production-safe bootstrap mechanism —
 * against real Testcontainers MySQL. Runs on the DEFAULT profile (no {@code seed}), so
 * {@link DevDataSeeder} never fires here and cannot interfere with these counts.
 *
 * <p>The real, Spring-managed {@code AdminBootstrapRunner} bean also runs automatically
 * at this context's own startup (it is unconditional by design), with blank
 * {@code BOOTSTRAP_ADMIN_EMAIL}/{@code BOOTSTRAP_ADMIN_PASSWORD} (nothing in this test
 * environment sets them) — that is itself a live proof of the "blank env is a no-op"
 * behaviour, asserted below before any test-constructed runner touches the database.
 * The rest of this class uses {@link AdminBootstrapRunner#forTesting} to drive every
 * other branch directly, without needing a separate Spring context per scenario.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AdminBootstrapRunnerTest {

    @Autowired private AdminAccountRepository adminAccountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void blankEnvironmentIsInert() {
        // The real bean already ran during context startup with blank env (nothing in
        // this test process sets BOOTSTRAP_ADMIN_EMAIL/PASSWORD) — confirm it created
        // nothing, then confirm explicitly via forTesting with blank values too.
        assertThat(adminAccountRepository.existsByRole(Role.ADMIN)).isFalse();

        AdminBootstrapRunner runner = AdminBootstrapRunner.forTesting(adminAccountRepository, passwordEncoder, "", "");
        runner.run(null);
        assertThat(adminAccountRepository.existsByRole(Role.ADMIN)).isFalse();

        AdminBootstrapRunner blankPasswordOnly =
                AdminBootstrapRunner.forTesting(
                        adminAccountRepository, passwordEncoder, "someone@example.com", "   ");
        blankPasswordOnly.run(null);
        assertThat(adminAccountRepository.existsByRole(Role.ADMIN)).isFalse();
    }

    @Test
    void shortPasswordFailsStartupLoudlyAndCreatesNoAdmin() {
        assertThat(adminAccountRepository.existsByRole(Role.ADMIN)).isFalse();

        // 11 characters — one under the 12-character floor.
        String tooShortPassword = "Short12345!";
        assertThat(tooShortPassword).hasSize(11);

        AdminBootstrapRunner runner =
                AdminBootstrapRunner.forTesting(
                        adminAccountRepository, passwordEncoder, "weak-admin@example.com", tooShortPassword);

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("12");

        assertThat(adminAccountRepository.existsByRole(Role.ADMIN)).isFalse();
    }

    @Test
    void validCredentialsCreateExactlyOneBcryptHashedAdmin() {
        String email = "bootstrap-admin@example.com";
        String password = "BootstrapPass1234"; // 17 chars, clears the 12-char floor

        AdminBootstrapRunner runner =
                AdminBootstrapRunner.forTesting(adminAccountRepository, passwordEncoder, email, password);
        runner.run(null);

        assertThat(adminAccountRepository.existsByRole(Role.ADMIN)).isTrue();
        long adminCount =
                adminAccountRepository.findAll().stream().filter(u -> u.getRole() == Role.ADMIN).count();
        assertThat(adminCount).isEqualTo(1);

        User created =
                adminAccountRepository.findAll().stream()
                        .filter(u -> u.getRole() == Role.ADMIN)
                        .findFirst()
                        .orElseThrow();
        assertThat(created.getEmail()).isEqualTo(email);
        // Never plaintext: the stored hash must not equal the raw password, and must
        // verify correctly through the same PasswordEncoder every login path uses.
        assertThat(created.getPassword()).isNotEqualTo(password);
        assertThat(passwordEncoder.matches(password, created.getPassword())).isTrue();

        // Re-running with DIFFERENT credentials, now that an admin exists, must be a
        // clean no-op — it must never create a second admin.
        AdminBootstrapRunner second =
                AdminBootstrapRunner.forTesting(
                        adminAccountRepository, passwordEncoder, "second-admin@example.com", "AnotherPass1234");
        second.run(null);

        long adminCountAfter =
                adminAccountRepository.findAll().stream().filter(u -> u.getRole() == Role.ADMIN).count();
        assertThat(adminCountAfter).isEqualTo(1);
        assertThat(userExists("second-admin@example.com")).isFalse();
    }

    private boolean userExists(String email) {
        return adminAccountRepository.findAll().stream().anyMatch(u -> u.getEmail().equals(email));
    }
}
