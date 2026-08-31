package smartcampus.dto;

/**
 * Response from marking all notifications as read. Contains the number of
 * notifications that were marked read and the new unread count.
 */
public record MarkAllReadResponse(int markedCount, long unreadCount) {}
