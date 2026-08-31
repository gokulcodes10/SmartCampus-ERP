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
 * The department-based leg of the §34 eligibility criteria for a job/drive.
 *
 * <p>Maps exactly to the {@code job_eligible_departments} table created by
 * {@code V8__placement.sql}. {@code spring.jpa.hibernate.ddl-auto=validate} refuses to
 * boot on any drift between this mapping and that migration (PROJECT_PLAN.md clarification G8).
 *
 * <p>SEMANTICS (from migration): ZERO rows for a job means the drive is open to EVERY
 * department. One or more rows means the drive is restricted to exactly those departments.
 * There is no "all departments" sentinel row and no NULL {@code department_id}.
 *
 * <p>This is an explicit child entity with its own JPA entity and repository, NOT a
 * {@code @ManyToMany} join table. This matches the house rule: {@code spring.jpa.open-in-view}
 * is false, so lazy collections touched during response serialization throw. Both the job
 * and its departments are read through their own repositories. See {@link CodingContest}
 * javadoc for the precedent.
 */
@Entity
@Table(
    name = "job_eligible_departments",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_job_eligible_departments_job_department",
            columnNames = {"job_id", "department_id"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class JobEligibleDepartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
