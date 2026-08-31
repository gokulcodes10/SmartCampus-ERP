package smartcampus.entity;

/**
 * The priority level of a notification or announcement, used by the client to
 * style and sort notifications.
 *
 * <p>Stored as the string name (not the ordinal) in {@code notifications.priority}
 * and {@code announcements.priority}.
 */
public enum NotificationPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}
