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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One turn of an {@link AIConversation}: the SYSTEM turn carrying the student's real
 * academic context, the USER turn, or the ASSISTANT turn a real provider call produced.
 *
 * <p>Maps exactly to the {@code ai_messages} table created by {@code V6__ai.sql}.
 * {@code spring.jpa.hibernate.ddl-auto=validate} refuses to boot on any drift between
 * this mapping and that migration (PROJECT_PLAN.md clarification G8). The table has NO
 * {@code updated_at} column — a message, once written, is never edited — so this entity
 * deliberately has no {@code updatedAt} field.
 *
 * <p>{@code content} is MEDIUMTEXT, not TEXT, and MUST carry both {@code
 * columnDefinition = "MEDIUMTEXT"} and {@link JdbcTypeCode @JdbcTypeCode(SqlTypes.LONGVARCHAR)}
 * — exactly as {@code CodingProblem.description} does — or {@code ddl-auto=validate}
 * rejects the mapping at boot.
 *
 * <p>{@code grounded} marks a SYSTEM turn built from real rows in {@code marks}, {@code
 * attendance} and {@code exams}. {@code
 * chk_ai_messages_assistant_has_model} — the anti-fabrication constraint (§69) — rejects
 * any ASSISTANT row with a null {@code model} at the database, not just in application
 * code.
 */
@Entity
@Table(
    name = "ai_messages",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_ai_messages_conversation_seq",
            columnNames = {"conversation_id", "seq_no"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class AIMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private AIConversation conversation;

    @Column(name = "seq_no", nullable = false)
    private Integer seqNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private AIMessageRole role;

    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String content;

    @Column(name = "model", nullable = true, length = 120)
    private String model;

    @Column(name = "grounded", nullable = false)
    @Builder.Default
    private boolean grounded = false;

    @Column(name = "prompt_tokens", nullable = true)
    private Integer promptTokens;

    @Column(name = "completion_tokens", nullable = true)
    private Integer completionTokens;

    @Column(name = "total_tokens", nullable = true)
    private Integer totalTokens;

    @Column(name = "latency_ms", nullable = true)
    private Integer latencyMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
