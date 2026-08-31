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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * One test case belonging to a {@link CodingProblem} (clarification G3).
 *
 * <p>Maps exactly to the {@code problem_test_cases} table created by {@code
 * V7__coding.sql}. {@code spring.jpa.hibernate.ddl-auto=validate} refuses to boot on
 * any drift between this mapping and that migration (PROJECT_PLAN.md clarification
 * G8).
 *
 * <p>{@code sample} cases are shown to the student and are what the playground "Run"
 * button executes; hidden cases ({@code sample == false}) drive the ACCEPTED /
 * WRONG_ANSWER verdict and are never serialized to a non-admin caller.
 *
 * <p>The Java field is deliberately named {@code sample}, not {@code isSample}: Lombok
 * then generates {@code isSample()}/{@code setSample()}, and the Spring Data derived
 * query keyword for it is {@code Sample} (see {@code
 * findByProblemIdAndSampleTrueOrderByOrdinalAsc} in the repository). The column itself
 * is {@code is_sample}; the DTO layer is what carries the {@code isSample} JSON key to
 * the client.
 *
 * <p>No {@code @OneToMany} back to {@link CodingProblem} or forward to {@code
 * SubmissionTestResult}: {@code spring.jpa.open-in-view} is false, so a lazy collection
 * touched outside a transaction throws at serialization time.
 */
@Entity
@Table(
    name = "problem_test_cases",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_problem_test_cases_problem_ordinal",
            columnNames = {"problem_id", "ordinal"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ProblemTestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private CodingProblem problem;

    @Column(name = "ordinal", nullable = false)
    private Integer ordinal;

    @Column(name = "input", nullable = false, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String input;

    @Column(name = "expected_output", nullable = false, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String expectedOutput;

    @Column(name = "is_sample", nullable = false)
    @Builder.Default
    private boolean sample = false;

    @Column(name = "weight", nullable = false)
    @Builder.Default
    private Integer weight = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
