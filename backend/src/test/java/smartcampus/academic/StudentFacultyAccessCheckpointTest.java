package smartcampus.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * Exercises the PROJECT_PLAN.md Phase 3 checkpoint through the real {@code
 * SecurityConfig} filter chain and real {@code StudentController}/{@code
 * FacultyController} endpoints: "explicit tests proving a student cannot read another
 * student's record by editing the ID in the URL, and that faculty [see] only the
 * students they actually teach" (PROJECT_PLAN.md clarification G2), plus the G1
 * pending-approval activation flow end to end.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class StudentFacultyAccessCheckpointTest {

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

    private static long counter = 0;

    private String unique(String prefix) {
        return prefix + "-" + System.nanoTime() + "-" + (counter++) + "@example.com";
    }

    private String registerStudent(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email
                                + "\",\"password\":\"" + password
                                + "\",\"fullName\":\"Checkpoint Student\"}"))
                .andExpect(status().isCreated());
        return login(email, password);
    }

    private User persistUser(String email, String rawPassword, Role role) {
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .fullName("Checkpoint " + role.name())
                .role(role)
                .build();
        return userRepository.save(user);
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extractString(body, "token");
    }

    private static String extractString(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\":\"([^\"]+)\"").matcher(json);
        if (!m.find()) {
            throw new IllegalStateException("Field " + field + " not found in " + json);
        }
        return m.group(1);
    }

    private static long extractLong(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\":(\\d+)").matcher(json);
        if (!m.find()) {
            throw new IllegalStateException("Field " + field + " not found in " + json);
        }
        return Long.parseLong(m.group(1));
    }

    private Department persistDepartment() {
        String suffix = String.valueOf(System.nanoTime()).substring(8);
        return departmentRepository.save(
                Department.builder().code("D" + suffix).name("Department " + suffix).build());
    }

    private Course persistCourse(Department department) {
        String suffix = String.valueOf(System.nanoTime()).substring(8);
        return courseRepository.save(
                Course.builder().code("C" + suffix).name("Course " + suffix).department(department).build());
    }

    private Subject persistSubject(Course course) {
        String suffix = String.valueOf(System.nanoTime()).substring(8);
        return subjectRepository.save(
                Subject.builder()
                        .code("S" + suffix)
                        .name("Subject " + suffix)
                        .credits(4)
                        .semester(1)
                        .course(course)
                        .build());
    }

    // ------------------------------------------------------------------
    // G1: registration creates a pending profile; admin activates it
    // ------------------------------------------------------------------

    @Test
    void registration_createsPendingStudentProfile_adminActivatesIt_idempotently() throws Exception {
        String email = unique("pending");
        String studentToken = registerStudent(email, "PendingPass1!");

        String meBody = mockMvc.perform(get("/api/students/me")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.registerNumber").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long studentId = extractLong(meBody, "id");

        String adminEmail = unique("admin");
        persistUser(adminEmail, "AdminPass1!", Role.ADMIN);
        String adminToken = login(adminEmail, "AdminPass1!");

        // Admin's pending queue contains this exact student.
        mockMvc.perform(get("/api/students/pending")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + studentId + ")]").exists());

        Department department = persistDepartment();
        Course course = persistCourse(department);
        String registerNumber = "REG" + System.nanoTime();

        String activateBody = "{\"registerNumber\":\"" + registerNumber
                + "\",\"departmentId\":" + department.getId()
                + ",\"courseId\":" + course.getId()
                + ",\"currentSemester\":1}";

        mockMvc.perform(post("/api/students/" + studentId + "/activate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.registerNumber").value(registerNumber))
                .andExpect(jsonPath("$.departmentId").value(department.getId()))
                .andExpect(jsonPath("$.courseId").value(course.getId()));

        // Activating an already-ACTIVE profile is rejected cleanly, not a 500 and not
        // a silent no-op that could double-apply anything.
        mockMvc.perform(post("/api/students/" + studentId + "/activate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activateBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // A non-admin (the student themselves) cannot activate anyone, including
        // themselves.
        mockMvc.perform(post("/api/students/" + studentId + "/activate")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activateBody))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // A student can never read (or list) another student's data
    // ------------------------------------------------------------------

    @Test
    void studentCannotReadAnotherStudentsRecord_byIdOrByListing() throws Exception {
        String emailA = unique("studenta");
        String emailB = unique("studentb");
        String tokenA = registerStudent(emailA, "StudentAPass1!");
        String tokenB = registerStudent(emailB, "StudentBPass1!");

        String meBodyA = mockMvc.perform(get("/api/students/me")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long idA = extractLong(meBodyA, "id");

        String meBodyB = mockMvc.perform(get("/api/students/me")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long idB = extractLong(meBodyB, "id");

        // Student A can read their own record by id.
        mockMvc.perform(get("/api/students/" + idA)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(emailA));

        // Student A editing the URL to Student B's id never returns Student B's data:
        // either a 404 (indistinguishable from a nonexistent id) or a 403, but never
        // 200 with someone else's record.
        mockMvc.perform(get("/api/students/" + idB)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    assertThat(sc).isIn(403, 404);
                })
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.registerNumber").doesNotExist());

        // The list endpoint is not a side door either: a STUDENT caller is rejected
        // outright rather than getting a filtered-but-nonempty roster.
        mockMvc.perform(get("/api/students")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());

        // A student attempting to update another student's record (or their own,
        // since admin-managed fields are not student-editable) is equally blocked.
        mockMvc.perform(patch("/api/students/" + idB + "/deactivate")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Faculty see only the students they actually teach (clarification G2)
    // ------------------------------------------------------------------

    @Test
    void facultySeesOnlyStudentsTheyActuallyTeach() throws Exception {
        Department department = persistDepartment();
        Course course = persistCourse(department);
        Subject subject = persistSubject(course);
        String academicYear = "2025-2026";
        int semester = 1;
        String section = "A";

        // Two activated students; only one is enrolled in the faculty's subject.
        String emailTaught = unique("taught");
        String tokenTaught = registerStudent(emailTaught, "TaughtPass1!");
        String meTaught = mockMvc.perform(get("/api/students/me")
                        .header("Authorization", "Bearer " + tokenTaught))
                .andReturn().getResponse().getContentAsString();
        long taughtStudentEntityId = extractLong(meTaught, "id");
        Student taughtStudent = studentRepository.findById(taughtStudentEntityId).orElseThrow();
        taughtStudent.setRegisterNumber("REGT" + System.nanoTime());
        taughtStudent.setDepartment(department);
        taughtStudent.setCourse(course);
        taughtStudent.setCurrentSemester(semester);
        taughtStudent.setSection(section);
        taughtStudent.setStatus(smartcampus.entity.StudentStatus.ACTIVE);
        studentRepository.save(taughtStudent);

        String emailOther = unique("other");
        String tokenOther = registerStudent(emailOther, "OtherPass1!");
        String meOther = mockMvc.perform(get("/api/students/me")
                        .header("Authorization", "Bearer " + tokenOther))
                .andReturn().getResponse().getContentAsString();
        long otherStudentEntityId = extractLong(meOther, "id");
        Student otherStudent = studentRepository.findById(otherStudentEntityId).orElseThrow();
        otherStudent.setRegisterNumber("REGO" + System.nanoTime());
        otherStudent.setDepartment(department);
        otherStudent.setCourse(course);
        otherStudent.setCurrentSemester(semester);
        otherStudent.setStatus(smartcampus.entity.StudentStatus.ACTIVE);
        studentRepository.save(otherStudent);

        enrollmentRepository.save(Enrollment.builder()
                .student(taughtStudent)
                .subject(subject)
                .academicYear(academicYear)
                .semester(semester)
                .section(section)
                .status(EnrollmentStatus.ACTIVE)
                .build());
        // otherStudent is deliberately NOT enrolled in this subject/section.

        String facultyEmail = unique("faculty");
        User facultyUser = persistUser(facultyEmail, "FacultyPass1!", Role.FACULTY);
        Faculty faculty = facultyRepository.save(Faculty.builder()
                .user(facultyUser)
                .employeeCode("EMP" + System.nanoTime())
                .department(department)
                .status(FacultyStatus.ACTIVE)
                .build());
        facultySubjectAssignmentRepository.save(FacultySubjectAssignment.builder()
                .faculty(faculty)
                .subject(subject)
                .academicYear(academicYear)
                .semester(semester)
                .section(section)
                .build());

        String facultyToken = login(facultyEmail, "FacultyPass1!");

        // The faculty list of students contains the taught student and nothing else.
        mockMvc.perform(get("/api/students")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + taughtStudentEntityId + ")]").exists())
                .andExpect(jsonPath("$.content[?(@.id == " + otherStudentEntityId + ")]").doesNotExist());

        // Direct by-id access mirrors the same rule.
        mockMvc.perform(get("/api/students/" + taughtStudentEntityId)
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(emailTaught));

        mockMvc.perform(get("/api/students/" + otherStudentEntityId)
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(403, 404))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    // ------------------------------------------------------------------
    // A faculty member can only ever read their own faculty profile
    // ------------------------------------------------------------------

    @Test
    void facultyCanReadOwnProfile_butNotAnotherFacultysProfile() throws Exception {
        Department department = persistDepartment();

        String emailA = unique("facultya");
        User userA = persistUser(emailA, "FacultyAPass1!", Role.FACULTY);
        Faculty facultyA = facultyRepository.save(Faculty.builder()
                .user(userA)
                .employeeCode("EMPA" + System.nanoTime())
                .department(department)
                .status(FacultyStatus.ACTIVE)
                .build());

        String emailB = unique("facultyb");
        User userB = persistUser(emailB, "FacultyBPass1!", Role.FACULTY);
        Faculty facultyB = facultyRepository.save(Faculty.builder()
                .user(userB)
                .employeeCode("EMPB" + System.nanoTime())
                .department(department)
                .status(FacultyStatus.ACTIVE)
                .build());

        String tokenA = login(emailA, "FacultyAPass1!");

        mockMvc.perform(get("/api/faculty/me").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(emailA));

        mockMvc.perform(get("/api/faculty/" + facultyA.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(emailA));

        mockMvc.perform(get("/api/faculty/" + facultyB.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(403, 404))
                .andExpect(jsonPath("$.email").doesNotExist());

        // Faculty cannot list all faculty either.
        mockMvc.perform(get("/api/faculty").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
    }
}
