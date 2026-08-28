package smartcampus.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import smartcampus.repository.UserRepository;

/**
 * Loads a {@link UserDetails} by email for Spring Security's own authentication
 * machinery - primarily the login flow's {@code AuthenticationManager} /
 * {@code DaoAuthenticationProvider}, wired up in {@code SecurityConfig}.
 * {@code smartcampus.entity.User} implements {@link UserDetails} directly, so no
 * adapter type is needed here.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository
                .findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account found for " + email));
    }
}
