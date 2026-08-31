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
 * One project entry within a resume (§37).
 *
 * <p>Maps exactly to the {@code resume_projects} table created by {@code V9__resume.sql}.
 * {@code spring.jpa.hibernate.ddl-auto=validate} refuses to boot on any drift between
 * this mapping and that migration (PROJECT_PLAN.md clarification G8).
 *
 * <p>Both start and end dates are optional because students routinely list personal
 * projects with no meaningful start date. When both are present, the database enforces
 * end_date >= start_date.
 *
 * <p>{@code description} is MEDIUMTEXT and MUST carry both {@code
 * columnDefinition = "MEDIUMTEXT"} and {@link JdbcTypeCode @JdbcTypeCode(SqlTypes.LONGVARCHAR)}
 * — exactly as {@code AIMessage.content} does — or {@code ddl-auto=validate} rejects the
 * mapping at boot.
 */
@Entity
@Table(name = "resume_projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ResumeProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", nullable = true, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String description;

    @Column(name = "tech_stack", nullable = true, length = 255)
    private String techStack;

    @Column(name = "project_url", nullable = true, length = 255)
    private String projectUrl;

    @Column(name = "repository_url", nullable = true, length = 255)
    private String repositoryUrl;

    @Column(name = "start_date", nullable = true)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = true)
    private LocalDate endDate;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
