package smartcampus.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import smartcampus.entity.Faculty;
import smartcampus.entity.FacultyStatus;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * §8/§42 "faculty-authorized announcements": FACULTY may create DEPARTMENT
 * announcements for their <b>own</b> department only, and may update/delete only
 * announcements they themselves created. Same real-HTTP/Testcontainers convention as
 * {@link AnnouncementTargetingTest}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class FacultyAnnouncementAuthorizationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private FacultyRepository facultyRepository;
    @Autowired private AnnouncementRepository announcementRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String RAW_PASSWORD = "CheckpointPass1!";
    // "FAA" tags every fixture this class creates — see AnnouncementTargetingTest.
    private static final String PREFIX = "FAA";

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

    private Student persistActiveStudent(Department department) {
        String t = tag();
        Course course =
                courseRepository.save(
                        Course.builder().code(t + "C").name(t + " Course").department(department).build());
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

    private String loginAsUser(User user) throws Exception {
        String body =
                mockMvc.perform(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"email\":\""
                                                        + user.getEmail()
                                                        + "\",\"password\":\""
                                                        + RAW_PASSWORD
                                                        + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(body).get("token").asString();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Map<String, Object> createBody(String title, String audience, Long departmentId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("body", tag() + " announcement body text.");
        body.put("audience", audience);
        body.put("departmentId", departmentId);
        body.put("priority", "NORMAL");
        body.put("expiresAt", null);
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
    // (1) Faculty CAN create a DEPARTMENT announcement for their own department,
    //     and it fans out to that department only.
    // ------------------------------------------------------------------

    @Test
    void faculty_canAnnounceToOwnDepartment_fanOutScopedToThatDepartment() throws Exception {
        Department ownDept = persistDepartment();
        Department otherDept = persistDepartment();
        Faculty author = persistActiveFaculty(ownDept);
        Student sameDeptStudent = persistActiveStudent(ownDept);
        Student otherDeptStudent = persistActiveStudent(otherDept);
        String token = loginAsUser(author.getUser());

        MvcResult result =
                postAnnouncement(
                        token, createBody(tag() + " Own Dept Notice", "DEPARTMENT", ownDept.getId()));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode created = json(result);
        long announcementId = created.get("id").asLong();
        assertThat(created.get("departmentId").asLong()).isEqualTo(ownDept.getId());

        assertThat(reached(sameDeptStudent.getUser(), announcementId)).isTrue();
        assertThat(reached(otherDeptStudent.getUser(), announcementId)).isFalse();
    }

    // ------------------------------------------------------------------
    // (2) A null departmentId defaults to the author's own department — there is
    //     only one legal value for a faculty caller.
    // ------------------------------------------------------------------

    @Test
    void faculty_nullDepartmentId_defaultsToOwnDepartment() throws Exception {
        Department ownDept = persistDepartment();
        Faculty author = persistActiveFaculty(ownDept);
        String token = loginAsUser(author.getUser());

        MvcResult result =
                postAnnouncement(token, createBody(tag() + " Defaulted Dept Notice", "DEPARTMENT", null));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(result).get("departmentId").asLong()).isEqualTo(ownDept.getId());
    }

    // ------------------------------------------------------------------
    // (3) Faculty can NOT target another department, and can NOT use any broader
    //     audience — 403, nothing created.
    // ------------------------------------------------------------------

    @Test
    void faculty_cannotTargetOtherDepartmentOrBroaderAudiences() throws Exception {
        Department ownDept = persistDepartment();
        Department otherDept = persistDepartment();
        Faculty author = persistActiveFaculty(ownDept);
        String token = loginAsUser(author.getUser());

        String otherDeptTitle = tag() + " Cross Dept Never";
        assertThat(
                        postAnnouncement(token, createBody(otherDeptTitle, "DEPARTMENT", otherDept.getId()))
                                .getResponse()
                                .getStatus())
                .isEqualTo(403);

        for (String audience : new String[] {"ALL", "STUDENTS", "FACULTY"}) {
            String title = tag() + " Broad Never " + audience;
            assertThat(postAnnouncement(token, createBody(title, audience, null)).getResponse().getStatus())
                    .isEqualTo(403);
        }

        assertThat(
                        announcementRepository.findAll().stream()
                                .noneMatch(a -> a.getTitle().contains(" Never")
                                        && a.getCreatedBy() != null
                                        && a.getCreatedBy().getId().equals(author.getUser().getId())))
                .isTrue();
    }

    // ------------------------------------------------------------------
    // (4) Update/delete: the creator may, another faculty may not, an admin may.
    // ------------------------------------------------------------------

    @Test
    void updateAndDelete_creatorAndAdminOnly() throws Exception {
        Department dept = persistDepartment();
        Faculty author = persistActiveFaculty(dept);
        Faculty otherFaculty = persistActiveFaculty(dept);
        User admin = persistUser(Role.ADMIN);
        String authorToken = loginAsUser(author.getUser());
        String otherToken = loginAsUser(otherFaculty.getUser());
        String adminToken = loginAsUser(admin);

        long firstId =
                json(postAnnouncement(
                                authorToken, createBody(tag() + " Editable Notice", "DEPARTMENT", null)))
                        .get("id")
                        .asLong();
        long secondId =
                json(postAnnouncement(
                                authorToken, createBody(tag() + " Deletable Notice", "DEPARTMENT", null)))
                        .get("id")
                        .asLong();

        String updateBody =
                objectMapper.writeValueAsString(
                        Map.of("title", tag() + " Edited Title", "body", "Edited body.", "priority", "NORMAL"));

        // Another faculty: both verbs 403.
        mockMvc.perform(
                        put("/api/announcements/" + firstId)
                                .header("Authorization", "Bearer " + otherToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        delete("/api/announcements/" + secondId)
                                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        // The creator: both verbs succeed.
        mockMvc.perform(
                        put("/api/announcements/" + firstId)
                                .header("Authorization", "Bearer " + authorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateBody))
                .andExpect(status().isOk());
        mockMvc.perform(
                        delete("/api/announcements/" + secondId)
                                .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isNoContent());

        // An admin can still delete the faculty-authored announcement.
        mockMvc.perform(
                        delete("/api/announcements/" + firstId)
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(announcementRepository.findById(firstId)).isEmpty();
        assertThat(announcementRepository.findById(secondId)).isEmpty();
    }

    // ------------------------------------------------------------------
    // (5) /manage: faculty see ONLY their own announcements; students are 403.
    // ------------------------------------------------------------------

    @Test
    void manage_facultySeesOnlyOwnAnnouncements_studentForbidden() throws Exception {
        Department dept = persistDepartment();
        Faculty author = persistActiveFaculty(dept);
        Faculty otherFaculty = persistActiveFaculty(dept);
        Student student = persistActiveStudent(dept);
        String authorToken = loginAsUser(author.getUser());
        String otherToken = loginAsUser(otherFaculty.getUser());

        String ownTitle = tag() + " Mine Manage";
        String otherTitle = tag() + " Theirs Manage";
        assertThat(
                        postAnnouncement(authorToken, createBody(ownTitle, "DEPARTMENT", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(201);
        assertThat(
                        postAnnouncement(otherToken, createBody(otherTitle, "DEPARTMENT", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(201);

        JsonNode page =
                json(
                        mockMvc.perform(
                                        get("/api/announcements/manage")
                                                .header("Authorization", "Bearer " + authorToken)
                                                .param("size", "100"))
                                .andExpect(status().isOk())
                                .andReturn());
        java.util.List<String> titles = new java.util.ArrayList<>();
        page.get("content").forEach(node -> titles.add(node.get("title").asString()));
        assertThat(titles).contains(ownTitle);
        assertThat(titles).doesNotContain(otherTitle);

        mockMvc.perform(
                        get("/api/announcements/manage")
                                .header("Authorization", "Bearer " + loginAsUser(student.getUser())))
                .andExpect(status().isForbidden());
    }
}
