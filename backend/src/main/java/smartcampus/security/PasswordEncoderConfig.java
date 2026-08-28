package smartcampus.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Central {@link PasswordEncoder} bean, used by registration (hashing) and login
 * (verification via {@code DaoAuthenticationProvider} or a direct {@code matches}
 * call). BCrypt per the confirmed stack decision (PROJECT_PLAN.md §1).
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
