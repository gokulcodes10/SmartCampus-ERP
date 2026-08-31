package smartcampus.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
 * Adversarial supplement to {@link InterviewSchedulingCheckpointTest}, written during
 * independent Phase 10 verification.
 *
 * <p>Covers each individual overlap shape the checkpoint enumerates against a fixed
 * 10:00-11:00 anchor interview (exact duplicate, starts-during, ends-during, wraps,
 * fully-inside) as its own assertion, plus both genuinely-adjacent slots, and drives a
 * real multi-threaded race of concurrent overlapping schedule requests for one student
 * through real HTTP with a {@link CountDownLatch} start gate so every thread submits at
 * (as close to) the same instant as the JVM allows — not the loosely-synchronized
 * shell-level race a set of backgrounded {@code curl} calls produces.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class InterviewSchedulingAdversarialTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private InterviewRepository interviewRepository;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String RAW_PASSWORD = "AdversarialPass1!";
    private static final String PREFIX = "ISA";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static String tag() {
        return PREFIX + SEQUENCE.incrementAndGet();
    }

    private Student newActiveStudent() {
        String t = tag();
        Department department = departmentRepository.save(Department.builder().code(t + "D").name(t + " Dept").build());
        Course course = courseRepository.save(Course.builder().code(t + "C").name(t + " Course").department(department).build());
        User user =
                userRepository.save(
                        User.builder()
                                .email(t.toLowerCase() + "@example.com")
                                .password(passwordEncoder.encode(RAW_PASSWORD))
                                .fullName(t + " Student")
                                .role(Role.STUDENT)
                                .build());
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

    private String loginAsStudent(Student student) throws Exception {
        String body =
                mockMvc.perform(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"email\":\""
                                                        + student.getUser().getEmail()
                                                        + "\",\"password\":\""
                                                        + RAW_PASSWORD
                                                        + "\"}"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(body).get("token").asString();
    }

    private Map<String, Object> scheduleBody(String title, LocalDateTime start, LocalDateTime end) {
        return Map.of(
                "title", title,
                "interviewType", "TECHNICAL",
                "companyName", "Acme",
                "roundName", "R1",
                "mode", "ONLINE",
                "meetingLink", "https://meet.example.com/" + tag(),
                "interviewerName", "Jane Doe",
                "scheduledStart", start.format(ISO),
                "scheduledEnd", end.format(ISO));
    }

    private MvcResult postInterview(String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(
                        post("/api/interviews")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    private LocalDateTime anchorStart() {
        return LocalDateTime.of(2029, 4, 1, 10, 0);
    }

    // ------------------------------------------------------------------
    // Each overlap shape named individually, exactly as the checkpoint spec enumerates.
    // ------------------------------------------------------------------

    @Test
    void exactDuplicate_isRejected() throws Exception {
        Student student = newActiveStudent();
        String token = loginAsStudent(student);
        LocalDateTime start = anchorStart();

        assertThat(postInterview(token, scheduleBody("Anchor", start, start.plusHours(1)))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);
        assertThat(postInterview(token, scheduleBody("Dup", start, start.plusHours(1)))
                        .getResponse()
                        .getStatus())
                .isEqualTo(409);
    }

    @Test
    void startsDuringExisting_isRejected() throws Exception {
        Student student = newActiveStudent();
        String token = loginAsStudent(student);
        LocalDateTime start = anchorStart();

        assertThat(postInterview(token, scheduleBody("Anchor", start, start.plusHours(1)))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);
        // 10:30-11:30 starts inside the 10:00-11:00 anchor.
        assertThat(
                        postInterview(
                                        token,
                                        scheduleBody("StartsDuring", start.plusMinutes(30), start.plusMinutes(90)))
                                .getResponse()
                                .getStatus())
                .isEqualTo(409);
    }

    @Test
    void endsDuringExisting_isRejected() throws Exception {
        Student student = newActiveStudent();
        String token = loginAsStudent(student);
        LocalDateTime start = anchorStart();

        assertThat(postInterview(token, scheduleBody("Anchor", start, start.plusHours(1)))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);
        // 09:30-10:30 ends inside the 10:00-11:00 anchor.
        assertThat(
                        postInterview(
                                        token,
                                        scheduleBody("EndsDuring", start.minusMinutes(30), start.plusMinutes(30)))
                                .getResponse()
                                .getStatus())
                .isEqualTo(409);
    }

    @Test
    void fullyContainsExisting_isRejected() throws Exception {
        Student student = newActiveStudent();
        String token = loginAsStudent(student);
        LocalDateTime start = anchorStart();

        assertThat(postInterview(token, scheduleBody("Anchor", start, start.plusHours(1)))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);
        // 09:00-12:00 wraps the 10:00-11:00 anchor entirely.
        assertThat(
                        postInterview(
                                        token,
                                        scheduleBody("Wraps", start.minusHours(1), start.plusHours(2)))
                                .getResponse()
                                .getStatus())
                .isEqualTo(409);
    }

    @Test
    void fullyInsideExisting_isRejected() throws Exception {
        Student student = newActiveStudent();
        String token = loginAsStudent(student);
        LocalDateTime start = anchorStart();

        assertThat(postInterview(token, scheduleBody("Anchor", start, start.plusHours(1)))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);
        // 10:15-10:45 sits entirely inside the 10:00-11:00 anchor.
        assertThat(
                        postInterview(
                                        token,
                                        scheduleBody("Inside", start.plusMinutes(15), start.plusMinutes(45)))
                                .getResponse()
                                .getStatus())
                .isEqualTo(409);
    }

    @Test
    void adjacentSlots_bothSides_areAllowed() throws Exception {
        Student student = newActiveStudent();
        String token = loginAsStudent(student);
        LocalDateTime start = anchorStart();

        assertThat(postInterview(token, scheduleBody("Anchor", start, start.plusHours(1)))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);
        // 09:00-10:00 ends exactly when the anchor starts.
        assertThat(
                        postInterview(token, scheduleBody("Before", start.minusHours(1), start))
                                .getResponse()
                                .getStatus())
                .isEqualTo(201);
        // 11:00-12:00 starts exactly when the anchor ends.
        assertThat(
                        postInterview(
                                        token, scheduleBody("After", start.plusHours(1), start.plusHours(2)))
                                .getResponse()
                                .getStatus())
                .isEqualTo(201);
    }

    // ------------------------------------------------------------------
    // Real concurrent race, latch-synchronized so every thread submits together.
    // ------------------------------------------------------------------

    @Test
    void concurrentOverlappingRequests_exactlyOneAccepted_noneCrash() throws Exception {
        Student student = newActiveStudent();
        String token = loginAsStudent(student);
        LocalDateTime start = anchorStart().plusYears(1);

        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);

        List<java.util.concurrent.Future<Integer>> futures =
                IntStream.range(0, threads)
                        .mapToObj(
                                i ->
                                        pool.submit(
                                                () -> {
                                                    ready.countDown();
                                                    go.await();
                                                    // All 12 requests target the SAME window for the
                                                    // SAME student - a genuine overlap race, not just a
                                                    // duplicate insert race.
                                                    MvcResult result =
                                                            postInterview(
                                                                    token,
                                                                    scheduleBody(
                                                                            "Race" + i, start, start.plusHours(1)));
                                                    return result.getResponse().getStatus();
                                                }))
                        .collect(Collectors.toList());

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();

        List<Integer> statuses = new java.util.ArrayList<>();
        for (var f : futures) {
            statuses.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        long accepted = statuses.stream().filter(s -> s == 201).count();
        long conflicted = statuses.stream().filter(s -> s == 409).count();
        long serverErrors = statuses.stream().filter(s -> s >= 500).count();

        long persistedCount =
                interviewRepository.findAll().stream()
                        .filter(i -> i.getStudent().getId().equals(student.getId()))
                        .count();

        // The invariant that actually matters for data integrity: never more than one
        // live row for the same overlapping window.
        assertThat(persistedCount)
                .as("no double-booking may ever be persisted, statuses=%s", statuses)
                .isEqualTo(1);
        assertThat(accepted).as("exactly one request should succeed, statuses=%s", statuses).isEqualTo(1);

        // The contract every other conflict path in this service honours: a rejected
        // overlapping booking is ALWAYS a clean 409 (DuplicateResourceException), never a
        // raw 5xx leaking an unhandled CannotAcquireLockException/deadlock to the client.
        assertThat(serverErrors)
                .as(
                        "a losing request must be rejected with 409, never surface as a raw"
                                + " 5xx (e.g. an unhandled InnoDB deadlock/CannotAcquireLockException"
                                + " from concurrent PESSIMISTIC_WRITE lock acquisition) - statuses=%s",
                        statuses)
                .isZero();
        assertThat(conflicted).isEqualTo(threads - 1);
    }
}
