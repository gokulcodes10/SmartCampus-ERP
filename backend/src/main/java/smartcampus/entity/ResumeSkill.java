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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One skill entry within a resume (§37).
 *
 * <p>Maps exactly to the {@code resume_skills} table created by {@code V9__resume.sql}.
 * {@code spring.jpa.hibernate.ddl-auto=validate} refuses to boot on any drift between
 * this mapping and that migration (PROJECT_PLAN.md clarification G8).
 *
 * <p><b>UNIQUENESS:</b> The unique key on (resume_id, name) is the point of this table.
 * "Java" listed twice on one resume is always a defect — it prints twice in the PDF —
 * and this is exactly what an add-form with no dedupe produces when the student scrolls
 * back up. Because the collation is utf8mb4_unicode_ci, the key also rejects 'Java' next
 * to 'java' and 'JAVA', which a Java-side {@code contains()} on raw strings would happily
 * allow through.
 *
 * <p>{@link #category} groups skills into PDF sub-headings. LANGUAGE means a spoken
 * language; programming languages are TECHNICAL.
 *
 * <p>{@link #proficiency} is NULLABLE because a student who does not want to self-rate
 * must be able to leave it out rather than be forced to assert a level they do not mean.
 * NULL prints nothing at all.
 */
@Entity
@Table(
    name = "resume_skills",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_resume_skills_resume_name", columnNames = {"resume_id", "name"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ResumeSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    @Builder.Default
    private SkillCategory category = SkillCategory.TECHNICAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "proficiency", nullable = true, length = 20)
    private SkillProficiency proficiency;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
