package smartcampus.realtime;

import java.util.Iterator;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import smartcampus.dto.RealtimeEnvelope;
import smartcampus.repository.NotificationRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * The {@code /ws/notifications} endpoint handler.
 *
 * <p>Depends on {@link NotificationRepository} directly, NOT on {@code
 * NotificationService} - this keeps the realtime module free of any dependency on
 * another agent's service layer and avoids a bean wiring cycle.
 *
 * <p>The ONLY inbound frame this handler ever acts on is the literal text {@code
 * "ping"}. Every other frame - including one that tries to name another user, a
 * "subscribe" command, or anything else - is silently ignored: not parsed, not acted
 * on, not echoed. There is no client command anywhere in this protocol that selects a
 * user, a stream, or a filter; the userId a connection pushes to is fixed once, at
 * handshake time, and is never read from an inbound frame.
 *
 * <p>The container invokes every lifecycle callback below with the same underlying
 * {@link WebSocketSession} instance for a given connection, so that raw session's
 * {@code id} is a stable key for finding "the decorator wrapping THIS connection"
 * inside {@link NotificationSocketRegistry#sessionsFor(Long)} without ever trusting
 * anything the client sent.
 */
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketHandler.class);

    private static final String PING = "ping";

    /** Buffer/limit tuning for {@link ConcurrentWebSocketSessionDecorator}. */
    private static final int SEND_TIME_LIMIT_MILLIS = 10_000;

    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    private final NotificationSocketRegistry registry;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    @Value("${smartcampus.realtime.max-sessions-per-user:5}")
    private int maxSessionsPerUser;

    public NotificationWebSocketHandler(
            NotificationSocketRegistry registry,
            NotificationRepository notificationRepository,
            ObjectMapper objectMapper) {
        this.registry = registry;
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = userIdOf(session);
        if (userId == null) {
            // Defence in depth: JwtHandshakeInterceptor already guarantees this attribute
            // is present for any session that reaches here.
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }

        // sendMessage is not thread-safe; every send from here on goes through this
        // decorator, and the decorator - never the raw session - is what gets stored.
        WebSocketSession concurrentSession =
                new ConcurrentWebSocketSessionDecorator(
                        session, SEND_TIME_LIMIT_MILLIS, BUFFER_SIZE_LIMIT_BYTES);

        enforceSessionCap(userId);
        registry.register(userId, concurrentSession);

        long unreadCount = notificationRepository.countByUserIdAndReadAtIsNull(userId);
        send(concurrentSession, RealtimeEnvelope.ready(unreadCount));
        log.debug("WebSocket connected for userId={}", userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if (!PING.equals(message.getPayload())) {
            // Everything else - including a hostile frame naming another user - is
            // silently ignored. No parsing, no action, no echo.
            return;
        }
        WebSocketSession target = findRegisteredSession(session);
        if (target != null) {
            send(target, RealtimeEnvelope.pong());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = userIdOf(session);
        if (userId == null) {
            return;
        }
        WebSocketSession registered = findRegisteredSession(session);
        registry.unregister(userId, registered != null ? registered : session);
        log.debug("WebSocket closed for userId={} status={}", userId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long userId = userIdOf(session);
        if (userId != null) {
            WebSocketSession registered = findRegisteredSession(session);
            registry.unregister(userId, registered != null ? registered : session);
        }
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    // ------------------------------------------------------------------

    private void enforceSessionCap(Long userId) {
        Set<WebSocketSession> existing = registry.sessionsFor(userId);
        if (existing.size() < maxSessionsPerUser) {
            return;
        }
        Iterator<WebSocketSession> it = existing.iterator();
        if (it.hasNext()) {
            WebSocketSession oldest = it.next();
            registry.unregister(userId, oldest);
            closeQuietly(oldest, CloseStatus.POLICY_VIOLATION);
        }
    }

    /** Finds the (decorator-wrapped) session stored in the registry for this raw session. */
    private WebSocketSession findRegisteredSession(WebSocketSession rawSession) {
        Long userId = userIdOf(rawSession);
        if (userId == null) {
            return null;
        }
        for (WebSocketSession candidate : registry.sessionsFor(userId)) {
            if (candidate.getId().equals(rawSession.getId())) {
                return candidate;
            }
        }
        return null;
    }

    private Long userIdOf(WebSocketSession session) {
        Object value = session.getAttributes().get(JwtHandshakeInterceptor.ATTR_USER_ID);
        return value instanceof Long l ? l : null;
    }

    private void send(WebSocketSession session, RealtimeEnvelope envelope) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(envelope)));
            }
        } catch (Exception ex) {
            // A dead socket must never propagate into a caller's transaction. The durable
            // notification row is the delivery guarantee; this push is best-effort.
            log.debug("Failed to send realtime envelope: {}", ex.getMessage());
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (Exception ex) {
            log.debug("Failed to close WebSocket session: {}", ex.getMessage());
        }
    }
}
