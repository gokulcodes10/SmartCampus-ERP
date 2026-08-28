package smartcampus.entity;

/**
 * The states of a student's enrollment in a subject.
 *
 * <p>Stored as the string name (not the ordinal) in {@code enrollments.status}.
 *
 * <p><b>ACTIVE:</b> Student is currently enrolled and attending the subject.
 *
 * <p><b>COMPLETED:</b> Student completed the subject (semester ended, marks recorded).
 *
 * <p><b>DROPPED:</b> Student withdrew from the subject during the semester.
 */
public enum EnrollmentStatus {
    ACTIVE,
    COMPLETED,
    DROPPED
}
