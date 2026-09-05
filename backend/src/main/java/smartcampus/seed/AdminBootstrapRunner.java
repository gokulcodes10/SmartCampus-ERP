package smartcampus.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import smartcampus.entity.Role;
import smartcampus.entity.User;

/**
 * Production-safe first-admin bootstrap — the real replacement for the manual "register
 * a student, then flip {@code role} to ADMIN by hand in MySQL" step that Phases 2-11
 * lived with (see Phase 2 note in PROJECT_PLAN.md and Addendum 4's "Known gaps").
 *
 * <p>This is deliberately a SEPARATE mechanism from {@link DevDataSeeder}, and it is
 * NOT profile-gated — it runs on every boot, in every environment, including
 * production. It is safe to run unconditionally because it does nothing unless BOTH
 * {@code BOOTSTRAP_ADMIN_EMAIL} and {@code BOOTSTRAP_ADMIN_PASSWORD} are supplied, and
 * even then it never touches an existing ADMIN. Contrast with {@link DevDataSeeder},
 * which installs a whole fake academic dataset and MUST NOT run in production — see
 * that class's javadoc for why a Flyway migration could not do either job (also
 * recorded in PROJECT_PLAN.md's Phase 12 section).
 *
 * <h2>Behaviour</h2>
 *
 * <ul>
 *   <li>{@code BOOTSTRAP_ADMIN_EMAIL} or {@code BOOTSTRAP_ADMIN_PASSWORD} blank (the
 *       default on every environment that does not set them) — logged no-op. This is
 *       the ordinary-boot case and must be inert.
 *   <li>Both set, and any ADMIN already exists — logged no-op. Bootstrapping is a
 *       one-time event; this runner never touches an existing administrator, seeded or
 *       otherwise.
 *   <li>Both set, no ADMIN exists yet, password shorter than 12 characters — startup
 *       fails loudly (an {@link IllegalStateException} propagates out of {@link #run},
 *       which Spring Boot turns into a failed application context). Refusing to install
 *       a weak administrator is more important than a smooth boot.
 *   <li>Both set, no ADMIN exists yet, password long enough — exactly one ADMIN is
 *       created, BCrypt-hashed through the application's real {@link PasswordEncoder}
 *       bean (the same one every login path verifies against).
 * </ul>
 *
 * <p>The password is never logged, echoed, or persisted anywhere except as its BCrypt
 * hash in {@code users.password} — the same column every other account uses, subject to
 * the same "no password ever appears in an API response" rule ({@code User.password} is
 * {@code @JsonIgnore}).
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private static final int MINIMUM_PASSWORD_LENGTH = 12;

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${BOOTSTRAP_ADMIN_EMAIL:}")
    private String bootstrapAdminEmail;

    @Value("${BOOTSTRAP_ADMIN_PASSWORD:}")
    private String bootstrapAdminPassword;

    public AdminBootstrapRunner(
            AdminAccountRepository adminAccountRepository, PasswordEncoder passwordEncoder) {
        this.adminAccountRepository = adminAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = bootstrapAdminEmail == null ? "" : bootstrapAdminEmail.trim();
        String password = bootstrapAdminPassword == null ? "" : bootstrapAdminPassword;

        if (email.isBlank() || password.isBlank()) {
            log.info(
                    "AdminBootstrapRunner: BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD not both set —"
                            + " no-op. Set both environment variables once to provision the first ADMIN"
                            + " account without touching MySQL by hand.");
            return;
        }

        if (adminAccountRepository.existsByRole(Role.ADMIN)) {
            log.info(
                    "AdminBootstrapRunner: an ADMIN account already exists — leaving it untouched"
                            + " (BOOTSTRAP_ADMIN_EMAIL/PASSWORD are only consulted while zero ADMIN"
                            + " accounts exist).");
            return;
        }

        // The existence check above runs BEFORE this validation on purpose: if an admin
        // already exists we must stay a no-op regardless of password strength, since we
        // are not creating anything either way. Only once we are actually about to
        // create the FIRST admin does a weak password become fatal.
        if (password.length() < MINIMUM_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD is shorter than " + MINIMUM_PASSWORD_LENGTH
                            + " characters. Refusing to start rather than install a weak administrator —"
                            + " set a longer BOOTSTRAP_ADMIN_PASSWORD and restart.");
        }

        User admin =
                User.builder()
                        .email(email.toLowerCase())
                        .password(passwordEncoder.encode(password))
                        .fullName("System Administrator")
                        .role(Role.ADMIN)
                        .enabled(true)
                        .build();

        try {
            adminAccountRepository.save(admin);
        } catch (DataIntegrityViolationException ex) {
            // Belt and braces: BOOTSTRAP_ADMIN_EMAIL collided with an existing non-admin
            // account's email (uk_users_email), or a concurrent boot raced this one. Log
            // and continue rather than crash startup over a collision this runner cannot
            // resolve on its own — an operator can re-run with a different email.
            log.warn(
                    "AdminBootstrapRunner: could not create the bootstrap ADMIN account for {} — {}."
                            + " No admin was created by this runner; check for an email collision.",
                    email,
                    ex.getMessage());
            return;
        }

        log.info(
                "AdminBootstrapRunner: created the first ADMIN account ({}). This message will not"
                        + " repeat — future boots see an existing ADMIN and no-op.",
                email);
    }

    /**
     * Test-only factory that sets the two {@code @Value} fields directly rather than
     * through property resolution, so {@code smartcampus.seed.AdminBootstrapRunnerTest}
     * can exercise every branch of {@link #run} — blank env, a too-short password, a
     * clean create, and the already-has-an-admin no-op — inside one Spring context
     * without needing four separate {@code SPRING_APPLICATION_JSON}-style contexts.
     * Package-private: visible only to {@code smartcampus.seed}, not part of the public
     * surface of this bean.
     */
    static AdminBootstrapRunner forTesting(
            AdminAccountRepository adminAccountRepository,
            PasswordEncoder passwordEncoder,
            String email,
            String password) {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(adminAccountRepository, passwordEncoder);
        runner.bootstrapAdminEmail = email;
        runner.bootstrapAdminPassword = password;
        return runner;
    }
}
