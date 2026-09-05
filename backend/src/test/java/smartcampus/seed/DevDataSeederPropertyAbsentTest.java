package smartcampus.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import smartcampus.TestcontainersConfiguration;

/**
 * The {@code seed} profile is active but {@code smartcampus.seed.enabled} is left at its
 * default ({@code false}, from {@code application-seed.properties}) — {@link
 * DevDataSeeder} must not exist as a bean. A stray profile alone must do nothing; see
 * that class's javadoc for why both switches are required.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("seed")
class DevDataSeederPropertyAbsentTest {

    @Autowired private ApplicationContext applicationContext;

    @Test
    void seederBeanDoesNotExistWhenPropertyIsAbsent() {
        assertThat(applicationContext.getBeanNamesForType(DevDataSeeder.class)).isEmpty();
    }
}
