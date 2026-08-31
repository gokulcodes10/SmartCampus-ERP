package smartcampus.entity;

/**
 * The pipeline state of a student's application to a placement drive.
 *
 * <p>Stored as the string name (not the ordinal) in {@code placement_applications.status}.
 * Note: this column is VARCHAR(30), not VARCHAR(20), because 'INTERVIEW_SCHEDULED' is 19
 * characters and leaves minimal headroom. JPA entity mappings MUST declare {@code length = 30}.
 *
 * <p><b>APPLIED:</b> The student applied. The only status a row may be inserted with.
 *
 * <p><b>UNDER_REVIEW:</b> The placement cell is screening.
 *
 * <p><b>SHORTLISTED:</b> The student has been shortlisted for further evaluation.
 *
 * <p><b>INTERVIEW_SCHEDULED:</b> The student has been called for an interview. Phase 10
 * (Interviews) links this to a real Interview row.
 *
 * <p><b>SELECTED:</b> Terminal. The student has been selected for the position.
 *
 * <p><b>REJECTED:</b> Terminal. The student's application was rejected.
 *
 * <p><b>WITHDRAWN:</b> Terminal and student-initiated. The student withdrew their application.
 * Withdrawal does not free the slot; the student cannot re-apply to the same drive.
 */
public enum ApplicationStatus {
    APPLIED,
    UNDER_REVIEW,
    SHORTLISTED,
    INTERVIEW_SCHEDULED,
    SELECTED,
    REJECTED,
    WITHDRAWN
}
