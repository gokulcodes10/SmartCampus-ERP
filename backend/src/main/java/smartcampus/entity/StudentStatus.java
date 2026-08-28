package smartcampus.entity;

/**
 * The lifecycle states of a student account.
 *
 * <p>Stored as the string name (not the ordinal) in {@code students.status}.
 *
 * <p><b>PENDING:</b> Self-registered but not yet activated by an admin. Register number,
 * department, course and current_semester are all NULL. See PROJECT_PLAN.md clarification
 * G1 and V3__academic.sql.
 *
 * <p><b>ACTIVE:</b> Activated by admin with all four required fields set. Can be promoted to
 * INACTIVE (deactivation via soft delete).
 *
 * <p><b>INACTIVE:</b> Deactivated by admin, typically for graduation or withdrawal.
 */
public enum StudentStatus {
    PENDING,
    ACTIVE,
    INACTIVE
}
