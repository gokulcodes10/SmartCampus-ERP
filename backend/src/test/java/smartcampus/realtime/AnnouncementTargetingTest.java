package smartcampus.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import smartcampus.dto.NotificationDispatch;
import smartcampus.entity.Course;
import smartcampus.entity.Department;
import smartcampus.entity.Faculty;
import smartcampus.entity.FacultyStatus;
import smartcampus.entity.NotificationReferenceType;
import smartcampus.entity.NotificationType;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.User;
import smartcampus.repository.AnnouncementRepository;
import smartcampus.repository.CourseRepository;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.FacultyRepository;
import smartcampus.repository.NotificationRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.UserRepository;
import smartcampus.service.NotificationService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 11 (Real-Time) checkpoint slice: §42 announcement audience targeting and its
 * fan-out into owned {@code notifications} rows. Real HTTP, real bearer tokens, real
 * MySQL via Testcontainers — same convention as {@code InterviewSchedulingCheckpointTest}.
 *
 * <p>Each test proves reach with a representative recipient in each category — a
 * student and a faculty member in each of two departments, plus an admin — via {@code
 * NotificationRepository.existsByUserIdAndDedupeKey}, which is a real per-user, per-key
 * DB lookup and is immune to any leftover rows other tests or other checkpoint classes
 * may have created against the shared MySQL instance.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AnnouncementTargetingTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private FacultyRepository facultyRepository;
    @Autowired private AnnouncementRepository announcementRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private PasswordEncoder passwordEncoder;

    @PersistenceContext private EntityManager entityManager;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String RAW_PASSWORD = "CheckpointPass1!";
    // "ATT" tags every fixture code/email this class creates, distinguishing them from
    // any sibling checkpoint test class sharing the cached Spring context / MySQL
    // instance.
    private static final String PREFIX = "ATT";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

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

    private Department persistDepartment() {
        String t = tag();
        return departmentRepository.save(Department.builder().code(t + "D").name(t + " Dept").build());
    }

    private Course persistCourse(Department department) {
        String t = tag();
        return courseRepository.save(
                Course.builder().code(t + "C").name(t + " Course").department(department).build());
    }

    private Student persistActiveStudent(Department department) {
        String t = tag();
        Course course = persistCourse(department);
        User user = persistUser(Role.STUDENT);
        return studentRepository.save(
                Student.builder()
                        .user(user)
                        .registerNumber(t + "REG")
                        .department(department)
                        .course(course)
                        .currentSemester(1)
                        .section("A")
                        .admissionYear(2024)
                        .status(StudentStatus.ACTIVE)
                        .build());
    }

    private Faculty persistActiveFaculty(Department department) {
        String t = tag();
        User user = persistUser(Role.FACULTY);
        return facultyRepository.save(
                Faculty.builder()
                        .user(user)
                        .employeeCode(t + "EMP")
                        .department(department)
                        .status(FacultyStatus.ACTIVE)
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

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Map<String, Object> createBody(
            String title, String audience, Long departmentId, LocalDateTime expiresAt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("body", tag() + " announcement body text.");
        body.put("audience", audience);
        body.put("departmentId", departmentId);
        body.put("priority", "NORMAL");
        body.put("expiresAt", expiresAt == null ? null : expiresAt.format(ISO));
        return body;
    }

    private MvcResult postAnnouncement(String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(
                        post("/api/announcements")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    private boolean reached(User user, Long announcementId) {
        return notificationRepository.existsByUserIdAndDedupeKey(
                user.getId(), "announcement:" + announcementId);
    }

    // ------------------------------------------------------------------
    // (1) STUDENTS reaches students, never faculty.
    // ------------------------------------------------------------------

    @Test
    void audienceStudents_reachesStudents_notFaculty() throws Exception {
        Department dept = persistDepartment();
        Student student = persistActiveStudent(dept);
        Faculty faculty = persistActiveFaculty(dept);
        User admin = persistUser(Role.ADMIN);
        String adminToken = loginAsUser(admin);

        MvcResult result =
                postAnnouncement(adminToken, createBody(tag() + " Students Notice", "STUDENTS", null, null));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        long announcementId = json(result).get("id").asLong();

        assertThat(reached(student.getUser(), announcementId)).isTrue();
        assertThat(reached(faculty.getUser(), announcementId)).isFalse();
    }

    // ------------------------------------------------------------------
    // (2) FACULTY reaches faculty, never students.
    // ------------------------------------------------------------------

    @Test
    void audienceFaculty_reachesFaculty_notStudents() throws Exception {
        Department dept = persistDepartment();
        Student student = persistActiveStudent(dept);
        Faculty faculty = persistActiveFaculty(dept);
        User admin = persistUser(Role.ADMIN);
        String adminToken = loginAsUser(admin);

        MvcResult result =
                postAnnouncement(adminToken, createBody(tag() + " Faculty Notice", "FACULTY", null, null));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        long announcementId = json(result).get("id").asLong();

        assertThat(reached(faculty.getUser(), announcementId)).isTrue();
        assertThat(reached(student.getUser(), announcementId)).isFalse();
    }

    // ------------------------------------------------------------------
    // (3) DEPARTMENT reaches students+faculty of that department only — not the
    //     other department, not an admin.
    // ------------------------------------------------------------------

    @Test
    void audienceDepartment_reachesOnlyThatDepartment_notOtherDept_notAdmin() throws Exception {
        Department dept1 = persistDepartment();
        Department dept2 = persistDepartment();
        Student student1 = persistActiveStudent(dept1);
        Faculty faculty1 = persistActiveFaculty(dept1);
        Student student2 = persistActiveStudent(dept2);
        Faculty faculty2 = persistActiveFaculty(dept2);
        User admin = persistUser(Role.ADMIN);
        String adminToken = loginAsUser(admin);

        MvcResult result =
                postAnnouncement(
                        adminToken, createBody(tag() + " Dept Notice", "DEPARTMENT", dept1.getId(), null));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        long announcementId = json(result).get("id").asLong();

        assertThat(reached(student1.getUser(), announcementId)).isTrue();
        assertThat(reached(faculty1.getUser(), announcementId)).isTrue();
        assertThat(reached(student2.getUser(), announcementId)).isFalse();
        assertThat(reached(faculty2.getUser(), announcementId)).isFalse();
        assertThat(reached(admin, announcementId)).isFalse();
    }

    // ------------------------------------------------------------------
    // (4) ALL reaches everyone, including the admin author.
    // ------------------------------------------------------------------

    @Test
    void audienceAll_reachesEveryone_includingAdminAuthor() throws Exception {
        Department dept = persistDepartment();
        Student student = persistActiveStudent(dept);
        Faculty faculty = persistActiveFaculty(dept);
        User admin = persistUser(Role.ADMIN);
        String adminToken = loginAsUser(admin);

        MvcResult result = postAnnouncement(adminToken, createBody(tag() + " All Notice", "ALL", null, null));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        long announcementId = json(result).get("id").asLong();

        assertThat(reached(student.getUser(), announcementId)).isTrue();
        assertThat(reached(faculty.getUser(), announcementId)).isTrue();
        assertThat(reached(admin, announcementId)).isTrue();
    }

    // ------------------------------------------------------------------
    // (5) Re-dispatching the same announcement does not duplicate anyone's row.
    // ------------------------------------------------------------------

    @Test
    void redispatchingSameAnnouncement_doesNotDuplicateAnyonesRow() throws Exception {
        Department dept = persistDepartment();
        Student student = persistActiveStudent(dept);
        User admin = persistUser(Role.ADMIN);
        String adminToken = loginAsUser(admin);

        MvcResult result =
                postAnnouncement(adminToken, createBody(tag() + " Dedupe Notice", "STUDENTS", null, null));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        long announcementId = json(result).get("id").asLong();
        assertThat(reached(student.getUser(), announcementId)).isTrue();

        // Re-run the exact same fan-out command the service issued at create time.
        String dedupeKey = "announcement:" + announcementId;
        NotificationDispatch replay =
                new NotificationDispatch(
                        student.getUser().getId(),
                        NotificationType.ANNOUNCEMENT,
                        "Dedupe Notice",
                        "replayed body",
                        null,
                        "/notifications",
                        NotificationReferenceType.ANNOUNCEMENT,
                        announcementId,
                        announcementId,
                        dedupeKey);
        int created = notificationService.dispatchAll(List.of(replay));
        assertThat(created).isEqualTo(0);

        long rowsForStudentWithKey =
                notificationRepository
                        .findByUserId(
                                student.getUser().getId(), org.springframework.data.domain.Pageable.unpaged())
                        .getContent()
                        .stream()
                        .filter(n -> dedupeKey.equals(n.getDedupeKey()))
                        .count();
        assertThat(rowsForStudentWithKey).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // (6) An expired announcement disappears from the board, but its
    //     already-delivered notifications remain.
    // ------------------------------------------------------------------

    @Test
    void expiredAnnouncement_disappearsFromBoard_notificationsRemain() throws Exception {
        Department dept = persistDepartment();
        Student student = persistActiveStudent(dept);
        User admin = persistUser(Role.ADMIN);
        String adminToken = loginAsUser(admin);
        String studentToken = loginAsUser(student.getUser());

        LocalDateTime shortExpiry = LocalDateTime.now().plusSeconds(2);
        MvcResult created =
                postAnnouncement(
                        adminToken,
                        createBody(tag() + " Expiring Notice", "STUDENTS", null, shortExpiry));
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        long announcementId = json(created).get("id").asLong();
        assertThat(reached(student.getUser(), announcementId)).isTrue();

        // Still active right after creation.
        assertThat(json(created).get("active").asBoolean()).isTrue();

        Thread.sleep(2500);

        MvcResult board =
                mockMvc.perform(get("/api/announcements").header("Authorization", "Bearer " + studentToken))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode boardContent = json(board).get("content");
        for (JsonNode item : boardContent) {
            assertThat(item.get("id").asLong()).isNotEqualTo(announcementId);
        }

        // A non-admin can no longer fetch it directly either.
        mockMvc.perform(
                        get("/api/announcements/" + announcementId)
                                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());

        // But the already-delivered notification is still there.
        assertThat(reached(student.getUser(), announcementId)).isTrue();
    }

    // ------------------------------------------------------------------
    // (7) DELETE removes the announcement from every recipient's centre (cascade),
    //     verified with a fresh DB read after flush/clear.
    // ------------------------------------------------------------------

    @Test
    void delete_removesFromEveryRecipientsCentre_cascade() throws Exception {
        Department dept = persistDepartment();
        Student student = persistActiveStudent(dept);
        Faculty faculty = persistActiveFaculty(dept);
        User admin = persistUser(Role.ADMIN);
        String adminToken = loginAsUser(admin);

        MvcResult created =
                postAnnouncement(adminToken, createBody(tag() + " Cascade Notice", "ALL", null, null));
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        long announcementId = json(created).get("id").asLong();
        assertThat(reached(student.getUser(), announcementId)).isTrue();
        assertThat(reached(faculty.getUser(), announcementId)).isTrue();

        mockMvc.perform(
                        delete("/api/announcements/" + announcementId)
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // No entityManager.flush() here: the DELETE above already committed over real HTTP
        // (this test class has no ambient @Transactional), so flush() outside a transaction
        // throws TransactionRequiredException. clear() alone is enough to drop this test's
        // own stale first-level-cache reads before re-querying below.
        entityManager.clear();

        assertThat(reached(student.getUser(), announcementId)).isFalse();
        assertThat(reached(faculty.getUser(), announcementId)).isFalse();
        assertThat(announcementRepository.findById(announcementId)).isEmpty();
    }

    // ------------------------------------------------------------------
    // (8) POST as a non-admin is 403; anonymous is 401 — never 201.
    // ------------------------------------------------------------------

    @Test
    void post_nonAdminIs403_anonymousIs401_neverCreated() throws Exception {
        Department dept = persistDepartment();
        Student student = persistActiveStudent(dept);
        String studentToken = loginAsUser(student.getUser());

        String uniqueTitle = tag() + " Should Never Exist";
        mockMvc.perform(
                        post("/api/announcements")
                                .header("Authorization", "Bearer " + studentToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                createBody(uniqueTitle, "ALL", null, null))))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        post("/api/announcements")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                createBody(uniqueTitle, "ALL", null, null))))
                .andExpect(status().isUnauthorized());

        assertThat(announcementRepository.findAll().stream().noneMatch(a -> uniqueTitle.equals(a.getTitle())))
                .isTrue();
    }

    // ------------------------------------------------------------------
    // (9) A DEPARTMENT announcement with no departmentId is a §47 400, not a 500 —
    //     proof the invariant is validated in Java before the CHECK constraint fires.
    // ------------------------------------------------------------------

    @Test
    void departmentAudienceWithoutDepartmentId_returns400NotServerError() throws Exception {
        User admin = persistUser(Role.ADMIN);
        String adminToken = loginAsUser(admin);

        MvcResult result =
                postAnnouncement(
                        adminToken, createBody(tag() + " Broken Dept Notice", "DEPARTMENT", null, null));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode error = json(result);
        assertThat(error.get("status").asInt()).isEqualTo(400);
        assertThat(error.get("error").asString()).isEqualTo("BAD_REQUEST");
    }
}
