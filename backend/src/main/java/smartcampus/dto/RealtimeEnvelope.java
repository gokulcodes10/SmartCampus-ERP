package smartcampus.dto;

/**
 * The ONLY server-to-client wire shape for real-time WebSocket events. Each
 * message is exactly this JSON object serialized by Jackson, with different
 * fields populated depending on the {@code event} type.
 *
 * <p>Event types and their field usage:
 * <ul>
 *   <li>{@code READY} — sent once on connection; {@code notification} is null,
 *       {@code unreadCount} contains the initial count
 *   <li>{@code NOTIFICATION} — a new notification arrived; {@code notification}
 *       is fully populated, {@code unreadCount} is the new count
 *   <li>{@code UNREAD_COUNT} — the unread count changed (e.g., marked as read
 *       elsewhere); {@code notification} is null, {@code unreadCount} is the new
 *       count
 *   <li>{@code PONG} — response to client "ping"; both {@code notification} and
 *       {@code unreadCount} are null
 * </ul>
 */
public record RealtimeEnvelope(
        RealtimeEventType event,
        NotificationResponse notification,
        Long unreadCount) {

    /** Factory for the READY event sent on connection. */
    public static RealtimeEnvelope ready(long unreadCount) {
        return new RealtimeEnvelope(RealtimeEventType.READY, null, unreadCount);
    }

    /** Factory for the NOTIFICATION event when a new notification arrives. */
    public static RealtimeEnvelope notification(
            NotificationResponse n, long unreadCount) {
        return new RealtimeEnvelope(RealtimeEventType.NOTIFICATION, n, unreadCount);
    }

    /** Factory for the UNREAD_COUNT event when the count changes. */
    public static RealtimeEnvelope unreadCount(long unreadCount) {
        return new RealtimeEnvelope(RealtimeEventType.UNREAD_COUNT, null, unreadCount);
    }

    /** Factory for the PONG event in response to a client ping. */
    public static RealtimeEnvelope pong() {
        return new RealtimeEnvelope(RealtimeEventType.PONG, null, null);
    }
}
