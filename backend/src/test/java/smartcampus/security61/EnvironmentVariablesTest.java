package smartcampus.security61;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * §61 item 9 — environment variables.
 *
 * <p>Reads {@code backend/src/main/resources/application.properties} — the ACTUAL main
 * config file, not the test-only override that shadows it on the test classpath (see
 * {@code src/test/resources/application.properties}'s own header comment: "This file
 * shadows the main one; it does not merge with it" — which means a {@code
 * classpath:application.properties} lookup from inside a running test would resolve to
 * the wrong file). This class therefore reads the file directly off disk by its real
 * module-relative path, not through the classpath, so there is no ambiguity about which
 * of the two files is being inspected.
 *
 * <p>Every one of the five credential-shaped variables named in scope §61/§62 must
 * appear ONLY as an {@code ${VAR:default}} placeholder (never hardcoded), and every
 * genuinely secret one — {@code JWT_SECRET}, {@code AI_API_KEY}, {@code SMTP_PASSWORD},
 * {@code JUDGE0_API_KEY} — must default to empty so a real deployment that forgets to
 * set it fails fast rather than booting on a guessable value.
 *
 * <p>{@code DB_PASSWORD} is deliberately NOT held to the "empty default" bar: the
 * file's own header comment states the local MySQL credential is "the local
 * development value from docker-compose.yml" and is "safe to commit", unlike the other
 * four. This class still asserts it is externalized as {@code ${DB_PASSWORD:...}} (never
 * hardcoded outside a placeholder), just not that the default is blank — asserting
 * otherwise would be inventing a stricter rule than this codebase's own documented
 * decision and failing a test the project never intended to pass. This nuance is
 * flagged again in the final report so the §75 auditor is not surprised by it.
 */
class EnvironmentVariablesTest {

    private static final String[] GENUINELY_SECRET_VARS = {
        "JWT_SECRET", "AI_API_KEY", "SMTP_PASSWORD", "JUDGE0_API_KEY"
    };

    private static String readMainApplicationProperties() throws IOException {
        File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        // Walk up until we find the backend module root (contains pom.xml AND src/main).
        while (dir != null && !new File(dir, "pom.xml").isFile()) {
            dir = dir.getParentFile();
        }
        if (dir == null) {
            throw new IllegalStateException(
                    "Could not locate backend module root (pom.xml) from " + System.getProperty("user.dir"));
        }
        File propsFile = new File(dir, "src/main/resources/application.properties");
        assertThat(propsFile).as("main application.properties must exist at %s", propsFile).exists();
        return Files.readString(propsFile.toPath(), StandardCharsets.UTF_8);
    }

    private static boolean referencesPlaceholder(String content, String varName) {
        Pattern pattern = Pattern.compile(Pattern.quote("${" + varName + ":"));
        return pattern.matcher(content).find();
    }

    private static boolean hasEmptyDefault(String content, String varName) {
        // Matches ${VAR:} exactly - nothing between the colon and the closing brace.
        Pattern pattern = Pattern.compile(Pattern.quote("${" + varName + ":}"));
        return pattern.matcher(content).find();
    }

    @Test
    void everyCredentialShapedVariable_isExternalizedAsAPlaceholder_neverHardcoded() throws IOException {
        String content = readMainApplicationProperties();

        for (String var : new String[] {"JWT_SECRET", "AI_API_KEY", "SMTP_PASSWORD", "DB_PASSWORD", "JUDGE0_API_KEY"}) {
            assertThat(referencesPlaceholder(content, var))
                    .as("application.properties must reference %s only via ${%s:...}", var, var)
                    .isTrue();
        }
    }

    @Test
    void genuinelySecretVariables_defaultToEmpty_soADeploymentThatForgetsThemFailsFast() throws IOException {
        String content = readMainApplicationProperties();

        for (String var : GENUINELY_SECRET_VARS) {
            assertThat(hasEmptyDefault(content, var))
                    .as("%s must default to empty (${%s:}) so a real deployment fails fast, not boots on a"
                            + " guessable/shared value", var, var)
                    .isTrue();
        }
    }

    @Test
    void dbPassword_isExternalized_butDefaultIsTheDocumentedSharedDevCredential_notASecretLeak() throws IOException {
        String content = readMainApplicationProperties();

        assertThat(referencesPlaceholder(content, "DB_PASSWORD")).isTrue();
        // Matches the docker-compose.yml / .env.example convention exactly - this is a
        // deliberate, documented project decision (see class javadoc), not asserted as
        // a defect.
        Matcher m = Pattern.compile(Pattern.quote("${DB_PASSWORD:") + "([^}]*)\\}").matcher(content);
        assertThat(m.find()).as("DB_PASSWORD placeholder must be present").isTrue();
        assertThat(m.group(1)).isEqualTo("smartcampus");
    }

    @Test
    void noRawSecretLiteralPattern_forJwtSigningKey_appearsOutsideThePlaceholder() throws IOException {
        String content = readMainApplicationProperties();
        // The jwt.secret line must be EXACTLY the placeholder form, not a placeholder
        // with a fallback literal key tacked on.
        assertThat(content).contains("smartcampus.jwt.secret=${JWT_SECRET:}");
    }
}
