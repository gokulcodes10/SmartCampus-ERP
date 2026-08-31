package smartcampus.dto;

import smartcampus.entity.NotificationPriority;
import smartcampus.entity.NotificationReferenceType;
import smartcampus.entity.NotificationType;

/**
 * Internal command that every notification producer uses to dispatch a
 * notification to one user. Each field corresponds to a column in the
 * {@code notifications} table. Validation and deduplication happen in the
 * service layer, not here.
 *
 * <p>Use the convenience factory {@link #of(Long, NotificationType, String,
 * String, String, NotificationReferenceType, Long, String)} to create a
 * dispatch with NORMAL priority and no announcement ID.
 */
public record NotificationDispatch(
        Long userId,
        NotificationType type,
        String title,
        String message,
        NotificationPriority priority,
        String link,
        NotificationReferenceType referenceType,
        Long referenceId,
        Long announcementId,
        String dedupeKey) {

    /**
     * Convenience factory for dispatches with NORMAL priority and no announcement
     * link (used by non-announcement producers).
     */
    public static NotificationDispatch of(
            Long userId,
            NotificationType type,
            String title,
            String message,
            String link,
            NotificationReferenceType referenceType,
            Long referenceId,
            String dedupeKey) {
        return new NotificationDispatch(
                userId, type, title, message, NotificationPriority.NORMAL, link,
                referenceType, referenceId, null, dedupeKey);
    }
}
