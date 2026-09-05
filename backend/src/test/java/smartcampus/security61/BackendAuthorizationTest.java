package smartcampus.security61;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import smartcampus.TestcontainersConfiguration;
import smartcampus.entity.ApplicationStatus;
import smartcampus.entity.Company;
import smartcampus.entity.CompanyStatus;
import smartcampus.entity.Course;
import smartcampus.entity.Department;
import smartcampus.entity.Job;
import smartcampus.entity.JobStatus;
import smartcampus.entity.JobType;
import smartcampus.entity.PlacementApplication;
import smartcampus.entity.ResumeTemplate;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.User;
import smartcampus.repository.CompanyRepository;
import smartcampus.repository.CourseRepository;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.JobRepository;
import smartcampus.repository.PlacementApplicationRepository;
import smartcampus.repository.ResumeRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.UserRepository;
import smartcampus.entity.Resume;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * §61 item 4 — backend authorization (not frontend-only). Scope §8 calls this out by
 * name: modifying an id in the URL must not work.
 *
 * <p>A second STUDENT account, authenticated with a real JWT that IS accepted by the
 * filter chain, attempts to read another student's row across every module the task
 * names: {@code /api/students/{id}}, {@code /api/marks} (the admin-scoped {@code
 * /summary/{studentId}} route — there is no other by-id marks read), {@code
 * /api/attendance} (same shape, {@code /summary/{studentId}}), {@code
 * /api/resumes/{id}} and {@code /api/applications/{id}}. Per the Phase 3 note in
 * PROJECT_PLAN.md, {@code /api/students/{id}}, {@code /api/resumes/{id}} and {@code
 * /api/applications/{id}} deliberately answer 404 (never 403) for a cross-owner read,
 * so ID enumeration cannot distinguish "not yours" from "does not exist" — that is the
 * assertion this class makes for those three, not a generic "some 4xx". The two
 * admin-only summary routes are a genuinely different shape (they are not "the owner
 * OR admin" pattern, they are "ADMIN only", full stop) and correctly answer 403 for a
 * STUDENT caller regardless of whose id is in the URL — asserted here too, so a future
 * change that turned them into an ownership check would not silently look "more
 * correct" than intended and pass unnoticed.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class BackendAuthorizationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private ResumeRepository resumeRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private PlacementApplicationRepository placementApplicationRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private static int next() {
        return COUNTER.incrementAndGet();
    }

    private String uniqueEmail(String prefix) {
        return prefix + next() + "-" + System.nanoTime() + "@example.com";
    }

    private User persistUser(String email, String password, Role role) {
        return userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .fullName("IDOR Check " + role.name())
                .role(role)
                .build());
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private Department persistDepartment() {
        int n = next();
        return departmentRepository.save(Department.builder().code("IB" + n).name("IDOR Dept " + n).build());
    }

    private Course persistCourse(Department department) {
        int n = next();
        return courseRepository.save(
                Course.builder().code("IC" + n).name("IDOR Course " + n).department(department).build());
    }

    private Student persistActiveStudent(String email, Department department, Course course) {
        User user = persistUser(email, "IdorPass1!", Role.STUDENT);
        return studentRepository.save(
                Student.builder()
                        .user(user)
                        .registerNumber("IREG" + next())
                        .department(department)
                        .course(course)
                        .currentSemester(3)
                        .section("A")
                        .admissionYear(2025)
                        .status(StudentStatus.ACTIVE)
                        .build());
    }

    // ------------------------------------------------------------------
    // /api/students/{id} — cross-owner read is 404, never 403 (ID-enumeration guard).
    // ------------------------------------------------------------------

    @Test
    void studentCannotReadAnotherStudentsProfileById_getsA404_notA403() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Student victim = persistActiveStudent(uniqueEmail("idor-victim"), department, course);
        Student attacker = persistActiveStudent(uniqueEmail("idor-attacker"), department, course);
        String attackerToken = login(attacker.getUser().getEmail(), "IdorPass1!");

        mockMvc.perform(get("/api/students/" + victim.getId())
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound());

        // Same attacker CAN read their own row - proves the 404 above is ownership, not
        // the route being broken for everyone.
        mockMvc.perform(get("/api/students/" + attacker.getId())
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // /api/marks/summary/{studentId} and /api/attendance/summary/{studentId} — ADMIN
    // only, full stop. A STUDENT gets 403 regardless of whose id is in the URL.
    // ------------------------------------------------------------------

    @Test
    void studentCannotReachTheAdminOnlyMarksOrAttendanceSummaryRoute_evenForOwnId() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Student student = persistActiveStudent(uniqueEmail("idor-marks"), department, course);
        String token = login(student.getUser().getEmail(), "IdorPass1!");

        mockMvc.perform(get("/api/marks/summary/" + student.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/attendance/summary/" + student.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // /api/resumes/{id} — cross-owner read is 404, never 403.
    // ------------------------------------------------------------------

    @Test
    void studentCannotReadAnotherStudentsResumeById_getsA404() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Student victim = persistActiveStudent(uniqueEmail("idor-resume-victim"), department, course);
        Student attacker = persistActiveStudent(uniqueEmail("idor-resume-attacker"), department, course);
        String attackerToken = login(attacker.getUser().getEmail(), "IdorPass1!");

        Resume victimResume = resumeRepository.save(
                Resume.builder()
                        .student(victim)
                        .title("Victim Resume " + next())
                        .template(ResumeTemplate.CLASSIC)
                        .fullName(victim.getUser().getFullName())
                        .email(victim.getUser().getEmail())
                        .build());

        mockMvc.perform(get("/api/resumes/" + victimResume.getId())
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // /api/applications/{id} — cross-owner read is 404, never 403.
    // ------------------------------------------------------------------

    @Test
    void studentCannotReadAnotherStudentsApplicationById_getsA404() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Student victim = persistActiveStudent(uniqueEmail("idor-app-victim"), department, course);
        Student attacker = persistActiveStudent(uniqueEmail("idor-app-attacker"), department, course);
        String attackerToken = login(attacker.getUser().getEmail(), "IdorPass1!");

        User adminPoster = persistUser(uniqueEmail("idor-app-admin"), "IdorPass1!", Role.ADMIN);
        Company company = companyRepository.save(
                Company.builder().name("IDOR Company " + next()).status(CompanyStatus.ACTIVE).build());
        Job job = jobRepository.save(
                Job.builder()
                        .company(company)
                        .title("IDOR Job " + next())
                        .jobType(JobType.FULL_TIME)
                        .salaryCurrency("INR")
                        .status(JobStatus.OPEN)
                        .applicationDeadline(LocalDateTime.now().plusDays(30))
                        .postedBy(adminPoster)
                        .build());
        PlacementApplication victimApplication = placementApplicationRepository.save(
                PlacementApplication.builder()
                        .job(job)
                        .student(victim)
                        .status(ApplicationStatus.APPLIED)
                        .build());

        mockMvc.perform(get("/api/applications/" + victimApplication.getId())
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound());
    }
}
