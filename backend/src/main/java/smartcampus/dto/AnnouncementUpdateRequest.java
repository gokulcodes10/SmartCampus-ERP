package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import smartcampus.entity.NotificationPriority;

/**
 * Request to update an announcement. Updates the {@code title}, {@code body},
 * {@code priority}, and {@code expiresAt} only. DELIBERATELY carries NO
 * {@code audience} and NO {@code departmentId} — the fan-out already happened
 * at create time; re-targeting would strand notifications with the wrong
 * recipients and leave new recipients with none. Re-targeting requires delete +
 * recreate.
 *
 * <p>If a client sends {@code audience} or {@code departmentId}, Jackson silently
 * drops them.
 */
public record AnnouncementUpdateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String body,
        NotificationPriority priority,
        LocalDateTime expiresAt) {}
