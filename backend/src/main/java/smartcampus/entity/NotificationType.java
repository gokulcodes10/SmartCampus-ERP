package smartcampus.entity;

/**
 * The type of notification event, corresponding to the business event that
 * triggered it. Each value is produced by a real source in the application — there
 * is no placeholder member.
 *
 * <p>Stored as the string name (not the ordinal) in {@code notifications.type}.
 */
public enum NotificationType {
    ANNOUNCEMENT,
    PLACEMENT_UPDATE,
    APPLICATION_UPDATE,
    INTERVIEW_UPDATE,
    CONTEST_UPDATE,
    LEADERBOARD_UPDATE,
    ATTENDANCE_WARNING
}
