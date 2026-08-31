package smartcampus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.CreationTimestamp;

/**
 * One row per AI request ATTEMPT by one {@link User}, written whether the provider
 * answered or not — the §61 rate-limit ledger and the §69 honest usage record.
 *
 * <p>Maps exactly to the {@code ai_request_logs} table created by {@code V6__ai.sql}.
 * {@code spring.jpa.hibernate.ddl-auto=validate} refuses to boot on any drift between
 * this mapping and that migration (PROJECT_PLAN.md clarification G8). The table has NO
 * {@code updated_at} column, so this entity deliberately has no {@code updatedAt}
 * field.
 *
 * <p>Must be committed even when the surrounding request then throws — write it from a
 * {@code @Transactional(propagation = REQUIRES_NEW)} recorder, mirroring the Phase 2
 * brute-force-cap lesson (rollback-on-unchecked would otherwise discard the very row
 * that proves the attempt happened).
 */
@Entity
@Table(name = "ai_request_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class AIRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = true)
    private AIConversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature", nullable = false, length = 30)
    private AIFeature feature;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private AIRequestOutcome outcome;

    @Column(name = "model", nullable = true, length = 120)
    private String model;

    @Column(name = "prompt_tokens", nullable = true)
    private Integer promptTokens;

    @Column(name = "completion_tokens", nullable = true)
    private Integer completionTokens;

    @Column(name = "total_tokens", nullable = true)
    private Integer totalTokens;

    @Column(name = "latency_ms", nullable = true)
    private Integer latencyMs;

    @Column(name = "error_message", nullable = true, length = 500)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
