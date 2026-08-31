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
 * One row per (student, question): completion marking, bookmark flag and the timestamp
 * completion happened.
 *
 * <p>Maps exactly to the {@code interview_question_progress} table created by
 * {@code V10__interview.sql}. {@code spring.jpa.hibernate.ddl-auto=validate} refuses to
 * boot on any drift between this mapping and that migration (PROJECT_PLAN.md
 * clarification G8).
 *
 * <p>This is the whole of "completion marking, bookmarks and progress tracking" in §38.
 * The progress summary endpoint aggregates this table and nothing else, so no dashboard
 * number in this module can be invented (§60, §69).
 */
@Entity
@Table(
    name = "interview_question_progress",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_interview_question_progress_student_question",
            columnNames = {"student_id", "question_id"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class InterviewQuestionProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private InterviewQuestion question;

    @Column(name = "completed", nullable = false)
    @Builder.Default
    private boolean completed = false;

    @Column(name = "bookmarked", nullable = false)
    @Builder.Default
    private boolean bookmarked = false;

    @Column(name = "completed_at", nullable = true)
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
