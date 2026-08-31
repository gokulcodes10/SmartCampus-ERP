package smartcampus.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
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
import smartcampus.entity.Course;
import smartcampus.entity.Department;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.User;
import smartcampus.repository.CourseRepository;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.InterviewRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 10 (Interview) checkpoint: "scheduling two overlapping interviews for the same
 * student is rejected." Real HTTP, real bearer tokens, real MySQL via Testcontainers —
 * same convention as {@code AIAssistantFlowTest}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class InterviewSchedulingCheckpointTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private InterviewRepository interviewRepository;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String RAW_PASSWORD = "CheckpointPass1!";
    // "ISC" tags every fixture code/email this class creates, distinguishing them from
    // any sibling checkpoint test class sharing the cached Spring context / MySQL
    // instance.
    private static final String PREFIX = "ISC";

    private static String tag() {
        return PREFIX + SEQUENCE.incrementAndGet();
    }

    // ------------------------------------------------------------------
    // Fixture builders
    // ------------------------------------------------------------------

    private Department persistDepartment() {
        String t = tag();
        return departmentRepository.save(Department.builder().code(t + "D").name(t + " Dept").build());
    }

    private Course persistCourse(Department department) {
        String t = tag();
        return courseRepository.save(
                Course.builder().code(t + "C").name(t + " Course").department(department).build());
    }

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

    private Student persistActiveStudent(Department department, Course course) {
        String t = tag();
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

    /** A standalone ACTIVE student with its own department/course — one call per fixture. */
    private Student newActiveStudent() {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        return persistActiveStudent(department, course);
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

    private String loginAsStudent(Student student) throws Exception {
        return login(student.getUser().getEmail(), RAW_PASSWORD);
    }

    private String loginAsUser(User user) throws Exception {
        return login(user.getEmail(), RAW_PASSWORD);
    }

    private String adminToken() throws Exception {
        return loginAsUser(persistUser(Role.ADMIN));
    }

    private String facultyToken() throws Exception {
        return loginAsUser(persistUser(Role.FACULTY));
    }

    // ------------------------------------------------------------------
    // Request builders
    // ------------------------------------------------------------------

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private Map<String, Object> scheduleBody(
            Long studentId, String title, LocalDateTime start, LocalDateTime end) {
        return scheduleBody(studentId, title, start, end, "ONLINE", "https://meet.example.com/" + tag());
    }

    private Map<String, Object> scheduleBody(
            Long studentId, String title, LocalDateTime start, LocalDateTime end, String mode, String meetingLink) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("studentId", studentId);
        body.put("title", title);
        body.put("interviewType", "TECHNICAL");
        body.put("companyName", "Acme Corp");
        body.put("roundName", "Round 1");
        body.put("mode", mode);
        body.put("meetingLink", meetingLink);
        body.put("location", null);
        body.put("interviewerName", "Jane Doe");
        body.put("scheduledStart", start.format(ISO));
        body.put("scheduledEnd", end.format(ISO));
        body.put("notes", null);
        return body;
    }

    private Map<String, Object> updateBody(String title, String mode, String meetingLink) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("interviewType", "HR");
        body.put("companyName", "Updated Corp");
        body.put("roundName", "Round 2");
        body.put("mode", mode);
        body.put("meetingLink", meetingLink);
        body.put("location", null);
        body.put("interviewerName", "John Roe");
        body.put("notes", "updated");
        return body;
    }

    private Map<String, Object> rescheduleBody(LocalDateTime start, LocalDateTime end) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scheduledStart", start.format(ISO));
        body.put("scheduledEnd", end.format(ISO));
        return body;
    }

    private Map<String, Object> statusBody(String status, String outcome, String feedback, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("outcome", outcome);
        body.put("feedback", feedback);
        body.put("cancellationReason", reason);
        return body;
    }

    private MvcResult postInterview(String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(
                        post("/api/interviews")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long id(MvcResult result) throws Exception {
        return json(result).get("id").asLong();
    }

    private LocalDateTime baseStart() {
        // Fixed, far-future anchor per test so fixtures never collide with each other
        // across test methods (each uses its own freshly-created student anyway).
        return LocalDateTime.of(2027, 3, 1, 10, 0);
    }

    // ------------------------------------------------------------------
    // (1) THE CHECKPOINT.
    // ------------------------------------------------------------------

    @Test
    void checkpoint_overlappingInterviewForSameStudentIsRejected_andNotPersisted() throws Exception {
        Student student = newActiveStudent();
        String token = loginAsStudent(student);
        LocalDateTime start = baseStart();

        MvcResult first = postInterview(token, scheduleBody(null, "First Round", start, start.plusHours(1)));
        first.getResponse().setCharacterEncoding("UTF-8");
        assertThat(first.getResponse().getStatus()).isEqualTo(201);

        MvcResult second =
                postInterview(
                        token,
                        scheduleBody(null, "Second Round", start.plusMinutes(30), start.plusMinutes(90)));
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        JsonNode errorBody = json(second);
        assertThat(errorBody.get("error").asString()).isEqualTo("CONFLICT");
        String message = errorBody.get("message").asString();
        assertThat(message).contains("10:00").contains("11:00");

        long count =
                interviewRepository.findAll().stream()
                        .filter(i -> i.getStudent().getId().equals(student.getId()))
                        .count();
        assertThat(count).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // (2) Back-to-back is allowed.
    // ------------------------------------------------------------------

    @Test
    void backToBackInterviews_areAllowed() throws Exception {
        Student student = newActiveStudent();
        String token = loginAsStudent(student);
        LocalDateTime start = baseStart();

        assertThat(postInterview(token, scheduleBody(null, "A", start, start.plusHours(1)))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);
        assertThat(
                        postInterview(
                                        token,
                                        scheduleBody(null, "B", start.plusHours(1), start.plusHours(2)))
                                .getResponse()
                                .getStatus())
                .isEqualTo(201);
    }

    // ------------------------------------------------------------------
    // (3) Different students, identical window -> no conflict.
    // ------------------------------------------------------------------

    @Test
    void identicalWindow_differentStudents_isAllowed() throws Exception {
        Student studentA = newActiveStudent();
        Student studentB = newActiveStudent();
        String tokenA = loginAsStudent(studentA);
        String tokenB = loginAsStudent(studentB);
        LocalDateTime start = baseStart();

        assertThat(postInterview(tokenA, scheduleBody(null, "A", start, start.plusHours(1)))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);
        assertThat(postInterview(tokenB, scheduleBody(null, "B", start, start.plusHours(1)))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);
    }

    // ------------------------------------------------------------------
    // (4) Containment, both directions.
    // ------------------------------------------------------------------

    @Test
    void containment_bothDirections_isRejected() throws Exception {
        Student student = newActiveStudent();
        String token = loginAsStudent(student);
        LocalDateTime start = baseStart();

        assertThat(postInterview(token, scheduleBody(null, "Existing", start, start.plusHours(1)))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);

        // 09:00-12:00 wraps the existing 10:00-11:00.
        assertThat(
                        postInterview(
                                        token,
                                        scheduleBody(
                                                null, "Wraps", start.minusHours(1), start.plusHours(2)))
                                .getResponse()
                                .getStatus())
                .isEqualTo(409);

        // 10:15-10:30 sits fully inside the existing 10:00-11:00.
        assertThat(
                        postInterview(
                                        token,
                                        scheduleBody(
                                                null,
                                                "Inside",
                                                start.plusMinutes(15),
                                                start.plusMinutes(30)))
                                .getResponse()
                                .getStatus())
                .isEqualTo(409);
    }

    // ------------------------------------------------------------------
    // (5) A cancelled interview does not hold its slot.
    // ------------------------------------------------------------------

    @Test
    void cancelledInterview_doesNotBlockItsFormerSlot() throws Exception {
        Student student = newActiveStudent();
        String token = loginAsStudent(student);
        LocalDateTime start = baseStart();

        MvcResult first = postInterview(token, scheduleBody(null, "Original", start, start.plusHours(1)));
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        long firstId = id(first);

        mockMvc.perform(
                        put("/api/interviews/" + firstId + "/status")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                statusBody("CANCELLED", null, null, "Plans changed"))))
                .andExpect(status().isOk());

        MvcResult again = postInterview(token, scheduleBody(null, "Retry", start, start.plusHours(1)));
        assertThat(again.getResponse().getStatus()).isEqualTo(201);
    }

    // ------------------------------------------------------------------
    // (6) Reschedule excludes itself, but still conflicts with another live interview.
    // ------------------------------------------------------------------

    @Test
    void reschedule_ontoOwnWindow_succeeds_ontoAnotherLiveInterview_conflicts() throws Exception {
        Student student = newActiveStudent();
        String token = loginAsStudent(student);
        LocalDateTime start = baseStart();

        MvcResult first = postInterview(token, scheduleBody(null, "First", start, start.plusHours(1)));
        long firstId = id(first);

        // Onto its own current window -> 200 (excludes itself).
        mockMvc.perform(
                        put("/api/interviews/" + firstId + "/reschedule")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                rescheduleBody(start, start.plusHours(1)))))
                .andExpect(status().isOk());

        MvcResult second =
                postInterview(
                        token, scheduleBody(null, "Second", start.plusHours(3), start.plusHours(4)));
        assertThat(second.getResponse().getStatus()).isEqualTo(201);

        // Rescheduling "First" onto "Second"'s live window must conflict.
        MvcResult clash =
                mockMvc.perform(
                                put("/api/interviews/" + firstId + "/reschedule")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        rescheduleBody(
                                                                start.plusHours(3), start.plusHours(4)))))
                        .andReturn();
        assertThat(clash.getResponse().getStatus()).isEqualTo(409);
    }

    // ------------------------------------------------------------------
    // (7) Lifecycle validation — never a 500.
    // ------------------------------------------------------------------

    @Test
    void lifecycle_invalidMovesAreRejectedWith400() throws Exception {
        Student student = newActiveStudent();
        String token = loginAsStudent(student);
        LocalDateTime start = baseStart();

        // mode=ONLINE with no meetingLink -> 400 on schedule.
        MvcResult noLink =
                postInterview(
                        token, scheduleBody(null, "NoLink", start, start.plusHours(1), "ONLINE", null));
        assertThat(noLink.getResponse().getStatus()).isEqualTo(400);

        MvcResult created = postInterview(token, scheduleBody(null, "Lifecycle", start, start.plusHours(1)));
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        long interviewId = id(created);

        // outcome=SELECTED while moving to a non-COMPLETED status -> 400.
        MvcResult badOutcome =
                mockMvc.perform(
                                put("/api/interviews/" + interviewId + "/status")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        statusBody("NO_SHOW", "SELECTED", null, null))))
                        .andReturn();
        assertThat(badOutcome.getResponse().getStatus()).isEqualTo(400);

        // CANCELLED with no reason -> 400.
        MvcResult noReason =
                mockMvc.perform(
                                put("/api/interviews/" + interviewId + "/status")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        statusBody("CANCELLED", null, null, null))))
                        .andReturn();
        assertThat(noReason.getResponse().getStatus()).isEqualTo(400);

        // Now legitimately complete it, then confirm COMPLETED -> CANCELLED is 400 (terminal).
        mockMvc.perform(
                        put("/api/interviews/" + interviewId + "/status")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                statusBody("COMPLETED", "SELECTED", "Great chat", null))))
                .andExpect(status().isOk());

        MvcResult terminal =
                mockMvc.perform(
                                put("/api/interviews/" + interviewId + "/status")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        statusBody(
                                                                "CANCELLED", null, null, "Too late now"))))
                        .andReturn();
        assertThat(terminal.getResponse().getStatus()).isEqualTo(400);
    }

    // ------------------------------------------------------------------
    // (8) Security / ownership.
    // ------------------------------------------------------------------

    @Test
    void studentCannotAccessAnotherStudentsInterview() throws Exception {
        Student studentA = newActiveStudent();
        Student studentB = newActiveStudent();
        String tokenA = loginAsStudent(studentA);
        String tokenB = loginAsStudent(studentB);
        LocalDateTime start = baseStart();

        MvcResult created =
                postInterview(tokenA, scheduleBody(null, "A's interview", start, start.plusHours(1)));
        long interviewId = id(created);

        // B cannot GET A's interview.
        assertThat(
                        mockMvc.perform(
                                        get("/api/interviews/" + interviewId)
                                                .header("Authorization", "Bearer " + tokenB))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);

        // B cannot PUT A's interview.
        assertThat(
                        mockMvc.perform(
                                        put("/api/interviews/" + interviewId)
                                                .header("Authorization", "Bearer " + tokenB)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        objectMapper.writeValueAsString(
                                                                updateBody(
                                                                        "Hijacked",
                                                                        "ONLINE",
                                                                        "https://x"))))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);

        // B cannot reschedule A's interview.
        assertThat(
                        mockMvc.perform(
                                        put("/api/interviews/" + interviewId + "/reschedule")
                                                .header("Authorization", "Bearer " + tokenB)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        objectMapper.writeValueAsString(
                                                                rescheduleBody(
                                                                        start.plusHours(5),
                                                                        start.plusHours(6)))))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);

        // B cannot change A's interview status.
        assertThat(
                        mockMvc.perform(
                                        put("/api/interviews/" + interviewId + "/status")
                                                .header("Authorization", "Bearer " + tokenB)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        objectMapper.writeValueAsString(
                                                                statusBody(
                                                                        "CANCELLED",
                                                                        null,
                                                                        null,
                                                                        "Not yours"))))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);

        // The row is unchanged: A can still fetch it with its original title/status.
        MvcResult stillA =
                mockMvc.perform(
                                get("/api/interviews/" + interviewId).header("Authorization", "Bearer " + tokenA))
                        .andReturn();
        assertThat(stillA.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(stillA);
        assertThat(body.get("title").asString()).isEqualTo("A's interview");
        assertThat(body.get("status").asString()).isEqualTo("SCHEDULED");
    }

    @Test
    void studentCannotScheduleForAnotherStudent_andFacultyAndDeleteAreForbidden() throws Exception {
        Student studentA = newActiveStudent();
        Student studentB = newActiveStudent();
        String tokenA = loginAsStudent(studentA);
        LocalDateTime start = baseStart();

        // A STUDENT POST carrying another student's studentId is rejected outright.
        MvcResult hijack =
                postInterview(
                        tokenA, scheduleBody(studentB.getId(), "Hijack", start, start.plusHours(1)));
        assertThat(hijack.getResponse().getStatus()).isEqualTo(403);
        assertThat(
                        interviewRepository.findAll().stream()
                                .anyMatch(i -> i.getStudent().getId().equals(studentB.getId())))
                .isFalse();

        // A FACULTY token cannot list or schedule interviews.
        String facultyToken = facultyToken();
        assertThat(
                        mockMvc.perform(get("/api/interviews").header("Authorization", "Bearer " + facultyToken))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(403);
        assertThat(
                        postInterview(facultyToken, scheduleBody(null, "Nope", start, start.plusHours(1)))
                                .getResponse()
                                .getStatus())
                .isEqualTo(403);

        // A STUDENT can never DELETE, even their own interview.
        MvcResult created = postInterview(tokenA, scheduleBody(null, "Mine", start, start.plusHours(1)));
        long interviewId = id(created);
        assertThat(
                        mockMvc.perform(
                                        delete("/api/interviews/" + interviewId)
                                                .header("Authorization", "Bearer " + tokenA))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(403);
    }

    // ------------------------------------------------------------------
    // (9) /upcoming.
    // ------------------------------------------------------------------

    @Test
    void upcoming_returnsOnlyFutureBlockingRows_ascending_honoursLimit() throws Exception {
        Student student = newActiveStudent();
        String token = loginAsStudent(student);
        LocalDateTime start = baseStart();

        // A past SCHEDULED interview -> must be excluded.
        MvcResult past =
                postInterview(
                        token,
                        scheduleBody(null, "Past", start.minusYears(1), start.minusYears(1).plusHours(1)));
        assertThat(past.getResponse().getStatus()).isEqualTo(201);

        // A future interview that will be cancelled -> must be excluded.
        MvcResult toCancel =
                postInterview(
                        token,
                        scheduleBody(
                                null, "WillCancel", start.plusDays(1), start.plusDays(1).plusHours(1)));
        long toCancelId = id(toCancel);
        mockMvc.perform(
                        put("/api/interviews/" + toCancelId + "/status")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                statusBody("CANCELLED", null, null, "changed mind"))))
                .andExpect(status().isOk());

        // Three genuinely future, still-blocking interviews, out of chronological order.
        postInterview(
                token, scheduleBody(null, "Third", start.plusDays(3), start.plusDays(3).plusHours(1)));
        postInterview(
                token, scheduleBody(null, "First", start.plusDays(2), start.plusDays(2).plusHours(1)));
        postInterview(
                token, scheduleBody(null, "Second", start.plusDays(2).plusHours(2), start.plusDays(2).plusHours(3)));

        MvcResult upcoming =
                mockMvc.perform(
                                get("/api/interviews/upcoming")
                                        .header("Authorization", "Bearer " + token)
                                        .param("limit", "2"))
                        .andReturn();
        assertThat(upcoming.getResponse().getStatus()).isEqualTo(200);
        JsonNode list = json(upcoming);
        assertThat(list.isArray()).isTrue();
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.get(0).get("title").asString()).isEqualTo("First");
        assertThat(list.get(1).get("title").asString()).isEqualTo("Second");
    }
}
