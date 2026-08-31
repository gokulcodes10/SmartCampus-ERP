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
import java.math.BigDecimal;
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
 * One student's score in one {@link Exam}. Class name is {@code Marks} (plural),
 * matching {@code PROJECT_PLAN.md} — do not rename to {@code Mark}.
 *
 * <p>Maps exactly to the {@code marks} table created by
 * {@code V4__academic_operations.sql}. {@code spring.jpa.hibernate.ddl-auto=validate}
 * refuses to boot on any drift between this mapping and that migration (clarification
 * G8).
 *
 * <p>{@code maximumMarks} is deliberately NOT duplicated here — it is read through the
 * {@link #exam} association, so an admin correcting an exam's maximum can never leave a
 * marks row validated against a stale value. The database can only enforce {@code
 * marksObtained >= 0}; the service layer MUST additionally enforce {@code
 * marksObtained <= exam.getMaximumMarks()}, a cross-table condition the migration
 * cannot express as a CHECK constraint.
 */
@Entity
@Table(
    name = "marks",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_marks_exam_student", columnNames = {"exam_id", "student_id"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Marks {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "marks_obtained", nullable = false, precision = 6, scale = 2)
    private BigDecimal marksObtained;

    @Column(name = "remarks", nullable = true, length = 255)
    private String remarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entered_by_faculty_id", nullable = true)
    private Faculty enteredByFaculty;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
