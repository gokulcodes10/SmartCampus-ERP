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
 * One problem's membership in one contest: its 1-based position ({@code ordinal},
 * rendered as the usual A/B/C label) and how many points solving it is worth on that
 * contest's leaderboard, independent of the problem's own difficulty.
 *
 * <p>Maps exactly to the {@code contest_problems} table created by {@code
 * V7__coding.sql}. {@code spring.jpa.hibernate.ddl-auto=validate} refuses to boot on
 * any drift between this mapping and that migration (PROJECT_PLAN.md clarification
 * G8).
 *
 * <p>The unique key on {@code (contest_id, problem_id)} is also the target of the
 * composite foreign key {@code (contest_id, problem_id)} on {@code coding_submissions}
 * that stops a submission claiming to be for a contest/problem pair the contest does
 * not actually contain. Hibernate's schema validator does not check foreign keys, so
 * that composite FK is not modelled here or on {@link CodingSubmission}.
 */
@Entity
@Table(
    name = "contest_problems",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_contest_problems_contest_problem",
            columnNames = {"contest_id", "problem_id"}),
        @UniqueConstraint(
            name = "uk_contest_problems_contest_ordinal",
            columnNames = {"contest_id", "ordinal"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ContestProblem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id", nullable = false)
    private CodingContest contest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private CodingProblem problem;

    @Column(name = "ordinal", nullable = false)
    private Integer ordinal;

    @Column(name = "points", nullable = false)
    @Builder.Default
    private Integer points = 100;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
