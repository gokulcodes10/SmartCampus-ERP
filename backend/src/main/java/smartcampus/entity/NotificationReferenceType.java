package smartcampus.entity;

/**
 * The type of the row that caused a notification, used to build the {@code link}
 * field and resolve the {@code reference_id}. A notification may carry null
 * reference_type and reference_id (when the event has no specific row to link to),
 * or both must be non-null (a half-pointer is invalid by CHECK constraint).
 *
 * <p>Stored as the string name (not the ordinal) in
 * {@code notifications.reference_type}.
 */
public enum NotificationReferenceType {
    ANNOUNCEMENT,
    JOB,
    PLACEMENT_APPLICATION,
    INTERVIEW,
    CONTEST,
    SUBJECT
}
