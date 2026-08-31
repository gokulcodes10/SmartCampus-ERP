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
 * One student's attempt at one problem, optionally inside a contest, carrying the full
 * §29 status and aggregate verdict. This IS the submission history table.
 *
 * <p>Maps exactly to the {@code coding_submissions} table created by {@code
 * V7__coding.sql}. {@code spring.jpa.hibernate.ddl-auto=validate} refuses to boot on
 * any drift between this mapping and that migration (PROJECT_PLAN.md clarification
 * G8).
 *
 * <p>{@link #createdAt} IS the submission time - there is no separate {@code
 * submitted_at} column, and contest time penalties are measured from it, so it is never
 * rewritten. {@link #contest} is {@code null} for a practice submission from the
 * playground.
 *
 * <p>The database additionally carries a composite foreign key {@code (contest_id,
 * problem_id) -> contest_problems} that stops a submission claiming to belong to a
 * contest that does not contain that problem. Hibernate's schema validator does not
 * inspect foreign keys, only tables and columns, so the three plain {@code @ManyToOne}
 * mappings below ({@link #problem}, {@link #student}, {@link #contest}) are correct and
 * complete; the composite FK is deliberately not modelled in JPA.
 *
 * <p>No {@code @OneToMany} to {@code SubmissionTestResult}: {@code
 * spring.jpa.open-in-view} is false, so a lazy collection touched outside a transaction
 * throws at serialization time. Results are read through {@code
 * SubmissionTestResultRepository}.
 */
@Entity
@Table(name = "coding_submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class CodingSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private CodingProblem problem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id", nullable = true)
    private CodingContest contest;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 10)
    private ProgrammingLanguage language;

    @Column(name = "source_code", nullable = false, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String sourceCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.PENDING;

    @Column(name = "passed_test_cases", nullable = false)
    @Builder.Default
    private Integer passedTestCases = 0;

    @Column(name = "total_test_cases", nullable = false)
    @Builder.Default
    private Integer totalTestCases = 0;

    @Column(name = "score", nullable = false)
    @Builder.Default
    private Integer score = 0;

    @Column(name = "max_score", nullable = false)
    @Builder.Default
    private Integer maxScore = 0;

    @Column(name = "execution_time_ms", nullable = true)
    private Integer executionTimeMs;

    @Column(name = "memory_kb", nullable = true)
    private Integer memoryKb;

    @Column(name = "failed_test_case_ordinal", nullable = true)
    private Integer failedTestCaseOrdinal;

    @Column(name = "compile_output", nullable = true, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String compileOutput;

    @Column(name = "error_message", nullable = true, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String errorMessage;

    @Column(name = "judged_at", nullable = true)
    private LocalDateTime judgedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
