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
import java.time.LocalDate;
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
 * One scheduled, independently editable and completable item of an {@link
 * AIStudyPlan}.
 *
 * <p>Maps exactly to the {@code ai_study_plan_items} table created by {@code V6__ai.sql}.
 * {@code spring.jpa.hibernate.ddl-auto=validate} refuses to boot on any drift between
 * this mapping and that migration (PROJECT_PLAN.md clarification G8).
 *
 * <p>{@code subject} is optional (a model may legitimately propose an item with no
 * catalog subject behind it) and {@code ON DELETE SET NULL}; {@code subjectLabel} keeps
 * the human-readable name even when {@code subject} is null or later cleared.
 *
 * <p>{@code description} is MEDIUMTEXT and MUST carry both {@code columnDefinition =
 * "MEDIUMTEXT"} and {@link JdbcTypeCode @JdbcTypeCode(SqlTypes.LONGVARCHAR)}, exactly as
 * {@code CodingProblem.description} does.
 */
@Entity
@Table(
    name = "ai_study_plan_items",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_ai_study_plan_items_plan_position",
            columnNames = {"study_plan_id", "position"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class AIStudyPlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_plan_id", nullable = false)
    private AIStudyPlan studyPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = true)
    private Subject subject;

    @Column(name = "subject_label", nullable = true, length = 150)
    private String subjectLabel;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = true, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String description;

    @Column(name = "duration_minutes", nullable = true)
    private Integer durationMinutes;

    @Column(name = "completed", nullable = false)
    @Builder.Default
    private boolean completed = false;

    @Column(name = "completed_at", nullable = true)
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
