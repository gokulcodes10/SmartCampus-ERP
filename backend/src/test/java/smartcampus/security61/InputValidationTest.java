package smartcampus.security61;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import smartcampus.TestcontainersConfiguration;
import smartcampus.entity.Course;
import smartcampus.entity.Department;
import smartcampus.entity.Exam;
import smartcampus.entity.ExamStatus;
import smartcampus.entity.ExamType;
import smartcampus.entity.Role;
import smartcampus.entity.Subject;
import smartcampus.entity.User;
import smartcampus.repository.CourseRepository;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.ExamRepository;
import smartcampus.repository.SubjectRepository;
import smartcampus.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * §61 item 5 — input validation. Six deliberately-invalid bodies across four different
 * modules (auth, academic reference data, attendance, marks), each asserted to produce
 * a clean 400/422 in the §47 envelope. A 500 anywhere in this class is the defect this
 * item exists to catch.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class InputValidationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private ExamRepository examRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private static int next() {
        return COUNTER.incrementAndGet();
    }

    private String uniqueEmail(String prefix) {
        return prefix + next() + "-" + System.nanoTime() + "@example.com";
    }

    private String adminToken() throws Exception {
        String email = uniqueEmail("iv-admin");
        userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode("Pass1234!"))
                .fullName("Input Validation Admin")
                .role(Role.ADMIN)
                .build());
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Pass1234!\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    // ------------------------------------------------------------------
    // 1) Auth module: blank required field (fullName)
    // ------------------------------------------------------------------

    @Test
    void register_blankFullName_returns400WithEnvelope() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + uniqueEmail("iv-blank")
                                + "\",\"password\":\"ValidPass1!\",\"fullName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // ------------------------------------------------------------------
    // 2) Auth module: malformed email
    // ------------------------------------------------------------------

    @Test
    void register_malformedEmail_returns400WithEnvelope() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"ValidPass1!\","
                                + "\"fullName\":\"Malformed Email Check\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ------------------------------------------------------------------
    // 3) Academic reference data module: oversized string (Department.code, max 10)
    // ------------------------------------------------------------------

    @Test
    void createDepartment_oversizedCode_returns400WithEnvelope() throws Exception {
        String token = adminToken();
        String tooLong = "ELEVEN-CHR"; // 10 chars is the limit; make it 11.
        tooLong = tooLong + "X";

        mockMvc.perform(post("/api/departments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + tooLong + "\",\"name\":\"Oversized Code Dept\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ------------------------------------------------------------------
    // 4) Attendance module: invalid enum value for AttendanceStatus
    // ------------------------------------------------------------------

    @Test
    void bulkAttendance_invalidEnumStatus_returns400_notA500() throws Exception {
        String token = adminToken();

        String body = "{\"subjectId\":1,\"academicYear\":\"2025-2026\",\"semester\":1,"
                + "\"section\":\"A\",\"date\":\"" + LocalDate.now() + "\",\"period\":1,"
                + "\"entries\":[{\"studentId\":1,\"status\":\"NOT_A_REAL_STATUS\"}]}";

        mockMvc.perform(post("/api/attendance/bulk")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ------------------------------------------------------------------
    // 5) Marks module: negative marksObtained
    // ------------------------------------------------------------------

    @Test
    void bulkMarks_negativeMarksObtained_returns400WithEnvelope() throws Exception {
        String token = adminToken();

        String body = "{\"examId\":1,\"entries\":[{\"studentId\":1,\"marksObtained\":-5.00}]}";

        mockMvc.perform(post("/api/marks/bulk")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ------------------------------------------------------------------
    // 6) Marks module: marksObtained greater than the exam's maximumMarks — a
    // cross-field rule field-level @Valid cannot express, enforced in MarksService.
    // ------------------------------------------------------------------

    @Test
    void bulkMarks_marksObtainedExceedsExamMaximum_returns400_notA500() throws Exception {
        String token = adminToken();

        int n = next();
        Department department = departmentRepository.save(
                Department.builder().code("IV" + n).name("Input Validation Dept " + n).build());
        Course course = courseRepository.save(
                Course.builder().code("IVC" + n).name("Input Validation Course " + n).department(department).build());
        Subject subject = subjectRepository.save(
                Subject.builder()
                        .code("IVS" + n)
                        .name("Input Validation Subject " + n)
                        .credits(3)
                        .semester(1)
                        .course(course)
                        .build());
        Exam exam = examRepository.save(
                Exam.builder()
                        .subject(subject)
                        .title("Input Validation Exam " + n)
                        .examType(ExamType.INTERNAL_1)
                        .academicYear("2025-2026")
                        .semester(1)
                        .section("A")
                        .examDate(LocalDate.now())
                        .maximumMarks(new BigDecimal("50.00"))
                        .status(ExamStatus.SCHEDULED)
                        .build());

        // 999999 need not resolve to a real student - the "exceeds maximum" check runs
        // before the enrollment lookup in MarksService.bulkUpsert, so this proves the
        // cross-field rule in isolation.
        String body = "{\"examId\":" + exam.getId()
                + ",\"entries\":[{\"studentId\":999999,\"marksObtained\":75.00}]}";

        mockMvc.perform(post("/api/marks/bulk")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
