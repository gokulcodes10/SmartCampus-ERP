package smartcampus.entity;

/**
 * The audience targeting of an announcement. Each value determines which users
 * are recipients when the announcement is fanned out into notification rows.
 *
 * <p>Stored as the string name (not the ordinal) in {@code announcements.audience}.
 * {@code DEPARTMENT} announcements MUST carry a non-null {@code department_id};
 * all other audiences MUST have a null {@code department_id} (enforced by a CHECK
 * constraint in the schema).
 */
public enum AnnouncementAudience {
    ALL,
    STUDENTS,
    FACULTY,
    DEPARTMENT
}
