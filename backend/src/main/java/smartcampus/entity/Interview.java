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
 * A scheduled interview for one student (§39). The scheduling window is half-open:
 * [scheduledStart, scheduledEnd).
 *
 * <p>Maps exactly to the {@code interviews} table created by {@code V10__interview.sql}.
 * {@code spring.jpa.hibernate.ddl-auto=validate} refuses to boot on any drift between
 * this mapping and that migration (PROJECT_PLAN.md clarification G8).
 *
 * <p><strong>CRITICAL:</strong> The {@code interviews} table has a column
 * {@code active_slot_start}, a MySQL STORED GENERATED column that backs the
 * partial-unique key {@code uk_interviews_student_active_slot (student_id,
 * active_slot_start)}. DO NOT map it on this entity. No field, no @Column, no @Formula,
 * no @Generated. Hibernate's {@code ddl-auto=validate} ignores database columns the
 * entity does not map, so leaving it unmapped is correct and safe; mapping it would make
 * every INSERT fail with "The value specified for generated column 'active_slot_start'
 * is not allowed".
 *
 * <p>Two interviews OVERLAP when
 * {@code a.scheduledStart < b.scheduledEnd AND a.scheduledEnd > b.scheduledStart}, so
 * back-to-back interviews (one ending exactly when the next begins) do NOT conflict.
 * SCHEDULED and RESCHEDULED interviews are "live" and block scheduling; COMPLETED,
 * CANCELLED and NO_SHOW are terminal.
 */
@Entity
@Table(name = "interviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Interview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_type", nullable = false, length = 20)
    private InterviewType interviewType;

    @Column(name = "company_name", nullable = true, length = 150)
    private String companyName;

    @Column(name = "round_name", nullable = true, length = 100)
    private String roundName;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 10)
    @Builder.Default
    private InterviewMode mode = InterviewMode.ONLINE;

    @Column(name = "meeting_link", nullable = true, length = 500)
    private String meetingLink;

    @Column(name = "location", nullable = true, length = 255)
    private String location;

    @Column(name = "interviewer_name", nullable = true, length = 150)
    private String interviewerName;

    @Column(name = "scheduled_start", nullable = false)
    private LocalDateTime scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private LocalDateTime scheduledEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private InterviewStatus status = InterviewStatus.SCHEDULED;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = true, length = 20)
    private InterviewOutcome outcome;

    @Column(name = "feedback", nullable = true, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String feedback;

    @Column(name = "notes", nullable = true, columnDefinition = "MEDIUMTEXT")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String notes;

    @Column(name = "cancellation_reason", nullable = true, length = 500)
    private String cancellationReason;

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
