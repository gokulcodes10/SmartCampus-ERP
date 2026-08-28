package smartcampus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.ProvisionUserRequest;
import smartcampus.dto.UserResponse;
import smartcampus.entity.User;
import smartcampus.exception.DuplicateResourceException;
import smartcampus.repository.UserRepository;

/**
 * Admin-side account provisioning — the other half of clarification G1.
 *
 * <p>{@link AuthService#register} deliberately hardcodes {@code STUDENT} so that nobody
 * can self-register as staff. That leaves a gap: {@code FACULTY} and {@code ADMIN}
 * accounts still have to come from somewhere. This service is that somewhere, and it is
 * reachable only through an {@code ADMIN}-only route (see {@code SecurityConfig}).
 *
 * <p>Authorization is enforced entirely at the route level rather than here, so this
 * class assumes its caller has already been established as an admin.
 */
@Service
public class UserProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(UserProvisioningService.class);

    private static final String DUPLICATE_MESSAGE = "An account with this email already exists.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserProvisioningService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Creates an account in any role, on behalf of an authenticated admin. */
    @Transactional
    public UserResponse provision(ProvisionUserRequest request) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException(DUPLICATE_MESSAGE);
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .role(request.role())
                .build();

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            // Same race as AuthService.register: uk_users_email in V2__auth.sql is the
            // real guarantee, and either path must surface the identical clean 409.
            throw new DuplicateResourceException(DUPLICATE_MESSAGE);
        }

        log.info("Admin provisioned new {} account: {}", user.getRole(), user.getEmail());
        return UserResponse.from(user);
    }
}
