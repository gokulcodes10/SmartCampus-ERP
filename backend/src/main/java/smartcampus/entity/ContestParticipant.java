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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Registration plus the denormalized per-contest leaderboard row (score / solved /
 * penalty), recomputed wholesale from {@code coding_submissions} - never incremented in
 * place, so it can never drift from the submissions that justify it (see {@code
 * ContestScoringService}).
 *
 * <p>Maps exactly to the {@code contest_participants} table created by {@code
 * V7__coding.sql}. {@code spring.jpa.hibernate.ddl-auto=validate} refuses to boot on
 * any drift between this mapping and that migration (PROJECT_PLAN.md clarification
 * G8).
 *
 * <p>Carries both {@link #registeredAt} ({@code @CreationTimestamp}, not updatable -
 * the moment the student registered) and {@link #createdAt} ({@code
 * @CreationTimestamp}, matching house style) - both are the same instant on insert but
 * serve different meanings, per the migration.
 *
 * <p>{@code chk_contest_participants_unsolved_is_clean} requires that a participant who
 * has solved nothing has {@code total_score = 0}, {@code penalty_seconds = 0} and
 * {@code last_accepted_at = null}: a recompute that finds zero solved problems MUST
 * write all three back to their clean values, never leave stale ones.
 */
@Entity
@Table(
    name = "contest_participants",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_contest_participants_contest_student",
            columnNames = {"contest_id", "student_id"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ContestParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id", nullable = false)
    private CodingContest contest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @CreationTimestamp
    @Column(name = "registered_at", nullable = false, updatable = false)
    private LocalDateTime registeredAt;

    @Column(name = "total_score", nullable = false)
    @Builder.Default
    private Integer totalScore = 0;

    @Column(name = "problems_solved", nullable = false)
    @Builder.Default
    private Integer problemsSolved = 0;

    @Column(name = "penalty_seconds", nullable = false)
    @Builder.Default
    private Integer penaltySeconds = 0;

    @Column(name = "last_accepted_at", nullable = true)
    private LocalDateTime lastAcceptedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
