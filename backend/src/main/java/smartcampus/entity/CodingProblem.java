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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * An authored coding problem statement (§30) with its Judge0 execution limits.
 *
 * <p>Maps exactly to the {@code coding_problems} table created by {@code V7__coding.sql}.
 * {@code spring.jpa.hibernate.ddl-auto=validate} refuses to boot on any drift between
 * this mapping and that migration (PROJECT_PLAN.md clarification G8).
 *
 * <p>Authoring is ADMIN-only (README "For Administrators": coding contest creation and
 * problem authoring). There is deliberately no per-problem language allowlist column -
 * every problem accepts both {@link ProgrammingLanguage#JAVA} and {@link
 * ProgrammingLanguage#CPP}.
 *
 * <p>No {@code @OneToMany} to {@link ProblemTestCase}: {@code spring.jpa.open-in-view}
 * is false, so a lazy collection touched outside a transaction throws at serialization
 * time. Test cases are read through {@code ProblemTestCaseRepository}.
 */
@Entity
@Table(
    name = "coding_problems",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_coding_problems_slug", columnNames = {"slug"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class CodingProblem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slug", nullable = false, length = 120, unique = true)
    private String slug;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String description;

    @Column(name = "input_format", nullable = true, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String inputFormat;

    @Column(name = "output_format", nullable = true, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String outputFormat;

    @Column(name = "constraints_text", nullable = true, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String constraintsText;

    @Column(name = "sample_input", nullable = true, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String sampleInput;

    @Column(name = "sample_output", nullable = true, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String sampleOutput;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false, length = 10)
    private ProblemDifficulty difficulty;

    @Column(name = "time_limit_ms", nullable = false)
    @Builder.Default
    private Integer timeLimitMs = 2000;

    @Column(name = "memory_limit_kb", nullable = false)
    @Builder.Default
    private Integer memoryLimitKb = 262144;

    @Column(name = "tags", length = 255)
    private String tags;

    @Column(name = "published", nullable = false)
    @Builder.Default
    private boolean published = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
