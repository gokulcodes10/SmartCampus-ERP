package smartcampus.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import smartcampus.dto.NotificationResponse;
import smartcampus.dto.RealtimeEnvelope;
import tools.jackson.databind.ObjectMapper;

/**
 * Best-effort live push on top of the durable notification row.
 *
 * <p>{@code smartcampus.service.NotificationService} calls this ONLY after the
 * enclosing transaction has committed (see its after-commit-push javadoc) - a push for
 * a row a later rollback undoes would show the client a notification that the database
 * never actually recorded. Every send here is wrapped in {@code try/catch(Exception)}
 * and logged at DEBUG: a dead socket must never let an exception escape into the
 * caller.
 */
@Component
public class NotificationPushService {

    private static final Logger log = LoggerFactory.getLogger(NotificationPushService.class);

    private final NotificationSocketRegistry registry;
    private final ObjectMapper objectMapper;

    public NotificationPushService(NotificationSocketRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    public void pushNotification(Long userId, NotificationResponse notification, long unreadCount) {
        broadcast(userId, RealtimeEnvelope.notification(notification, unreadCount));
    }

    public void pushUnreadCount(Long userId, long unreadCount) {
        broadcast(userId, RealtimeEnvelope.unreadCount(unreadCount));
    }

    public boolean isOnline(Long userId) {
        return registry.isOnline(userId);
    }

    private void broadcast(Long userId, RealtimeEnvelope envelope) {
        var sessions = registry.sessionsFor(userId);
        if (sessions.isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(envelope);
        } catch (Exception ex) {
            log.debug("Failed to serialise realtime envelope for userId={}: {}", userId, ex.getMessage());
            return;
        }
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            } catch (Exception ex) {
                log.debug("Failed to push to userId={}: {}", userId, ex.getMessage());
            }
        }
    }
}
