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
 * One work experience entry within a resume (§37).
 *
 * <p>Maps exactly to the {@code resume_experiences} table created by
 * {@code V9__resume.sql}. {@code spring.jpa.hibernate.ddl-auto=validate} refuses to
 * boot on any drift between this mapping and that migration (PROJECT_PLAN.md
 * clarification G8).
 *
 * <p><b>CURRENT POSITION LOCKING:</b> {@link #currentPosition} and {@link #endDate} are
 * locked to each other:
 * <ul>
 *   <li>a CURRENT role must have no end_date (not "Jan 2025 - Mar 2025 (Present)")
 *   <li>a PAST role must have an end_date (not "Jan 2025 - " with a dangling dash)
 * </ul>
 * Together they make "Present" a fact about the row rather than a guess by the
 * renderer. This is enforced by the database CHECKs.
 *
 * <p>{@code description} is MEDIUMTEXT and MUST carry both {@code
 * columnDefinition = "MEDIUMTEXT"} and {@link JdbcTypeCode @JdbcTypeCode(SqlTypes.LONGVARCHAR)}
 * — exactly as {@code AIMessage.content} does — or {@code ddl-auto=validate} rejects the
 * mapping at boot.
 */
@Entity
@Table(name = "resume_experiences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ResumeExperience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "role_title", nullable = false, length = 150)
    private String roleTitle;

    @Column(name = "location", nullable = true, length = 150)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = true, length = 20)
    private EmploymentType employmentType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = true)
    private LocalDate endDate;

    @Column(name = "is_current", nullable = false)
    private boolean currentPosition;

    @Column(name = "description", nullable = true, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
