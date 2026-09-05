package smartcampus.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import smartcampus.TestcontainersConfiguration;

/**
 * {@code smartcampus.seed.enabled=true} is set, but the {@code seed} Spring profile is
 * NOT active (this context runs on the default profile) — {@link DevDataSeeder} must not
 * exist as a bean. A stray property alone must do nothing; see that class's javadoc for
 * why both switches are required. This is also, incidentally, a live demonstration that
 * a plain default-profile boot (like every other test in this suite) is unaffected by
 * this property even if something set it.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "smartcampus.seed.enabled=true")
class DevDataSeederProfileAbsentTest {

    @Autowired private ApplicationContext applicationContext;

    @Test
    void seederBeanDoesNotExistWhenProfileIsAbsent() {
        assertThat(applicationContext.getBeanNamesForType(DevDataSeeder.class)).isEmpty();
    }
}
