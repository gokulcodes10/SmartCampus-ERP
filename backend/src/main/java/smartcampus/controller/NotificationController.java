package smartcampus.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.DeleteAllNotificationsResponse;
import smartcampus.dto.MarkAllReadResponse;
import smartcampus.dto.NotificationResponse;
import smartcampus.dto.PageResponse;
import smartcampus.dto.UnreadCountResponse;
import smartcampus.entity.NotificationType;
import smartcampus.entity.User;
import smartcampus.service.NotificationService;

/**
 * {@code /api/notifications} — §40/§41 notification centre. Every route is implicitly
 * scoped to the caller's own rows; there is no {@code userId} request parameter
 * anywhere on this controller, and no cross-user read path even for an ADMIN. Method
 * security is not enabled on this build; ownership enforcement lives entirely in
 * {@link NotificationService}, which always queries by {@code caller.getId()}.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) NotificationType type,
            @AuthenticationPrincipal User caller,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return notificationService.list(caller, unreadOnly, type, pageable);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal User caller) {
        return notificationService.unreadCount(caller);
    }

    // Declared before {id}/read to match the Phase 7/10 convention, even though the
    // patterns do not actually collide.
    @PutMapping("/read-all")
    public MarkAllReadResponse markAllRead(@AuthenticationPrincipal User caller) {
        return notificationService.markAllRead(caller);
    }

    /** 404 (never 403) for a notification not owned by the caller — ids must not be probeable. */
    @PutMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        return notificationService.markRead(id, caller);
    }

    /** 404 (never 403) for a notification not owned by the caller. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        notificationService.delete(id, caller);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public DeleteAllNotificationsResponse deleteAll(@AuthenticationPrincipal User caller) {
        return notificationService.deleteAll(caller);
    }
}
