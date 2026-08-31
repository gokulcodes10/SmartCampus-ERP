package smartcampus.dto;

/**
 * The type of real-time event sent from server to client over the WebSocket.
 * This is wire vocabulary, not a domain enum — it appears in the JSON envelope
 * serialized to clients.
 */
public enum RealtimeEventType {
    READY,
    NOTIFICATION,
    UNREAD_COUNT,
    PONG
}
