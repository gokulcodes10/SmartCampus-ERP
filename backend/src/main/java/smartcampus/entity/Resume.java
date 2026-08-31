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
 * One saved version of a student's resume: the header/contact block, free-text summary,
 * chosen template, and the lock that freezes a version once attached to a placement
 * application (§35, §37).
 *
 * <p>Maps exactly to the {@code resumes} table created by {@code V9__resume.sql}.
 * {@code spring.jpa.hibernate.ddl-auto=validate} refuses to boot on any drift between
 * this mapping and that migration (PROJECT_PLAN.md clarification G8).
 *
 * <p><b>MULTIPLE VERSIONS:</b> Each resume row is one independent, renderable version.
 * The student may save multiple versions with different titles and templates. Duplicate
 * title for the same student is prevented by the unique constraint on (student_id, title).
 *
 * <p><b>LOCK MECHANISM:</b> {@link #lockedAt} is NULL for versions never attached to a
 * placement application, and carries the instant of first attachment afterwards. Once set,
 * it is never cleared. Locked versions are READ-ONLY — ResumeService refuses updates and
 * deletes, and offers "duplicate" instead, which creates a fresh unlocked version.
 *
 * <p><b>NO COLLECTIONS:</b> This entity deliberately declares NO @OneToMany collections.
 * Section entries (educations, experiences, etc.) are loaded and written exclusively
 * through their own repositories to avoid Hibernate's insert-before-delete ordering trap
 * against {@code uk_resume_skills_resume_name} and to avoid LazyInitializationException
 * under {@code spring.jpa.open-in-view=false}.
 *
 * <p>{@code summary} is MEDIUMTEXT and MUST carry both {@code columnDefinition = "MEDIUMTEXT"}
 * and {@link JdbcTypeCode @JdbcTypeCode(SqlTypes.LONGVARCHAR)} — exactly as
 * {@code AIMessage.content} does — or {@code ddl-auto=validate} rejects the mapping at boot.
 */
@Entity
@Table(
    name = "resumes",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_resumes_student_title", columnNames = {"student_id", "title"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "template", nullable = false, length = 20)
    @Builder.Default
    private ResumeTemplate template = ResumeTemplate.CLASSIC;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "phone", nullable = true, length = 20)
    private String phone;

    @Column(name = "location", nullable = true, length = 150)
    private String location;

    @Column(name = "linkedin_url", nullable = true, length = 255)
    private String linkedinUrl;

    @Column(name = "github_url", nullable = true, length = 255)
    private String githubUrl;

    @Column(name = "portfolio_url", nullable = true, length = 255)
    private String portfolioUrl;

    @Column(name = "summary", nullable = true, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String summary;

    @Column(name = "locked_at", nullable = true)
    private LocalDateTime lockedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
