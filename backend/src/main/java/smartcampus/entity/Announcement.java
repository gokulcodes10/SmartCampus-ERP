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
 * An admin broadcast (§42) with audience targeting, priority and optional expiry.
 * This is the SOURCE row: it is written once and is the permanent record of what
 * was announced, by whom, to whom, and until when. The announcement is fanned out
 * into one {@link Notification} row per eligible recipient at create time.
 *
 * <p>Maps exactly to the {@code announcements} table created by
 * {@code V11__realtime.sql}. {@code spring.jpa.hibernate.ddl-auto=validate}
 * refuses to boot on any drift between this mapping and that migration
 * (PROJECT_PLAN.md clarification G8).
 *
 * <p><strong>AUDIENCE TARGETING:</strong> {@code audience} is one of four values
 * and {@code department_id} is locked to it in BOTH directions by a CHECK
 * constraint in the schema:
 * <ul>
 *   <li>{@code ALL} — every authenticated user is a recipient
 *   <li>{@code STUDENTS} — every user with role STUDENT
 *   <li>{@code FACULTY} — every user with role FACULTY
 *   <li>{@code DEPARTMENT} — every student and faculty member in that department
 * </ul>
 *
 * <p><strong>EXPIRY:</strong> {@code expiresAt} NULL means "never expires". A
 * non-NULL value must be strictly after {@code publishedAt}. Expiry hides an
 * announcement from the ACTIVE board but does NOT delete it and does NOT retract
 * notifications that were already delivered.
 */
@Entity
@Table(name = "announcements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", nullable = false, length = 20)
    private AnnouncementAudience audience;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = true)
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    @Builder.Default
    private NotificationPriority priority = NotificationPriority.NORMAL;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "expires_at", nullable = true)
    private LocalDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = true)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
