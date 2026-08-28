package smartcampus.dto;

import java.time.LocalDateTime;
import smartcampus.entity.Role;
import smartcampus.entity.User;

/**
 * The safe, public shape of a {@link User} — every field a caller is allowed to see and
 * nothing else. No password or hash ever appears here, so it is safe to return from
 * registration, login, and {@code GET /api/auth/me} alike.
 */
public record UserResponse(
        Long id,
        String email,
        String fullName,
        Role role,
        boolean enabled,
        LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt());
    }
}
