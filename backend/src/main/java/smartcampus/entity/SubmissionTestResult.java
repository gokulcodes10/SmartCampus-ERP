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
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The evidence behind one submission's verdict: one row per test case the submission
 * was actually run against, so a verdict is auditable rather than asserted.
 *
 * <p>Maps exactly to the {@code submission_test_results} table created by {@code
 * V7__coding.sql}. {@code spring.jpa.hibernate.ddl-auto=validate} refuses to boot on
 * any drift between this mapping and that migration (PROJECT_PLAN.md clarification
 * G8).
 *
 * <p>Only terminal statuses are storable here (the database CHECK rejects PENDING and
 * RUNNING). There is no {@code passed} column: {@code passed == (status ==
 * SubmissionStatus.ACCEPTED)}, derived, never stored.
 *
 * <p>The Java field is deliberately named {@code sample}, matching {@link
 * ProblemTestCase#isSample()}: Lombok generates {@code isSample()}/{@code
 * setSample()}. This is a denormalized snapshot of {@code problem_test_cases.is_sample}
 * as it was at judging time, so the API's "hide hidden-case output" rule cannot change
 * retroactively if an admin later flips a case from sample to hidden.
 *
 * <p>No {@code updated_at} column on this table - a test result row is written once,
 * at judging time, and never revised. No {@code @OneToMany} anywhere on this entity;
 * {@code spring.jpa.open-in-view} is false.
 */
@Entity
@Table(
    name = "submission_test_results",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_submission_test_results_submission_case",
            columnNames = {"submission_id", "test_case_id"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class SubmissionTestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private CodingSubmission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_case_id", nullable = false)
    private ProblemTestCase testCase;

    @Column(name = "ordinal", nullable = false)
    private Integer ordinal;

    @Column(name = "is_sample", nullable = false)
    @Builder.Default
    private boolean sample = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SubmissionStatus status;

    @Column(name = "execution_time_ms", nullable = true)
    private Integer executionTimeMs;

    @Column(name = "memory_kb", nullable = true)
    private Integer memoryKb;

    @Column(name = "judge0_token", length = 64, nullable = true)
    private String judge0Token;

    @Column(name = "judge0_status_id", nullable = true)
    private Integer judge0StatusId;

    @Column(name = "actual_output", nullable = true, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String actualOutput;

    @Column(name = "stderr_output", nullable = true, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String stderrOutput;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
