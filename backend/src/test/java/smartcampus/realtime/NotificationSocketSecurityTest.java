package smartcampus.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import smartcampus.TestcontainersConfiguration;
import smartcampus.dto.NotificationDispatch;
import smartcampus.entity.Notification;
import smartcampus.entity.NotificationType;
import smartcampus.entity.Role;
import smartcampus.entity.User;
import smartcampus.repository.NotificationRepository;
import smartcampus.repository.UserRepository;
import smartcampus.security.JwtService;
import smartcampus.service.NotificationService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 11 (Real-Time) security checkpoint: "a user cannot subscribe to another user's
 * notification stream." Real socket, against a real random port, over the raw
 * {@code /ws/notifications} transport — no STOMP, no client-supplied destination
 * anywhere, so cross-user delivery can only happen if {@link
 * NotificationWebSocketHandler} or {@link NotificationSocketRegistry} address a push by
 * something other than the userId resolved once at handshake time.
 *
 * <p>Follows the fixture idiom of {@code InterviewSchedulingCheckpointTest}: every
 * fixture email/code carries a per-class {@code PREFIX} tag so sibling checkpoint test
 * classes sharing the cached context / MySQL instance never collide.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationSocketSecurityTest {

    @LocalServerPort private int port;

    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;

    @Value("${smartcampus.jwt.secret}")
    private String configuredJwtSecret;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    // "NSS" tags every fixture email this class creates, distinguishing them from any
    // sibling checkpoint test class sharing the cached Spring context / MySQL instance.
    private static final String PREFIX = "NSS";

    private static String tag() {
        return PREFIX + SEQUENCE.incrementAndGet();
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private User persistUser(boolean enabled) {
        String t = tag();
        return userRepository.save(
                User.builder()
                        .email(t.toLowerCase() + "@example.com")
                        .password(passwordEncoder.encode("CheckpointPass1!"))
                        .fullName(t + " User")
                        .role(Role.STUDENT)
                        .enabled(enabled)
                        .build());
    }

    /** Writes a durable row directly — used only to seed unread-count state before connecting. */
    private Notification persistNotification(User user, String title) {
        return notificationRepository.save(
                Notification.builder()
                        .user(user)
                        .type(NotificationType.PLACEMENT_UPDATE)
                        .title(title)
                        .message("test message body")
                        .link("/student/applications")
                        .build());
    }

    /**
     * Dispatches through the real service so the after-commit push actually fires — a
     * direct repository save bypasses {@code NotificationPushService} entirely, which
     * would make "does a live push leak across users" untestable.
     */
    private void dispatchNotification(User user, String title) {
        notificationService.dispatch(
                NotificationDispatch.of(
                        user.getId(),
                        NotificationType.PLACEMENT_UPDATE,
                        title,
                        "test message body",
                        "/student/applications",
                        null,
                        null,
                        tag()));
    }

    private String wsUrl(String token) {
        return "ws://localhost:" + port + "/ws/notifications?token=" + token;
    }

    /** Forges a token signed with the app's real configured secret, expired long ago. */
    private String expiredToken(String email) {
        SecretKey key = Keys.hmacShaKeyFor(configuredJwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant issuedAt = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant expiry = Instant.now().minus(1, ChronoUnit.DAYS);
        return Jwts.builder()
                .subject(email)
                .claim("role", Role.STUDENT.name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiry))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Forges a token for {@code email}, correctly formed but signed with a DIFFERENT key. */
    private String forgedToken(String email) {
        SecretKey wrongKey =
                Keys.hmacShaKeyFor(
                        "a-completely-different-signing-key-not-the-real-one-32b".getBytes(
                                StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(email)
                .claim("role", Role.STUDENT.name())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                .signWith(wrongKey, Jwts.SIG.HS256)
                .compact();
    }

    // ------------------------------------------------------------------
    // Test client
    // ------------------------------------------------------------------

    /** Captures every inbound text frame on a queue for the test to assert against. */
    private static final class CapturingHandler extends TextWebSocketHandler {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.offer(message.getPayload());
        }
    }

    private JsonNode json(String payload) {
        return objectMapper.readTree(payload);
    }

    /** Attempts a handshake; returns the open session, or null if the handshake was rejected. */
    private WebSocketSession tryConnect(WebSocketHandler handler, String token) {
        StandardWebSocketClient client = new StandardWebSocketClient();
        try {
            return client.execute(handler, wsUrl(token)).get(5, TimeUnit.SECONDS);
        } catch (Exception ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // (1) Handshake rejection cases.
    // ------------------------------------------------------------------

    @Test
    void noToken_handshakeFails() {
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = null;
        try {
            session =
                    client.execute(
                                    new CapturingHandler(),
                                    "ws://localhost:" + port + "/ws/notifications")
                            .get(5, TimeUnit.SECONDS);
        } catch (Exception expected) {
            // handshake rejected — expected
        }
        assertThat(session).isNull();
    }

    @Test
    void garbageToken_handshakeFails() {
        WebSocketSession session = tryConnect(new CapturingHandler(), "this-is-not-a-jwt");
        assertThat(session).isNull();
    }

    @Test
    void tokenSignedWithDifferentKey_handshakeFails() {
        User user = persistUser(true);
        WebSocketSession session = tryConnect(new CapturingHandler(), forgedToken(user.getEmail()));
        assertThat(session).isNull();
    }

    @Test
    void expiredToken_handshakeFails() {
        User user = persistUser(true);
        WebSocketSession session = tryConnect(new CapturingHandler(), expiredToken(user.getEmail()));
        assertThat(session).isNull();
    }

    @Test
    void disabledUser_handshakeFails() {
        User user = persistUser(false);
        String token = jwtService.generateToken(user);
        WebSocketSession session = tryConnect(new CapturingHandler(), token);
        assertThat(session).isNull();
    }

    // ------------------------------------------------------------------
    // (2) Valid token connects and receives its own READY frame.
    // ------------------------------------------------------------------

    @Test
    void validToken_connectsAndReceivesOwnReadyFrame() throws Exception {
        User user = persistUser(true);
        persistNotification(user, "Unread before connect");
        String token = jwtService.generateToken(user);

        CapturingHandler handler = new CapturingHandler();
        WebSocketSession session = tryConnect(handler, token);
        try {
            assertThat(session).isNotNull();
            assertThat(session.isOpen()).isTrue();

            String first = handler.messages.poll(5, TimeUnit.SECONDS);
            assertThat(first).isNotNull();
            JsonNode envelope = json(first);
            assertThat(envelope.get("event").asString()).isEqualTo("READY");
            assertThat(envelope.get("unreadCount").asLong()).isEqualTo(1L);
        } finally {
            closeQuietly(session);
        }
    }

    // ------------------------------------------------------------------
    // (3) THE CHECKPOINT: cross-user isolation.
    // ------------------------------------------------------------------

    @Test
    void userA_neverReceivesUserBsNotification_andHostileFramesDoNotLeakIt() throws Exception {
        User userA = persistUser(true);
        User userB = persistUser(true);
        String tokenA = jwtService.generateToken(userA);

        CapturingHandler handlerA = new CapturingHandler();
        WebSocketSession sessionA = tryConnect(handlerA, tokenA);
        try {
            assertThat(sessionA).isNotNull();
            // Drain the initial READY frame.
            String ready = handlerA.messages.poll(5, TimeUnit.SECONDS);
            assertThat(ready).isNotNull();
            assertThat(json(ready).get("event").asString()).isEqualTo("READY");

            // A notification for B must never reach A's socket.
            dispatchNotification(userB, "For B only");
            String nothingForA = handlerA.messages.poll(2, TimeUnit.SECONDS);
            assertThat(nothingForA)
                    .as("A must receive nothing when a notification is written for B")
                    .isNull();

            // Hostile inbound frames naming B do not change what A receives afterwards.
            sessionA.sendMessage(new TextMessage("{\"subscribe\":\"user:" + userB.getId() + "\"}"));
            sessionA.sendMessage(new TextMessage(String.valueOf(userB.getId())));
            String stillNothing = handlerA.messages.poll(2, TimeUnit.SECONDS);
            assertThat(stillNothing)
                    .as("hostile inbound frames naming B must produce no response and no leak")
                    .isNull();

            // A's own "ping" still gets exactly a PONG — proving the socket is alive and the
            // silence above was isolation, not a dead connection.
            sessionA.sendMessage(new TextMessage("ping"));
            String pong = handlerA.messages.poll(5, TimeUnit.SECONDS);
            assertThat(pong).isNotNull();
            assertThat(json(pong).get("event").asString()).isEqualTo("PONG");

            // A notification for A DOES arrive.
            dispatchNotification(userA, "For A");
            String forA = handlerA.messages.poll(5, TimeUnit.SECONDS);
            assertThat(forA).isNotNull();
            JsonNode envelope = json(forA);
            assertThat(envelope.get("event").asString()).isEqualTo("NOTIFICATION");
            assertThat(envelope.get("notification").get("title").asString()).isEqualTo("For A");
        } finally {
            closeQuietly(sessionA);
        }
    }

    // ------------------------------------------------------------------

    private void closeQuietly(WebSocketSession session) {
        if (session != null && session.isOpen()) {
            try {
                session.close(CloseStatus.NORMAL);
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
    }
}
