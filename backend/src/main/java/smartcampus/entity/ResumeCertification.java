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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One certification entry within a resume (§37).
 *
 * <p>Maps exactly to the {@code resume_certifications} table created by
 * {@code V9__resume.sql}. {@code spring.jpa.hibernate.ddl-auto=validate} refuses to
 * boot on any drift between this mapping and that migration (PROJECT_PLAN.md
 * clarification G8).
 *
 * <p>{@link #expiryDate} is nullable — NULL means "does not expire", which is the common
 * case, so it carries no separate boolean flag. A flag would immediately allow
 * disagreement with the date.
 */
@Entity
@Table(name = "resume_certifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ResumeCertification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "issuer", nullable = true, length = 200)
    private String issuer;

    @Column(name = "issue_date", nullable = true)
    private LocalDate issueDate;

    @Column(name = "expiry_date", nullable = true)
    private LocalDate expiryDate;

    @Column(name = "credential_id", nullable = true, length = 120)
    private String credentialId;

    @Column(name = "credential_url", nullable = true, length = 255)
    private String credentialUrl;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
