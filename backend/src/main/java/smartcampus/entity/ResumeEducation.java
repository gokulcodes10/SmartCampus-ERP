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
 * One education entry within a resume (§37).
 *
 * <p>Maps exactly to the {@code resume_educations} table created by {@code V9__resume.sql}.
 * {@code spring.jpa.hibernate.ddl-auto=validate} refuses to boot on any drift between
 * this mapping and that migration (PROJECT_PLAN.md clarification G8).
 *
 * <p>The grade is stored as a NUMBER PLUS ITS SCALE, not as free text. This allows
 * validation, prefilling from live CGPA, and range-checking. The pair is all-or-nothing:
 * either both {@link #gradeValue} and {@link #gradeScale} are set, or both are null.
 *
 * <p>Years are nullable because some degree types may not have meaningful start/end years.
 * When present, they are constrained to 1950..2100.
 */
@Entity
@Table(name = "resume_educations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ResumeEducation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    @Column(name = "institution", nullable = false, length = 200)
    private String institution;

    @Column(name = "degree", nullable = true, length = 150)
    private String degree;

    @Column(name = "field_of_study", nullable = true, length = 150)
    private String fieldOfStudy;

    @Column(name = "start_year", nullable = true)
    private Integer startYear;

    @Column(name = "end_year", nullable = true)
    private Integer endYear;

    @Column(name = "grade_value", nullable = true, precision = 5, scale = 2)
    private BigDecimal gradeValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade_scale", nullable = true, length = 20)
    private GradeScale gradeScale;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
