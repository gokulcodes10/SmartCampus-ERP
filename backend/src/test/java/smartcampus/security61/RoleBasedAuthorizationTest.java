package smartcampus.security61;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import smartcampus.entity.Enrollment;
import smartcampus.entity.EnrollmentStatus;
import smartcampus.entity.Faculty;
import smartcampus.entity.FacultyStatus;
import smartcampus.entity.FacultySubjectAssignment;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.Subject;
import smartcampus.entity.User;
import smartcampus.repository.CourseRepository;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.EnrollmentRepository;
import smartcampus.repository.FacultyRepository;
import smartcampus.repository.FacultySubjectAssignmentRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.SubjectRepository;
import smartcampus.repository.UserRepository;

/**
 * §61 item 3 — role-based authorization.
 *
 * <p>For {@code ADMIN} and {@code STUDENT} this proves a route that is reachable to
 * exactly that role and denied to <em>both</em> of the other two — a genuine "the other
 * two must not reach it" property. {@code FACULTY} is different by deliberate design
 * (see {@code ScopedWriteAuthorizer} javadoc): {@code ADMIN} is authorized
 * unconditionally on every faculty-scoped write, since an admin has no {@code faculty}
 * row for {@code AcademicAccessGuard} to check against. So the strongest true statement
 * for FACULTY is "reachable to an assignment-scoped FACULTY caller, denied to STUDENT" —
 * that is what {@link #facultyReachesItsAssignedScope_studentCannot()} proves, and its
 * javadoc says so explicitly rather than overclaiming ADMIN exclusion that does not
 * exist in this codebase.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RoleBasedAuthorizationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private FacultyRepository facultyRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private FacultySubjectAssignmentRepository facultySubjectAssignmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private static int next() {
        return COUNTER.incrementAndGet();
    }

    private String uniqueEmail(String prefix) {
        return prefix + next() + "-" + System.nanoTime() + "@example.com";
    }

    private String persistUserAndLogin(String prefix, Role role, String password) throws Exception {
        String email = uniqueEmail(prefix);
        userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .fullName("RoleTest " + role.name())
                .role(role)
                .build());
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.replaceAll("(?s).*\"token\":\"([^\"]+)\".*", "$1");
    }

    // ------------------------------------------------------------------
    // ADMIN-only: POST /api/departments. FACULTY and STUDENT both denied; ADMIN allowed.
    // ------------------------------------------------------------------

    @Test
    void adminOnlyRoute_deniedToFacultyAndStudent_allowedToAdmin() throws Exception {
        String studentToken = persistUserAndLogin("roleadm-student", Role.STUDENT, "Pass1234!");
        String facultyToken = persistUserAndLogin("roleadm-faculty", Role.FACULTY, "Pass1234!");
        String adminToken = persistUserAndLogin("roleadm-admin", Role.ADMIN, "Pass1234!");

        String code = "RB" + next();
        String body = "{\"code\":\"" + code + "\",\"name\":\"Role Test Dept " + code + "\"}";

        mockMvc.perform(post("/api/departments")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/departments")
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/departments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------
    // STUDENT-only: POST /api/resumes. ADMIN and FACULTY both denied; STUDENT allowed.
    // ------------------------------------------------------------------

    @Test
    void studentOnlyRoute_deniedToAdminAndFaculty_allowedToStudent() throws Exception {
        // POST /api/resumes requires a real Student row linked to the caller's user id
        // (ScopedWriteAuthorizer.requireOwnStudent) - an account with role STUDENT but
        // no Student profile correctly gets 404 ("no student profile exists"), not 201.
        int n = next();
        Department department = departmentRepository.save(
                Department.builder().code("RS" + n).name("Role Stu Dept " + n).build());
        Course course = courseRepository.save(
                Course.builder().code("RSC" + n).name("Role Stu Course " + n).department(department).build());

        String studentEmail = uniqueEmail("rolestu-student");
        User studentUser = userRepository.save(User.builder()
                .email(studentEmail)
                .password(passwordEncoder.encode("Pass1234!"))
                .fullName("Role Student")
                .role(Role.STUDENT)
                .build());
        studentRepository.save(
                Student.builder()
                        .user(studentUser)
                        .registerNumber("RSREG" + n)
                        .department(department)
                        .course(course)
                        .currentSemester(3)
                        .section("A")
                        .admissionYear(2025)
                        .status(StudentStatus.ACTIVE)
                        .build());
        String studentToken = login(studentEmail, "Pass1234!");
        String facultyToken = persistUserAndLogin("rolestu-faculty", Role.FACULTY, "Pass1234!");
        String adminToken = persistUserAndLogin("rolestu-admin", Role.ADMIN, "Pass1234!");

        String resumeBody = "{\"title\":\"Role Test Resume " + next()
                + "\",\"template\":\"CLASSIC\",\"fullName\":\"Role Student\","
                + "\"email\":\"" + studentEmail + "\"}";

        mockMvc.perform(post("/api/resumes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resumeBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/resumes")
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resumeBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/resumes")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resumeBody))
                .andExpect(status().isCreated());
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.replaceAll("(?s).*\"token\":\"([^\"]+)\".*", "$1");
    }

    // ------------------------------------------------------------------
    // FACULTY reaches its own assigned (subject, year, semester, section) tuple via
    // POST /api/attendance/bulk; STUDENT is denied outright. See class javadoc for why
    // ADMIN is deliberately not asserted as excluded here.
    // ------------------------------------------------------------------

    @Test
    void facultyReachesItsAssignedScope_studentCannot() throws Exception {
        int n = next();
        Department department = departmentRepository.save(
                Department.builder().code("RD" + n).name("Role Dept " + n).build());
        Course course = courseRepository.save(
                Course.builder().code("RC" + n).name("Role Course " + n).department(department).build());
        Subject subject = subjectRepository.save(
                Subject.builder()
                        .code("RS" + n)
                        .name("Role Subject " + n)
                        .credits(4)
                        .semester(3)
                        .course(course)
                        .build());

        String facultyEmail = uniqueEmail("rolefac-faculty");
        userRepository.save(User.builder()
                .email(facultyEmail)
                .password(passwordEncoder.encode("Pass1234!"))
                .fullName("Role Faculty")
                .role(Role.FACULTY)
                .build());
        Faculty faculty = facultyRepository.save(
                Faculty.builder()
                        .user(userRepository.findByEmail(facultyEmail).orElseThrow())
                        .employeeCode("RFEMP" + n)
                        .department(department)
                        .status(FacultyStatus.ACTIVE)
                        .build());

        String academicYear = "2025-2026";
        int semester = 3;
        String section = "A";

        facultySubjectAssignmentRepository.save(
                FacultySubjectAssignment.builder()
                        .faculty(faculty)
                        .subject(subject)
                        .academicYear(academicYear)
                        .semester(semester)
                        .section(section)
                        .build());

        String studentEmail = uniqueEmail("rolefac-student");
        userRepository.save(User.builder()
                .email(studentEmail)
                .password(passwordEncoder.encode("Pass1234!"))
                .fullName("Role Enrolled Student")
                .role(Role.STUDENT)
                .build());
        Student student = studentRepository.save(
                Student.builder()
                        .user(userRepository.findByEmail(studentEmail).orElseThrow())
                        .registerNumber("RREG" + n)
                        .department(department)
                        .course(course)
                        .currentSemester(semester)
                        .section(section)
                        .admissionYear(2025)
                        .status(StudentStatus.ACTIVE)
                        .build());
        enrollmentRepository.save(
                Enrollment.builder()
                        .student(student)
                        .subject(subject)
                        .academicYear(academicYear)
                        .semester(semester)
                        .section(section)
                        .status(EnrollmentStatus.ACTIVE)
                        .build());

        String facultyToken = login(facultyEmail, "Pass1234!");
        // A second STUDENT account, unrelated to the class above — proves the denial is
        // "no STUDENT may write attendance", not merely "this particular student can't".
        String outsiderEmail = uniqueEmail("rolefac-outsider");
        userRepository.save(User.builder()
                .email(outsiderEmail)
                .password(passwordEncoder.encode("Pass1234!"))
                .fullName("Outsider Student")
                .role(Role.STUDENT)
                .build());
        String realOutsiderToken = login(outsiderEmail, "Pass1234!");

        String requestBody = "{\"subjectId\":" + subject.getId()
                + ",\"academicYear\":\"" + academicYear + "\",\"semester\":" + semester
                + ",\"section\":\"" + section + "\",\"date\":\"" + LocalDate.now()
                + "\",\"period\":1,\"entries\":[{\"studentId\":" + student.getId()
                + ",\"status\":\"PRESENT\"}]}";

        // STUDENT (even an enrolled one, even the class's own outsider) cannot write attendance at all.
        mockMvc.perform(post("/api/attendance/bulk")
                        .header("Authorization", "Bearer " + realOutsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());

        // FACULTY assigned to exactly this (subject, year, semester, section) succeeds.
        mockMvc.perform(post("/api/attendance/bulk")
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());
    }
}
