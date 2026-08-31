package smartcampus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
 * One admin-configurable percentage-to-grade band (PROJECT_PLAN.md clarification G7).
 * Nothing about grading may be hard-coded in Java — both the band boundaries and grade
 * points are rows in this table, editable through {@code /api/grade-bands} by an ADMIN.
 *
 * <p>Maps exactly to the {@code grade_bands} table created by
 * {@code V4__academic_operations.sql}. {@code spring.jpa.hibernate.ddl-auto=validate}
 * refuses to boot on any drift between this mapping and that migration (clarification
 * G8).
 *
 * <p>{@link #passGrade} is a PRIMITIVE {@code boolean} mapped with a plain {@code
 * @Column} against the migration's {@code TINYINT(1)} column — the same pattern {@code
 * User.enabled} and {@code PasswordResetToken.used} already use. Do NOT change this to
 * {@code Boolean}, add {@code @Type}, or add {@code columnDefinition}; that pattern is
 * what validates against this exact column type.
 */
@Entity
@Table(name = "grade_bands")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class GradeBand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "grade", nullable = false, unique = true, length = 5)
    private String grade;

    @Column(name = "min_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal minPercentage;

    @Column(name = "max_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxPercentage;

    @Column(name = "grade_point", nullable = false, precision = 4, scale = 2)
    private BigDecimal gradePoint;

    @Builder.Default
    @Column(name = "pass_grade", nullable = false)
    private boolean passGrade = true;

    @Column(name = "description", nullable = true, length = 100)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
