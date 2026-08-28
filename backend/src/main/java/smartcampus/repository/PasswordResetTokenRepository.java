package smartcampus.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import smartcampus.entity.PasswordResetToken;

/**
 * Persistence access for {@link PasswordResetToken}.
 *
 * <p>At most one OTP is meant to be live for a user at a time -
 * {@code smartcampus.service.PasswordResetService} invalidates any previously issued,
 * still-unused token before creating a new one (see {@link
 * #invalidateActiveTokensForUser}), so {@link
 * #findFirstByUser_IdAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc} normally finds
 * at most one candidate row. Backed by {@code idx_password_reset_tokens_user_token}
 * (leading column {@code user_id}) from {@code V2__auth.sql}.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findFirstByUser_IdAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            Long userId, LocalDateTime now);

    /**
     * Marks every currently-unused token for {@code userId} as used. Called before
     * issuing a fresh OTP so an earlier, still-valid code from a previous request
     * cannot also be used to reset the password.
     */
    @Modifying
    @Query("update PasswordResetToken t set t.used = true where t.user.id = :userId and t.used = false")
    void invalidateActiveTokensForUser(@Param("userId") Long userId);
}
