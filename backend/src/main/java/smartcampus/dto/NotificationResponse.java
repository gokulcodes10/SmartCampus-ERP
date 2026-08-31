package smartcampus.dto;

import java.time.LocalDateTime;
import smartcampus.entity.Notification;
import smartcampus.entity.NotificationPriority;
import smartcampus.entity.NotificationReferenceType;
import smartcampus.entity.NotificationType;

/**
 * Response representation of a {@link Notification}. FLAT — the read state is
 * computed as a derived {@code read} boolean from {@code readAt}, and the
 * announcement (if present) is denormalized into an {@code announcementId} scalar.
 *
 * <p><strong>CRITICAL:</strong> The {@code from(Notification)} factory must NEVER
 * call {@code n.getUser()} and must NEVER dereference the announcement beyond
 * {@code getId()} — calling {@code getId()} on a lazy proxy does not trigger a
 * SELECT, but {@code getTitle()} does. The announcement ID is read as:
 * {@code n.getAnnouncement() == null ? null : n.getAnnouncement().getId()}.
 */
public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        NotificationPriority priority,
        String link,
        NotificationReferenceType referenceType,
        Long referenceId,
        Long announcementId,
        boolean read,
        LocalDateTime readAt,
        LocalDateTime createdAt) {

    /** Converts a {@link Notification} entity to its response DTO. */
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getMessage(),
                n.getPriority(),
                n.getLink(),
                n.getReferenceType(),
                n.getReferenceId(),
                n.getAnnouncement() == null ? null : n.getAnnouncement().getId(),
                n.getReadAt() != null,
                n.getReadAt(),
                n.getCreatedAt());
    }
}
