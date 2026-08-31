package smartcampus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One admin-configurable performance band (PROJECT_PLAN.md §22-§24, §60) — the thresholds that
 * turn a student's real marks percentage, attendance percentage and (optionally) GPA into one of
 * the four fixed {@link PerformanceCategory} values. Nothing about the classification may be a
 * literal in Java; both the thresholds and the display colour live here, editable through
 * {@code /api/performance-bands} by an ADMIN.
 *
 * <p>Maps exactly to the {@code performance_bands} table created by
 * {@code V5__analytics.sql}. {@code spring.jpa.hibernate.ddl-auto=validate} refuses to boot on
 * any drift between this mapping and that migration (clarification G8) — in particular {@link
 * #colorHex} maps to a plain {@code String} with no {@code columnDefinition}, because the column
 * is {@code VARCHAR(7)} and Hibernate must resolve it to JDBC VARCHAR, not CHAR.
 *
 * <p>The category set is closed (see {@link PerformanceCategory}), so there is no {@code create}
 * or {@code delete} for this entity — only its thresholds are mutable.
 */
@Entity
@Table(name = "performance_bands")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class PerformanceBand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, unique = true, length = 20)
    private PerformanceCategory category;

    @Column(name = "display_order", nullable = false, unique = true)
    private Integer displayOrder;

    @Column(name = "min_marks_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal minMarksPercentage;

    @Column(name = "min_attendance_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal minAttendancePercentage;

    /** {@code null} means "this band imposes no GPA requirement" — never "unknown", never "0". */
    @Column(name = "min_gpa", nullable = true, precision = 4, scale = 2)
    private BigDecimal minGpa;

    @Column(name = "color_hex", nullable = false, length = 7)
    private String colorHex;

    @Column(name = "description", nullable = true, length = 150)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
