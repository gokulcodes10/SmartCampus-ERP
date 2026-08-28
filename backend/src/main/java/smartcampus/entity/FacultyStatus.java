package smartcampus.entity;

/**
 * The lifecycle states of a faculty account.
 *
 * <p>Stored as the string name (not the ordinal) in {@code faculty.status}.
 *
 * <p><b>ACTIVE:</b> Faculty member can teach subjects and access related operations.
 *
 * <p><b>INACTIVE:</b> Deactivated by admin, typically for leave or departure. Cannot
 * be assigned to new subjects or perform faculty operations.
 */
public enum FacultyStatus {
    ACTIVE,
    INACTIVE
}
