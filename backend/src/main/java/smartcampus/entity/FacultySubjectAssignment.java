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
 * Which faculty teaches which subject/section, in which academic year/semester.
 *
 * <p>Maps exactly to the {@code faculty_subject_assignments} table created by
 * {@code V3__academic.sql}. {@code spring.jpa.hibernate.ddl-auto=validate} refuses to boot
 * on any drift between this mapping and that migration (PROJECT_PLAN.md clarification G8).
 *
 * <p>Every faculty authorization check in the application routes through this table
 * (PROJECT_PLAN.md clarification G2). The exact tuple this entity defines serves as a
 * byproduct the authorization check every faculty-write endpoint must run: "is this
 * faculty assigned to this subject (optionally + year/semester/section)?" via WHERE
 * faculty_id = ? AND subject_id = ? [AND academic_year = ? AND semester = ? AND
 * section = ?] — a leftmost-prefix match against the unique key.
 */
@Entity
@Table(
    name = "faculty_subject_assignments",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_fsa_faculty_subject_year_sem_section",
            columnNames = {"faculty_id", "subject_id", "academic_year", "semester", "section"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class FacultySubjectAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(name = "academic_year", nullable = false, length = 9)
    private String academicYear;

    @Column(name = "semester", nullable = false)
    private Integer semester;

    @Column(name = "section", nullable = false, length = 10)
    private String section;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
