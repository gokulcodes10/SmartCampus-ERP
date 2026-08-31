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
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * A scheduled exam: subject, type, date, maximum marks and owning faculty
 * (PROJECT_PLAN.md clarification G4). Clarification G5: an "assignment" is simply
 * {@code examType = ASSIGNMENT} — there is no separate submission module.
 *
 * <p>Maps exactly to the {@code exams} table created by
 * {@code V4__academic_operations.sql}. {@code spring.jpa.hibernate.ddl-auto=validate}
 * refuses to boot on any drift between this mapping and that migration (clarification
 * G8).
 *
 * <p>{@code faculty} is {@code null} when an ADMIN schedules the exam directly (an
 * admin has no {@code faculty} row). The scope tuple ({@code subject}, {@code
 * academicYear}, {@code semester}, {@code section}) is immutable after creation — see
 * {@code ExamUpdateRequest} in the contract.
 */
@Entity
@Table(
    name = "exams",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_exams_scope_type_title",
            columnNames = {"subject_id", "academic_year", "semester", "section", "exam_type", "title"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id", nullable = true)
    private Faculty faculty;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_type", nullable = false, length = 20)
    private ExamType examType;

    @Column(name = "academic_year", nullable = false, length = 9)
    private String academicYear;

    @Column(name = "semester", nullable = false)
    private Integer semester;

    @Column(name = "section", nullable = false, length = 10)
    private String section;

    @Column(name = "exam_date", nullable = false)
    private LocalDate examDate;

    @Column(name = "maximum_marks", nullable = false, precision = 6, scale = 2)
    private BigDecimal maximumMarks;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ExamStatus status = ExamStatus.SCHEDULED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
