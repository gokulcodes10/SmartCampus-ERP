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
import java.math.BigDecimal;
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
 * A student's application to one placement drive (§35, §36), and its admin-driven
 * status lifecycle.
 *
 * <p>Maps exactly to the {@code placement_applications} table created by
 * {@code V8__placement.sql}. {@code spring.jpa.hibernate.ddl-auto=validate} refuses to
 * boot on any drift between this mapping and that migration (PROJECT_PLAN.md clarification G8).
 *
 * <p><b>DUPLICATE GUARD (§35):</b> The unique constraint on (job_id, student_id) is the
 * authoritative guard against double-application. A Java-only check is insufficient because
 * concurrent submits can both read "no row" and both insert. Service logic checks first for
 * a clean 409 response, but MUST also catch DataIntegrityViolationException on insert and
 * translate it to 409, because the check and insert are not atomic.
 *
 * <p><b>WITHDRAWAL:</b> A student-initiated WITHDRAWN status is terminal and does not delete
 * the row or free the slot. The student cannot re-apply to the same drive. This is deliberate:
 * deleting on withdrawal would destroy the record that the student was ever in the pipeline,
 * and would allow students to defeat the unique key guard at will.
 *
 * <p><b>HISTORICAL FIELDS:</b> {@link #cgpaAtApplication} and {@link #percentageAtApplication}
 * are written once at insert from the same AnalyticsService figures the eligibility engine
 * just evaluated, and are never recomputed. They are NULLABLE because a drive with no
 * CGPA/percentage criterion legitimately accepts a student with nothing graded yet. NULL
 * means "not computable at application time", never zero. They support sorting and display
 * without re-running analytics, and provide truthful answers to "why was this student accepted?"
 * six months after the fact.
 *
 * <p><b>STATUS COLUMN SIZE:</b> {@link #status} is VARCHAR(30), not VARCHAR(20), because
 * 'INTERVIEW_SCHEDULED' is 19 characters. JPA mapping MUST declare {@code length = 30} or
 * {@code ddl-auto=validate} fails at boot (G8).
 *
 * <p><b>STATUS ATTRIBUTION:</b> Every status transition MUST set {@link #status},
 * {@link #statusChangedAt}, and {@link #statusChangedBy} in the same save, or the database
 * constraint {@code chk_placement_applications_status_change_attributed} rejects the row.
 * APPLIED is the only status that allows NULL in these fields.
 *
 * <p><b>RESUME (Phase 9):</b> {@link #resume} is optional (an application may carry no
 * resume) and is guarded on the database side by a <em>composite</em> foreign key —
 * {@code fk_placement_applications_resume FOREIGN KEY (resume_id, student_id) REFERENCES
 * resumes (id, student_id)}, added by {@code V9__resume.sql}. That composite key is what
 * actually stops one student's application from ever pointing at another student's
 * resume; JPA only maps the {@code resume_id} column here (Hibernate {@code validate}
 * does not inspect foreign keys, composite or otherwise), so the service layer still
 * performs its own ownership lookup ({@code ResumeRepository#findByIdAndStudentId}) to
 * turn a cross-student attempt into a clean 404 instead of a raw constraint violation.
 * The FK is {@code RESTRICT} (no {@code ON DELETE}), so a resume once attached to an
 * application cannot be deleted while the reference stands. <b>Locking:</b> attaching a
 * resume to an application — on {@code apply} or via {@code PATCH
 * /api/applications/{id}/resume} — stamps {@code Resume#lockedAt} the first time it
 * happens (never overwritten), so the exact document a student applied with can never be
 * edited out from under an admin who is reviewing it.
 */
@Entity
@Table(
    name = "placement_applications",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_placement_applications_job_student",
            columnNames = {"job_id", "student_id"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class PlacementApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.APPLIED;

    @Column(name = "cover_note", nullable = true, length = 2000)
    private String coverNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = true)
    private Resume resume;

    @Column(name = "cgpa_at_application", nullable = true, precision = 4, scale = 2)
    private BigDecimal cgpaAtApplication;

    @Column(name = "percentage_at_application", nullable = true, precision = 5, scale = 2)
    private BigDecimal percentageAtApplication;

    @CreationTimestamp
    @Column(name = "applied_at", nullable = false, updatable = false)
    private LocalDateTime appliedAt;

    @Column(name = "status_changed_at", nullable = true)
    private LocalDateTime statusChangedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_changed_by", nullable = true)
    private User statusChangedBy;

    @Column(name = "decision_note", nullable = true, length = 500)
    private String decisionNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
