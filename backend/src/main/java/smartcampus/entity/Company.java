package smartcampus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * A recruiting organisation that posts placement drives (§33).
 *
 * <p>Maps exactly to the {@code companies} table created by {@code V8__placement.sql}.
 * {@code spring.jpa.hibernate.ddl-auto=validate} refuses to boot on any drift between
 * this mapping and that migration (PROJECT_PLAN.md clarification G8).
 *
 * <p>{@link #description} is MEDIUMTEXT, not TEXT, and MUST carry both {@code
 * columnDefinition = "MEDIUMTEXT"} and {@link JdbcTypeCode @JdbcTypeCode(SqlTypes.LONGVARCHAR)}
 * — exactly as {@code AIMessage.content} does — or {@code ddl-auto=validate} rejects the
 * mapping at boot (G8).
 *
 * <p>{@link #status} is a soft-delete/visibility flag: INACTIVE means the company no
 * longer recruits, but its historical drives and applications remain for the record.
 * CompanyService refuses a DELETE (409) when any jobs reference this company; deactivation
 * is offered instead.
 */
@Entity
@Table(
    name = "companies",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_companies_name", columnNames = {"name"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 150)
    private String name;

    @Column(name = "industry", nullable = true, length = 100)
    private String industry;

    @Column(name = "website", nullable = true, length = 255)
    private String website;

    @Column(name = "description", nullable = true, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String description;

    @Column(name = "location", nullable = true, length = 150)
    private String location;

    @Column(name = "contact_person", nullable = true, length = 120)
    private String contactPerson;

    @Column(name = "contact_email", nullable = true, length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = true, length = 20)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CompanyStatus status = CompanyStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
