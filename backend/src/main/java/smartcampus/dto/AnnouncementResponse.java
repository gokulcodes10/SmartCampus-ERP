package smartcampus.dto;

import java.time.LocalDateTime;
import smartcampus.entity.AnnouncementAudience;
import smartcampus.entity.NotificationPriority;

/**
 * Response representation of an announcement. FLAT — the referenced department
 * and author are denormalized into scalars (departmentId/departmentName and
 * createdById/createdByName), never nested objects. The {@code active} field is
 * computed from {@code publishedAt} and {@code expiresAt}. The
 * {@code recipientCount} is populated only for ADMIN callers and is null
 * otherwise.
 */
public record AnnouncementResponse(
        Long id,
        String title,
        String body,
        AnnouncementAudience audience,
        Long departmentId,
        String departmentName,
        NotificationPriority priority,
        LocalDateTime publishedAt,
        LocalDateTime expiresAt,
        boolean active,
        Long createdById,
        String createdByName,
        Long recipientCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
