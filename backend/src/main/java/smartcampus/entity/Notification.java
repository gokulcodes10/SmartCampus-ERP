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
 * One row per (recipient user, event) — the DELIVERY row and the entire content
 * of the §40 notification centre: unread count, mark read, mark all read, delete.
 * Every notification in the system lives here, whatever produced it — an
 * announcement fan-out, a placement decision, an interview change, a contest or
 * leaderboard movement, or a low-attendance warning.
 *
 * <p>Maps exactly to the {@code notifications} table created by
 * {@code V11__realtime.sql}. {@code spring.jpa.hibernate.ddl-auto=validate}
 * refuses to boot on any drift between this mapping and that migration
 * (PROJECT_PLAN.md clarification G8).
 *
 * <p><strong>OWNERSHIP IS THE WHOLE SECURITY MODEL:</strong> Every row belongs to
 * exactly one user, and {@code user_id} is the ONLY thing that decides who may
 * read, mark or delete it. Enforcement is via database constraints: the service
 * MUST NOT "load by id then compare in Java", which leaks rows when a code path
 * forgets the comparison.
 *
 * <p><strong>READ STATE IS ONE COLUMN:</strong> {@code readAt} NULL means unread;
 * a timestamp means read. There is deliberately no companion {@code isRead}
 * boolean — two columns expressing one fact drift apart, and every unread-count
 * bug comes from them disagreeing.
 *
 * <p><strong>REFERENCE POINTER:</strong> {@code referenceType} and
 * {@code referenceId} are a SOFT pointer at the row that caused the notification,
 * used to build the {@code link} field. They are written together or not at all —
 * both null or both non-null — enforced by a CHECK constraint.
 *
 * <p><strong>IDEMPOTENCE:</strong> {@code dedupeKey} + a UNIQUE constraint on
 * {@code (user_id, dedupe_key)} makes re-running a fan-out, re-marking a roster
 * or recomputing a leaderboard idempotent. NULL dedupeKey opts out (MySQL unique
 * indexes do not compare NULLs, so any number of rows may have one).
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    @Builder.Default
    private NotificationPriority priority = NotificationPriority.NORMAL;

    @Column(name = "link", nullable = true, length = 500)
    private String link;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = true, length = 30)
    private NotificationReferenceType referenceType;

    @Column(name = "reference_id", nullable = true)
    private Long referenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "announcement_id", nullable = true)
    private Announcement announcement;

    @Column(name = "dedupe_key", nullable = true, length = 150)
    private String dedupeKey;

    @Column(name = "read_at", nullable = true)
    private LocalDateTime readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
