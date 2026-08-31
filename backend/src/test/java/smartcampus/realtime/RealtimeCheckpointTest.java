package smartcampus.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import smartcampus.TestcontainersConfiguration;
import smartcampus.entity.Role;
import smartcampus.entity.User;
import smartcampus.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 11 (Real-Time) full-phase checkpoint: a STUDENT logs in over real HTTP,
 * opens a real WebSocket with the returned token, and — with NO further HTTP call
 * from the student — receives a live {@code NOTIFICATION} envelope the moment an
 * ADMIN posts an {@code ALL}-audience announcement over real HTTP. That absence of
 * a second student-side request IS "without a page refresh": everything the student
 * sees after connecting arrives unprompted, over the socket that {@code
 * NotificationWebSocketHandler} / {@code NotificationSocketRegistry} keep addressed
 * only by the userId resolved once at handshake time.
 *
 * <p>Depends on {@code POST /api/announcements}, built by another Phase 11 agent to
 * the same contract — if that endpoint is not on disk yet, this test will not compile.
 * See this agent's final report.
 *
 * <p>Follows the fixture idiom of {@code InterviewSchedulingCheckpointTest}: a
 * per-class {@code PREFIX} tag on every email this class creates.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RealtimeCheckpointTest {

    @LocalServerPort private int port;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String RAW_PASSWORD = "CheckpointPass1!";
    // "RTC" tags every fixture email this class creates, distinguishing them from any
    // sibling checkpoint test class sharing the cached Spring context / MySQL instance.
    private static final String PREFIX = "RTC";

    private static String tag() {
        return PREFIX + SEQUENCE.incrementAndGet();
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private User persistAdmin() {
        String t = tag();
        return userRepository.save(
                User.builder()
                        .email(t.toLowerCase() + "@example.com")
                        .password(passwordEncoder.encode(RAW_PASSWORD))
                        .fullName(t + " Admin")
                        .role(Role.ADMIN)
                        .build());
    }

    private String login(String email, String password) throws Exception {
        String body =
                mockMvc.perform(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(body).get("token").asString();
    }

    /** Registers a brand-new STUDENT over real HTTP and logs in, exactly as the UI would. */
    private String registerAndLoginStudent() throws Exception {
        String t = tag();
        String email = t.toLowerCase() + "@example.com";
        Map<String, Object> registerBody = new LinkedHashMap<>();
        registerBody.put("email", email);
        registerBody.put("password", RAW_PASSWORD);
        registerBody.put("fullName", t + " Student");

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerBody)))
                .andExpect(status().isCreated());

        return login(email, RAW_PASSWORD);
    }

    private String adminToken() throws Exception {
        User admin = persistAdmin();
        return login(admin.getEmail(), RAW_PASSWORD);
    }

    // ------------------------------------------------------------------
    // WebSocket test client
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // THE CHECKPOINT.
    // ------------------------------------------------------------------

    @Test
    void studentSeesAdminAnnouncement_liveOverTheOpenSocket_noPageRefresh() throws Exception {
        // 1. STUDENT logs in over real HTTP and opens a real socket with the returned token.
        String studentToken = registerAndLoginStudent();
        CapturingHandler handler = new CapturingHandler();
        WebSocketSession session = connect(handler, studentToken);
        try {
            assertThat(session).isNotNull();
            assertThat(session.isOpen()).isTrue();

            // Drain the initial READY frame.
            String ready = handler.messages.poll(5, TimeUnit.SECONDS);
            assertThat(ready).isNotNull();
            assertThat(json(ready).get("event").asString()).isEqualTo("READY");

            // 2. An ADMIN posts an ALL-audience announcement over real HTTP.
            String admin = adminToken();
            String announcementTitle = "Campus closed for " + tag();
            Map<String, Object> createBody = new LinkedHashMap<>();
            createBody.put("title", announcementTitle);
            createBody.put("body", "All classes are suspended until further notice.");
            createBody.put("audience", "ALL");
            createBody.put("departmentId", null);
            createBody.put("priority", "HIGH");
            createBody.put("expiresAt", LocalDateTime.now().plusDays(7).toString());

            MvcResult created =
                    mockMvc.perform(
                                    post("/api/announcements")
                                            .header("Authorization", "Bearer " + admin)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(objectMapper.writeValueAsString(createBody)))
                            .andReturn();
            assertThat(created.getResponse().getStatus())
                    .as("announcement create response: %s", created.getResponse().getContentAsString())
                    .isEqualTo(201);

            // 3. The student's ALREADY-OPEN socket receives it, with NO further HTTP call
            //    from the student — that absence IS "without a page refresh".
            String pushed = handler.messages.poll(10, TimeUnit.SECONDS);
            assertThat(pushed).as("student never received a live push for the announcement").isNotNull();

            JsonNode envelope = objectMapper.readTree(pushed);
            assertThat(envelope.get("event").asString()).isEqualTo("NOTIFICATION");
            JsonNode notification = envelope.get("notification");
            assertThat(notification.get("type").asString()).isEqualTo("ANNOUNCEMENT");
            assertThat(notification.get("title").asString()).isEqualTo(announcementTitle);
        } finally {
            if (session != null && session.isOpen()) {
                session.close(CloseStatus.NORMAL);
            }
        }
    }

    /** Parses a raw JSON payload — kept separate from {@link #json(MvcResult)} for the socket frames. */
    private JsonNode json(String payload) {
        return objectMapper.readTree(payload);
    }
}
