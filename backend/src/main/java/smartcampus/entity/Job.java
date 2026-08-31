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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * One placement drive or job posting (§33-§35), carrying both the posting details and the
 * §34 eligibility criteria (minimum CGPA, minimum marks percentage, required graduation year),
 * plus the §35/§53 application deadline.
 *
 * <p>Maps exactly to the {@code jobs} table created by {@code V8__placement.sql}.
 * {@code spring.jpa.hibernate.ddl-auto=validate} refuses to boot on any drift between
 * this mapping and that migration (PROJECT_PLAN.md clarification G8).
 *
 * <p>{@link #description} is MEDIUMTEXT and MUST carry both {@code
 * columnDefinition = "MEDIUMTEXT"} and {@link JdbcTypeCode @JdbcTypeCode(SqlTypes.LONGVARCHAR)}.
 *
 * <p>ELIGIBILITY CRITERIA: {@link #minCgpa}, {@link #minMarksPercentage}, and
 * {@link #graduationYear} are all nullable. NULL means "no requirement"; a non-NULL value
 * is compared against the student's live values from AnalyticsService (never cached). The
 * department-based criterion is stored as a separate {@link JobEligibleDepartment} child table.
 * An EMPTY set means "every department is eligible"; one or more rows restricts to exactly
 * those departments.
 *
 * <p>{@link #status} governs visibility: DRAFT and CANCELLED are hidden from non-admin
 * requests (404, not 403, per house convention).
 *
 * <p>No {@code @OneToMany} to {@link JobEligibleDepartment}: {@code spring.jpa.open-in-view}
 * is false, so a lazy collection touched during serialization throws. {@link JobEligibleDepartment}
 * is read through its own repository.
 */
@Entity
@Table(
    name = "jobs",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_jobs_company_title_deadline",
            columnNames = {"company_id", "title", "application_deadline"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "description", nullable = true, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String description;

    @Column(name = "location", nullable = true, length = 150)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 20)
    private JobType jobType;

    @Column(name = "openings", nullable = true)
    private Integer openings;

    @Column(name = "salary_min", nullable = true, precision = 12, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", nullable = true, precision = 12, scale = 2)
    private BigDecimal salaryMax;

    @Column(name = "salary_currency", nullable = false, length = 3)
    @Builder.Default
    private String salaryCurrency = "INR";

    @Column(name = "min_cgpa", nullable = true, precision = 4, scale = 2)
    private BigDecimal minCgpa;

    @Column(name = "min_marks_percentage", nullable = true, precision = 5, scale = 2)
    private BigDecimal minMarksPercentage;

    @Column(name = "graduation_year", nullable = true)
    private Integer graduationYear;

    @Column(name = "application_deadline", nullable = false)
    private LocalDateTime applicationDeadline;

    @Column(name = "drive_date", nullable = true)
    private LocalDate driveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private JobStatus status = JobStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_by", nullable = false)
    private User postedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
