package smartcampus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A single-use OTP issued for the forgot-password flow.
 *
 * <p>Maps exactly to the {@code password_reset_tokens} table created by {@code
 * V2__auth.sql} - {@code spring.jpa.hibernate.ddl-auto=validate} refuses to boot on any
 * drift between this mapping and that migration (PROJECT_PLAN.md clarification G8).
 *
 * <p>{@link #tokenHash} is never the plaintext OTP - see {@code
 * smartcampus.util.OtpUtils#hash(String)}. It is a deterministic (unsalted) digest so
 * it can be looked up by equality, which is what the migration's composite {@code
 * (user_id, token_hash)} index is for; the six-digit OTP space is protected against
 * brute force by {@link #attemptCount} rather than by the hash itself. A row is
 * consumed exactly once: {@link #used} flips to {@code true} either when a reset
 * actually succeeds or when {@link #attemptCount} reaches the configured cap, whichever
 * happens first.
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = "tokenHash")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Builder.Default
    @Column(name = "used", nullable = false)
    private boolean used = false;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
