package smartcampus.realtime;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * The single place a {@code userId} maps to the set of live sockets currently open for
 * that user. Nothing else in this module is allowed to hold its own copy of that
 * mapping.
 *
 * <p>Values are {@link CopyOnWriteArraySet}s so concurrent register/unregister/iterate
 * calls (a push racing a disconnect) never throw {@code ConcurrentModificationException}.
 * A userId's entry is removed entirely once its session set becomes empty so this map
 * never grows without bound as users come and go.
 */
@Component
public class NotificationSocketRegistry {

    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessionsByUserId =
            new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        sessionsByUserId
                .computeIfAbsent(userId, id -> new CopyOnWriteArraySet<>())
                .add(session);
    }

    public void unregister(Long userId, WebSocketSession session) {
        sessionsByUserId.computeIfPresent(
                userId,
                (id, sessions) -> {
                    sessions.remove(session);
                    return sessions.isEmpty() ? null : sessions;
                });
    }

    /** Never null; possibly empty. */
    public Set<WebSocketSession> sessionsFor(Long userId) {
        Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
        return sessions == null ? Collections.emptySet() : sessions;
    }

    public int sessionCount(Long userId) {
        return sessionsFor(userId).size();
    }

    public boolean isOnline(Long userId) {
        return sessionCount(userId) > 0;
    }
}
