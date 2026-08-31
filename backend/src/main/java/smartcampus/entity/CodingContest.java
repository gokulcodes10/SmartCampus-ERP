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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * A timed coding contest (§31, §32) with its per-wrong-attempt ICPC-style penalty.
 *
 * <p>Maps exactly to the {@code coding_contests} table created by {@code
 * V7__coding.sql}. {@code spring.jpa.hibernate.ddl-auto=validate} refuses to boot on
 * any drift between this mapping and that migration (PROJECT_PLAN.md clarification
 * G8).
 *
 * <p>{@link #status} is the AUTHORING lifecycle only (see {@link ContestStatus}); the
 * live upcoming/running/ended phase is never stored and is computed by {@link
 * ContestPhase}.
 *
 * <p>No {@code @OneToMany} to {@link ContestProblem} or {@link ContestParticipant}:
 * {@code spring.jpa.open-in-view} is false, so a lazy collection touched outside a
 * transaction throws at serialization time. Both are read through their own
 * repositories.
 */
@Entity
@Table(
    name = "coding_contests",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_coding_contests_slug", columnNames = {"slug"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class CodingContest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slug", nullable = false, length = 120, unique = true)
    private String slug;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = true, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String description;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ContestStatus status = ContestStatus.DRAFT;

    @Column(name = "penalty_minutes_per_wrong_attempt", nullable = false)
    @Builder.Default
    private Integer penaltyMinutesPerWrongAttempt = 10;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
