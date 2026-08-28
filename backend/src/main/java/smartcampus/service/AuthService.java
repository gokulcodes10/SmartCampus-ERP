package smartcampus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.AuthResponse;
import smartcampus.dto.LoginRequest;
import smartcampus.dto.RegisterRequest;
import smartcampus.dto.UserResponse;
import smartcampus.entity.Role;
import smartcampus.entity.User;
import smartcampus.exception.DuplicateResourceException;
import smartcampus.exception.InvalidCredentialsException;
import smartcampus.repository.UserRepository;
import smartcampus.security.JwtService;

/**
 * Business logic for registration, login, and "who am I".
 *
 * <p>Login is implemented against {@link UserRepository} and {@link PasswordEncoder}
 * directly rather than through an {@code AuthenticationManager} / {@code
 * DaoAuthenticationProvider}: the default pre-authentication checks a {@code
 * DaoAuthenticationProvider} runs would throw a distinct exception for a disabled
 * account before ever comparing the password, which — left unhandled — would either
 * leak account existence or surface as an opaque 500. Doing the lookup, the disabled
 * check, and the password comparison in one place guarantees every failure mode
 * (unknown email, wrong password, disabled account) collapses to the exact same
 * {@link InvalidCredentialsException} message, satisfying the no-enumeration
 * requirement.
 */
@Service
public class AuthService {

    private static final String GENERIC_LOGIN_FAILURE = "Invalid email or password.";

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Self-registers a new {@code STUDENT} account. The role is never taken from the
     * caller (PROJECT_PLAN.md clarification G1) — it is hardcoded here regardless of
     * anything the request might otherwise imply. Creates the {@link User} row only;
     * the pending Student profile is out of this scope (Phase 3).
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("An account with this email already exists.");
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .role(Role.STUDENT)
                .build();

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            // Belt and braces against a duplicate email that lands between the
            // existsByEmail check above and this insert (uk_users_email in V2__auth.sql
            // is the real guarantee); translate it to the same clean 409 either way.
            throw new DuplicateResourceException("An account with this email already exists.");
        }

        log.info("Registered new STUDENT account: {}", user.getEmail());
        return UserResponse.from(user);
    }

    /**
     * Verifies credentials and issues a JWT. Every failure — unknown email, wrong
     * password, or a disabled account — throws the identical {@link
     * InvalidCredentialsException} so a caller can never tell which case occurred.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository
                .findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new InvalidCredentialsException(GENERIC_LOGIN_FAILURE));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException(GENERIC_LOGIN_FAILURE);
        }

        if (!user.isEnabled()) {
            throw new InvalidCredentialsException(GENERIC_LOGIN_FAILURE);
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, UserResponse.from(user));
    }

    /** The safe summary of the account currently authenticated via the JWT filter. */
    public UserResponse currentUser(User authenticatedUser) {
        return UserResponse.from(authenticatedUser);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
