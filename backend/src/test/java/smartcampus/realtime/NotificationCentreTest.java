package smartcampus.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import smartcampus.TestcontainersConfiguration;
import smartcampus.entity.Notification;
import smartcampus.entity.NotificationPriority;
import smartcampus.entity.NotificationType;
import smartcampus.entity.Role;
import smartcampus.entity.User;
import smartcampus.repository.NotificationRepository;
import smartcampus.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 11 (Real-Time) checkpoint slice: the §40/§41 notification centre. Real HTTP,
 * real bearer tokens, real MySQL via Testcontainers — same convention as {@code
 * InterviewSchedulingCheckpointTest}.
 *
 * <p>Every assertion here is about OWNERSHIP: a caller must never be able to read,
 * mark or delete another user's notification row, and an ADMIN gets no special
 * cross-user read path either — every list/count/mark/delete statement in {@code
 * NotificationRepository} is scoped by {@code user_id} in the WHERE clause.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class NotificationCentreTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String RAW_PASSWORD = "CheckpointPass1!";
    // "NCT" tags every fixture email this class creates, distinguishing them from any
    // sibling checkpoint test class sharing the cached Spring context / MySQL instance.
    private static final String PREFIX = "NCT";

    private static String tag() {
        return PREFIX + SEQUENCE.incrementAndGet();
    }

    // ------------------------------------------------------------------
    // Fixture builders
    // ------------------------------------------------------------------

    private User persistUser(Role role) {
        String t = tag();
        return userRepository.save(
                User.builder()
                        .email(t.toLowerCase() + "@example.com")
                        .password(passwordEncoder.encode(RAW_PASSWORD))
                        .fullName(t + " " + role.name())
                        .role(role)
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

    private String loginAsUser(User user) throws Exception {
        return login(user.getEmail(), RAW_PASSWORD);
    }

    /** Persists a notification directly (there is no HTTP producer endpoint on this module). */
    private Notification persistNotification(User user, NotificationType type, boolean read) {
        String t = tag();
        Notification n =
                Notification.builder()
                        .user(user)
                        .type(type)
                        .title(t + " title")
                        .message(t + " message")
                        .priority(NotificationPriority.NORMAL)
                        .build();
        n = notificationRepository.save(n);
        if (read) {
            n.setReadAt(LocalDateTime.now());
            n = notificationRepository.save(n);
        }
        return n;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // ------------------------------------------------------------------
    // (1) Ownership: list only returns the caller's own rows; cross-user
    //     read/mark/delete is 404 (never 403), and a denied delete does not
    //     touch the row.
    // ------------------------------------------------------------------

    @Test
    void ownership_listOnlyReturnsOwnRows_andCrossUserWriteOperationsAre404_rowSurvives() throws Exception {
        User a = persistUser(Role.STUDENT);
        User b = persistUser(Role.STUDENT);
        Notification aNotification = persistNotification(a, NotificationType.INTERVIEW_UPDATE, false);
        Notification bNotification = persistNotification(b, NotificationType.PLACEMENT_UPDATE, false);

        String tokenA = loginAsUser(a);

        // A's list contains only A's row.
        MvcResult listResult =
                mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + tokenA))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode content = json(listResult).get("content");
        assertThat(content.isArray()).isTrue();
        for (JsonNode item : content) {
            assertThat(item.get("id").asLong()).isNotEqualTo(bNotification.getId());
        }
        boolean containsA = false;
        for (JsonNode item : content) {
            if (item.get("id").asLong() == aNotification.getId()) {
                containsA = true;
            }
        }
        assertThat(containsA).isTrue();

        // A marking B's notification as read -> 404, never 403.
        mockMvc.perform(
                        put("/api/notifications/" + bNotification.getId() + "/read")
                                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        // A deleting B's notification -> 404, and the row survives.
        mockMvc.perform(
                        delete("/api/notifications/" + bNotification.getId())
                                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
        assertThat(notificationRepository.findById(bNotification.getId())).isPresent();

        // Even an ADMIN gets 404 on another user's notification — no cross-user read path.
        User admin = persistUser(Role.ADMIN);
        String adminToken = loginAsUser(admin);
        mockMvc.perform(
                        put("/api/notifications/" + bNotification.getId() + "/read")
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        // B's row is untouched — still unread.
        assertThat(notificationRepository.findById(bNotification.getId()).orElseThrow().getReadAt()).isNull();
    }

    // ------------------------------------------------------------------
    // (2) unread-count reflects only the caller's own unread rows.
    // ------------------------------------------------------------------

    @Test
    void unreadCount_reflectsOnlyCallersOwnUnreadRows() throws Exception {
        User a = persistUser(Role.STUDENT);
        User b = persistUser(Role.STUDENT);
        persistNotification(a, NotificationType.INTERVIEW_UPDATE, false);
        persistNotification(a, NotificationType.PLACEMENT_UPDATE, false);
        persistNotification(a, NotificationType.CONTEST_UPDATE, true); // already read
        persistNotification(b, NotificationType.INTERVIEW_UPDATE, false);
        persistNotification(b, NotificationType.INTERVIEW_UPDATE, false);

        String tokenA = loginAsUser(a);
        MvcResult result =
                mockMvc.perform(
                                get("/api/notifications/unread-count")
                                        .header("Authorization", "Bearer " + tokenA))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(json(result).get("unreadCount").asLong()).isEqualTo(2L);
    }

    // ------------------------------------------------------------------
    // (3) mark-read is idempotent.
    // ------------------------------------------------------------------

    @Test
    void markRead_isIdempotent_secondCallDoesNotChangeReadAtOrGoNegative() throws Exception {
        User a = persistUser(Role.STUDENT);
        Notification n = persistNotification(a, NotificationType.ATTENDANCE_WARNING, false);
        String tokenA = loginAsUser(a);

        MvcResult first =
                mockMvc.perform(
                                put("/api/notifications/" + n.getId() + "/read")
                                        .header("Authorization", "Bearer " + tokenA))
                        .andExpect(status().isOk())
                        .andReturn();
        String readAtFirst = json(first).get("readAt").asString();
        assertThat(json(first).get("read").asBoolean()).isTrue();
        assertThat(readAtFirst).isNotBlank();

        MvcResult second =
                mockMvc.perform(
                                put("/api/notifications/" + n.getId() + "/read")
                                        .header("Authorization", "Bearer " + tokenA))
                        .andExpect(status().isOk())
                        .andReturn();
        String readAtSecond = json(second).get("readAt").asString();
        assertThat(readAtSecond).isEqualTo(readAtFirst);

        MvcResult unread =
                mockMvc.perform(
                                get("/api/notifications/unread-count")
                                        .header("Authorization", "Bearer " + tokenA))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(json(unread).get("unreadCount").asLong()).isEqualTo(0L);
    }

    // ------------------------------------------------------------------
    // (4) mark-all-read returns the real markedCount and leaves unreadCount 0.
    // ------------------------------------------------------------------

    @Test
    void markAllRead_returnsRealMarkedCount_andLeavesUnreadCountZero() throws Exception {
        User a = persistUser(Role.STUDENT);
        persistNotification(a, NotificationType.INTERVIEW_UPDATE, false);
        persistNotification(a, NotificationType.PLACEMENT_UPDATE, false);
        persistNotification(a, NotificationType.CONTEST_UPDATE, false);
        persistNotification(a, NotificationType.ATTENDANCE_WARNING, true); // already read

        String tokenA = loginAsUser(a);
        MvcResult result =
                mockMvc.perform(
                                put("/api/notifications/read-all").header("Authorization", "Bearer " + tokenA))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(json(result).get("markedCount").asLong()).isEqualTo(3L);
        assertThat(json(result).get("unreadCount").asLong()).isEqualTo(0L);

        MvcResult unread =
                mockMvc.perform(
                                get("/api/notifications/unread-count")
                                        .header("Authorization", "Bearer " + tokenA))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(json(unread).get("unreadCount").asLong()).isEqualTo(0L);
    }

    // ------------------------------------------------------------------
    // (5) The ?type= and ?unreadOnly= filters really filter.
    // ------------------------------------------------------------------

    @Test
    void listFilters_typeAndUnreadOnly_reallyFilter() throws Exception {
        User a = persistUser(Role.STUDENT);
        Notification interviewUnread = persistNotification(a, NotificationType.INTERVIEW_UPDATE, false);
        persistNotification(a, NotificationType.INTERVIEW_UPDATE, true);
        Notification placementUnread = persistNotification(a, NotificationType.PLACEMENT_UPDATE, false);

        String tokenA = loginAsUser(a);

        MvcResult typeFiltered =
                mockMvc.perform(
                                get("/api/notifications")
                                        .param("type", "INTERVIEW_UPDATE")
                                        .header("Authorization", "Bearer " + tokenA))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode typeContent = json(typeFiltered).get("content");
        assertThat(typeContent.size()).isEqualTo(2);
        for (JsonNode item : typeContent) {
            assertThat(item.get("type").asString()).isEqualTo("INTERVIEW_UPDATE");
        }

        MvcResult unreadFiltered =
                mockMvc.perform(
                                get("/api/notifications")
                                        .param("unreadOnly", "true")
                                        .header("Authorization", "Bearer " + tokenA))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode unreadContent = json(unreadFiltered).get("content");
        assertThat(unreadContent.size()).isEqualTo(2);
        for (JsonNode item : unreadContent) {
            assertThat(item.get("read").asBoolean()).isFalse();
        }

        MvcResult both =
                mockMvc.perform(
                                get("/api/notifications")
                                        .param("unreadOnly", "true")
                                        .param("type", "PLACEMENT_UPDATE")
                                        .header("Authorization", "Bearer " + tokenA))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode bothContent = json(both).get("content");
        assertThat(bothContent.size()).isEqualTo(1);
        assertThat(bothContent.get(0).get("id").asLong()).isEqualTo(placementUnread.getId());
    }

    // ------------------------------------------------------------------
    // (6) The response envelope is the §44 shape.
    // ------------------------------------------------------------------

    @Test
    void listEnvelope_isThePageResponseShape() throws Exception {
        User a = persistUser(Role.STUDENT);
        persistNotification(a, NotificationType.INTERVIEW_UPDATE, false);
        String tokenA = loginAsUser(a);

        MvcResult result =
                mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + tokenA))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode body = json(result);
        assertThat(body.has("content")).isTrue();
        assertThat(body.has("page")).isTrue();
        assertThat(body.has("size")).isTrue();
        assertThat(body.has("totalElements")).isTrue();
        assertThat(body.has("totalPages")).isTrue();
        assertThat(body.get("content").isArray()).isTrue();
    }

    // ------------------------------------------------------------------
    // (7) delete-all removes every one of the caller's rows and no one else's.
    // ------------------------------------------------------------------

    @Test
    void deleteAll_removesOnlyCallersOwnRows() throws Exception {
        User a = persistUser(Role.STUDENT);
        User b = persistUser(Role.STUDENT);
        persistNotification(a, NotificationType.INTERVIEW_UPDATE, false);
        persistNotification(a, NotificationType.PLACEMENT_UPDATE, false);
        Notification bNotification = persistNotification(b, NotificationType.CONTEST_UPDATE, false);

        String tokenA = loginAsUser(a);
        MvcResult result =
                mockMvc.perform(delete("/api/notifications").header("Authorization", "Bearer " + tokenA))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(json(result).get("deletedCount").asLong()).isEqualTo(2L);

        MvcResult afterList =
                mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + tokenA))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(json(afterList).get("content").size()).isEqualTo(0);

        assertThat(notificationRepository.findById(bNotification.getId())).isPresent();
    }
}
