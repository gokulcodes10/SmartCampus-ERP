package smartcampus.entity;

/**
 * The authoring and publication lifecycle of a placement drive.
 *
 * <p>Stored as the string name (not the ordinal) in {@code jobs.status}.
 *
 * <p><b>DRAFT:</b> Being authored. Invisible to students (returns 404 to non-admin requests).
 *
 * <p><b>OPEN:</b> Published and accepting applications. The only status in which students
 * may apply.
 *
 * <p><b>CLOSED:</b> Published but no longer accepting applications. Remains visible to students
 * so applications they made are not orphaned.
 *
 * <p><b>CANCELLED:</b> The drive was called off. Invisible to students, like DRAFT.
 */
public enum JobStatus {
    DRAFT,
    OPEN,
    CLOSED,
    CANCELLED
}
