package smartcampus.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import smartcampus.TestcontainersConfiguration;
import smartcampus.dto.NotificationDispatch;
import smartcampus.entity.NotificationType;
import smartcampus.entity.Role;
import smartcampus.entity.User;
import smartcampus.repository.UserRepository;
import smartcampus.security.JwtService;
import smartcampus.service.NotificationService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Adversarial verification pass for the Phase 11 checkpoint: "a user cannot subscribe
 * to another user's notification stream." Written independently during verification
 * (not part of the original build), specifically probing subscribe-destination SHAPES
 * the existing {@code NotificationSocketSecurityTest} does not already try: a
 * wildcard-style subscribe, and a destination built from the victim's own email rather
 * than their numeric id. The protocol has no subscribe verb at all — the point of these
 * tests is to confirm that remains true under a hostile client, not just a well-behaved
 * one, and that trying anyway never crashes the socket or leaks a frame.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealtimeVerificationAdversarialTest {

    @LocalServerPort private int port;

    @Autowired private UserRepository userRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    // "RVA" tags every fixture email this class creates, distinguishing them from any
    // sibling test class sharing the cached Spring context / MySQL instance.
    private static final String PREFIX = "RVA";

    private static String tag() {
        return PREFIX + SEQUENCE.incrementAndGet();
    }

    private User persistUser() {
        String t = tag();
        return userRepository.save(
                User.builder()
                        .email(t.toLowerCase() + "@example.com")
                        .password(passwordEncoder.encode("VerifyPass1!"))
                        .fullName(t + " User")
                        .role(Role.STUDENT)
                        .enabled(true)
                        .build());
    }

    private void dispatchNotification(User user, String title) {
        notificationService.dispatch(
                NotificationDispatch.of(
                        user.getId(),
                        NotificationType.PLACEMENT_UPDATE,
                        title,
                        "adversarial test body",
                        "/student/applications",
                        null,
                        null,
                        tag()));
    }

    private static final class CapturingHandler extends TextWebSocketHandler {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.offer(message.getPayload());
        }
    }

    private WebSocketSession connect(CapturingHandler handler, String token) throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        return client.execute(handler, "ws://localhost:" + port + "/ws/notifications?token=" + token)
                .get(5, TimeUnit.SECONDS);
    }

    private JsonNode json(String payload) {
        return objectMapper.readTree(payload);
    }

    /**
     * Student A connects, then throws every shape of "subscribe to someone else" frame
     * we can think of at the socket: the raw victim user id, a wildcard-style
     * subscription, and a destination derived from the victim's email. None of them may
     * change what A receives, none may crash or close the socket, and a genuine
     * notification for the victim must still never arrive on A's connection.
     */
    @Test
    void hostileSubscribeShapes_neverLeakAnotherUsersStream_andSocketSurvives() throws Exception {
        User victim = persistUser();
        User attacker = persistUser();
        String attackerToken = jwtService.generateToken(attacker);

        CapturingHandler handler = new CapturingHandler();
        WebSocketSession session = connect(handler, attackerToken);
        try {
            assertThat(session).isNotNull();
            assertThat(session.isOpen()).isTrue();

            String ready = handler.messages.poll(5, TimeUnit.SECONDS);
            assertThat(ready).isNotNull();
            assertThat(json(ready).get("event").asString()).isEqualTo("READY");

            // Shape 1: raw victim user id as the destination.
            session.sendMessage(new TextMessage("{\"destination\":\"/user/" + victim.getId() + "/queue/notifications\"}"));
            // Shape 2: wildcard subscription attempting to catch every user's stream.
            session.sendMessage(new TextMessage("{\"subscribe\":\"/topic/notifications/*\"}"));
            session.sendMessage(new TextMessage("{\"subscribe\":\"*\"}"));
            session.sendMessage(new TextMessage("SUBSCRIBE\ndestination:/user/*/queue/notifications\n\n\0"));
            // Shape 3: destination derived from the victim's own email.
            session.sendMessage(
                    new TextMessage("{\"subscribe\":\"/user/" + victim.getEmail() + "/notifications\"}"));
            session.sendMessage(new TextMessage(victim.getEmail()));

            // None of the above should have produced any response frame at all — the
            // protocol has no subscribe verb, so every one of them is simply not "ping"
            // and must be silently dropped.
            String responseToHostileFrames = handler.messages.poll(2, TimeUnit.SECONDS);
            assertThat(responseToHostileFrames)
                    .as("no hostile subscribe-shaped frame should ever produce a response")
                    .isNull();

            // The socket must still be alive and functioning normally afterwards.
            assertThat(session.isOpen()).isTrue();
            session.sendMessage(new TextMessage("ping"));
            String pong = handler.messages.poll(5, TimeUnit.SECONDS);
            assertThat(pong).isNotNull();
            assertThat(json(pong).get("event").asString()).isEqualTo("PONG");

            // A real notification for the victim must still never reach the attacker's
            // socket, even after all of the above.
            dispatchNotification(victim, "Victim's private notification");
            String leaked = handler.messages.poll(2, TimeUnit.SECONDS);
            assertThat(leaked)
                    .as("victim's notification must never leak to the attacker's socket")
                    .isNull();

            // And the attacker's own notifications still arrive normally — proving the
            // silence above is isolation, not a broken/dead connection.
            dispatchNotification(attacker, "Attacker's own notification");
            String own = handler.messages.poll(5, TimeUnit.SECONDS);
            assertThat(own).isNotNull();
            JsonNode envelope = json(own);
            assertThat(envelope.get("event").asString()).isEqualTo("NOTIFICATION");
            assertThat(envelope.get("notification").get("title").asString())
                    .isEqualTo("Attacker's own notification");
        } finally {
            if (session != null && session.isOpen()) {
                session.close(CloseStatus.NORMAL);
            }
        }
    }
}
