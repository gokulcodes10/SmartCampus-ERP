package smartcampus.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import smartcampus.TestcontainersConfiguration;
import smartcampus.dto.AuthResponse;
import smartcampus.dto.CompanyCreateRequest;
import smartcampus.dto.CompanyResponse;
import smartcampus.dto.JobCreateRequest;
import smartcampus.dto.JobResponse;
import smartcampus.entity.JobType;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.User;
import smartcampus.repository.PlacementApplicationRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Independent, adversarial verification of the Phase 8 checkpoint's concurrency clause:
 * "race it: fire two concurrent applications and confirm only one is recorded, since a
 * check-then-insert without a unique constraint will let both through."
 *
 * <p>{@link PlacementCheckpointTest#applyOnce_duplicateRejected_uniqueKeyEnforcedAtDatabase_withdrawalIsTerminal()}
 * proves the unique constraint exists by inserting a second row directly through the
 * repository and asserting {@code DataIntegrityViolationException}. That is a genuine
 * proof the constraint exists, but it never drives two requests through {@code
 * PlacementApplicationController} at the same time, so it does not by itself rule out a
 * check-then-insert race in {@link smartcampus.service.PlacementApplicationService#apply}
 * slipping two {@code APPLIED} rows past the service-level {@code existsBy} check before
 * either has committed. This test closes that gap: it fires a real burst of concurrent
 * {@code POST /api/applications} requests, from real threads, through the real filter
 * chain (JWT auth + SecurityConfig), the real service, and the real Testcontainers MySQL
 * unique key, and asserts on the actual HTTP outcomes and the actual row count left in
 * the table afterward — not on a single simulated insert.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PlacementConcurrencyVerificationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private PlacementApplicationRepository placementApplicationRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private smartcampus.repository.DepartmentRepository departmentRepository;
    @Autowired private smartcampus.repository.CourseRepository courseRepository;
    @Autowired private smartcampus.repository.CompanyRepository companyRepository;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String PREFIX = "PLCR";
    private static final String RAW_PASSWORD = "ConcurrencyPass1!";

    private static String tag() {
        return String.valueOf(SEQUENCE.incrementAndGet());
    }

    private String login(String email, String password) throws Exception {
        String body =
                mockMvc.perform(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readValue(body, AuthResponse.class).token();
    }

    /**
     * True concurrency, exercised twice: once with a modest burst (8 threads) to match the
     * checkpoint's literal "fire two concurrent applications" at a level that gives the
     * database's lock manager a realistic chance to interleave badly if the guard were
     * Java-only, and this asserts on the row actually left in the table, not just on
     * response codes (a service bug could return 409 to every caller yet somehow persist
     * two rows, or vice versa — only counting the table rules that out).
     */
    @Test
    void concurrentApplications_exactlyOneRowPersisted_regardlessOfHttpOutcomeCounting() throws Exception {
        String t = tag();
        smartcampus.entity.Department dept =
                departmentRepository.save(
                        smartcampus.entity.Department.builder().code(PREFIX + "D" + t).name(PREFIX + " Dept " + t).build());
        smartcampus.entity.Course course =
                courseRepository.save(
                        smartcampus.entity.Course.builder()
                                .code(PREFIX + "C" + t)
                                .name(PREFIX + " Course " + t)
                                .department(dept)
                                .durationSemesters(8)
                                .build());
        User studentUser =
                userRepository.save(
                        User.builder()
                                .email(PREFIX.toLowerCase() + "-race" + t + "@example.com")
                                .password(passwordEncoder.encode(RAW_PASSWORD))
                                .fullName(PREFIX + " Race " + t)
                                .role(Role.STUDENT)
                                .build());
        Student student =
                studentRepository.save(
                        Student.builder()
                                .user(studentUser)
                                .registerNumber(PREFIX + "REG" + t)
                                .department(dept)
                                .course(course)
                                .currentSemester(5)
                                .section("A")
                                .admissionYear(2022)
                                .status(StudentStatus.ACTIVE)
                                .build());

        User adminUser =
                userRepository.save(
                        User.builder()
                                .email(PREFIX.toLowerCase() + "-admin" + t + "@example.com")
                                .password(passwordEncoder.encode(RAW_PASSWORD))
                                .fullName(PREFIX + " Admin " + t)
                                .role(Role.ADMIN)
                                .build());
        String adminToken = login(adminUser.getEmail(), RAW_PASSWORD);
        String studentToken = login(studentUser.getEmail(), RAW_PASSWORD);

        smartcampus.entity.Company company =
                companyRepository.save(
                        smartcampus.entity.Company.builder()
                                .name(PREFIX + " Co " + t)
                                .status(smartcampus.entity.CompanyStatus.ACTIVE)
                                .build());

        // No eligibility criteria at all -> the student is unconditionally eligible; the
        // ONLY thing that can reject a request here is the duplicate-application guard.
        JobCreateRequest jobRequest =
                new JobCreateRequest(
                        company.getId(),
                        PREFIX + " Race Role " + t,
                        null,
                        null,
                        JobType.FULL_TIME,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        LocalDateTime.now().plusDays(30),
                        null,
                        smartcampus.entity.JobStatus.OPEN);
        MockHttpServletResponse jobResponse =
                mockMvc.perform(
                                post("/api/jobs")
                                        .header("Authorization", "Bearer " + adminToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(jobRequest)))
                        .andReturn()
                        .getResponse();
        assertThat(jobResponse.getStatus()).isEqualTo(201);
        JobResponse job = objectMapper.readValue(jobResponse.getContentAsString(), JobResponse.class);

        int threadCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        try {
            // Submit first (each Future starts running immediately on the pool), THEN
            // release the starting gate — invokeAll() would block the submitting thread
            // until every task finishes, which would deadlock against `go` never being
            // counted down. Futures let the main thread submit, wait for every worker to
            // report ready, fire the gate, and only then block on the results.
            List<Future<Integer>> futures =
                    IntStream.range(0, threadCount)
                            .<Future<Integer>>mapToObj(
                                    i ->
                                            pool.submit(
                                                    () -> {
                                                        ready.countDown();
                                                        go.await();
                                                        String body =
                                                                "{\"jobId\":" + job.id() + ",\"coverNote\":\"race " + i + "\"}";
                                                        return mockMvc
                                                                .perform(
                                                                        post("/api/applications")
                                                                                .header("Authorization", "Bearer " + studentToken)
                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                .content(body))
                                                                .andReturn()
                                                                .getResponse()
                                                                .getStatus();
                                                    }))
                            .toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).as("all workers reached the starting gate").isTrue();
            go.countDown();
            List<Integer> statuses =
                    futures.stream()
                            .map(
                                    f -> {
                                        try {
                                            return f.get(30, TimeUnit.SECONDS);
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    })
                            .collect(Collectors.toList());
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

            long created = statuses.stream().filter(s -> s == 201).count();
            long conflicted = statuses.stream().filter(s -> s == 409).count();
            assertThat(created)
                    .as("exactly one concurrent POST /api/applications must be accepted, statuses=%s", statuses)
                    .isEqualTo(1);
            assertThat(conflicted)
                    .as("every losing request must come back 409, never a raw 5xx, statuses=%s", statuses)
                    .isEqualTo(threadCount - 1);

            long rowsInDatabase =
                    placementApplicationRepository.findByJobIdAndStudentId(job.id(), student.getId()).stream().count();
            assertThat(rowsInDatabase)
                    .as("the database must hold exactly one row for (job, student) after the race, not two")
                    .isEqualTo(1);

            // Confirm the surviving row is readable via GET /api/applications/me, i.e. the
            // "winner" is a real, queryable application, not an orphaned partial write.
            MockHttpServletResponse mine =
                    mockMvc.perform(get("/api/applications/me").header("Authorization", "Bearer " + studentToken))
                            .andReturn()
                            .getResponse();
            assertThat(mine.getStatus()).isEqualTo(200);
            assertThat(mine.getContentAsString()).contains("\"totalElements\":1");
        } finally {
            pool.shutdownNow();
        }
    }
}
