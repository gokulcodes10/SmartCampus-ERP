package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import smartcampus.entity.AnnouncementAudience;
import smartcampus.entity.NotificationPriority;

/**
 * Request to create an announcement. The {@code title} and {@code body} are
 * required and non-blank. The {@code departmentId} is REQUIRED only when
 * {@code audience == DEPARTMENT}, and MUST be null otherwise. The
 * {@code priority} and {@code expiresAt} are optional (default to NORMAL and
 * never expires, respectively).
 */
public record AnnouncementCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String body,
        @NotNull AnnouncementAudience audience,
        Long departmentId,
        NotificationPriority priority,
        LocalDateTime expiresAt) {}
