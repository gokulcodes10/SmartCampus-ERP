package smartcampus.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import smartcampus.entity.InterviewDifficulty;
import smartcampus.entity.InterviewQuestion;
import smartcampus.entity.InterviewQuestionCategory;
import smartcampus.entity.InterviewQuestionProgress;
import smartcampus.entity.InterviewQuestionSource;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.User;
import smartcampus.repository.CourseRepository;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.InterviewQuestionProgressRepository;
import smartcampus.repository.InterviewQuestionRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 10 (Interview) verification for the question bank slice: real HTTP, real JWTs,
 * real MySQL (Testcontainers, real Flyway V10 migration) — no stubbing, the whole
 * {@code InterviewQuestionController} → {@code InterviewQuestionService} →
 * {@code InterviewQuestionRepository} stack is exercised end to end.
 *
 * <p>Every fixture code/email is derived from a per-JVM {@link AtomicInteger}, never
 * {@code System.nanoTime()} — PROJECT_PLAN.md documents a real duplicate-key flake
 * elsewhere caused by exactly that pattern.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class InterviewQuestionBankTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private InterviewQuestionRepository interviewQuestionRepository;
    @Autowired private InterviewQuestionProgressRepository interviewQuestionProgressRepository;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String RAW_PASSWORD = "CheckpointPass1!";
    // "IQT" tags every fixture code/email this class creates, distinguishing them from
    // any sibling checkpoint test class sharing the cached Spring context / MySQL
    // instance.
    private static final String PREFIX = "IQT";

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

    private Student persistActiveStudent() {
        Department department = persistDepartment();
        Course course = persistCourse(department);
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

    private InterviewQuestion persistCuratedQuestion(InterviewQuestionCategory category) {
        String t = tag();
        return interviewQuestionRepository.save(
                InterviewQuestion.builder()
                        .category(category)
                        .difficulty(InterviewDifficulty.MEDIUM)
                        .question(t + " curated question text?")
                        .source(InterviewQuestionSource.CURATED)
                        .ownerStudent(null)
                        .model(null)
                        .build());
    }

    private InterviewQuestion persistAiQuestion(Student owner) {
        String t = tag();
        return interviewQuestionRepository.save(
                InterviewQuestion.builder()
                        .category(InterviewQuestionCategory.TECHNICAL)
                        .difficulty(InterviewDifficulty.EASY)
                        .question(t + " private AI question text?")
                        .source(InterviewQuestionSource.AI_GENERATED)
                        .ownerStudent(owner)
                        .model("test-model-1")
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

    private String loginAsStudent(Student student) throws Exception {
        return login(student.getUser().getEmail(), RAW_PASSWORD);
    }

    private String loginAsUser(User user) throws Exception {
        return login(user.getEmail(), RAW_PASSWORD);
    }

    // ------------------------------------------------------------------
    // (1) Admin creates a question; a student sees it.
    // ------------------------------------------------------------------

    @Test
    void adminCreatesQuestion_studentSeesItInTheBank() throws Exception {
        User admin = persistUser(Role.ADMIN);
        Student student = persistActiveStudent();
        String adminToken = loginAsUser(admin);
        String studentToken = loginAsStudent(student);

        String t = tag();
        String createBody =
                "{\"category\":\"TECHNICAL\",\"difficulty\":\"HARD\",\"question\":\""
                        + t
                        + " what is a hashmap?\",\"answer\":\"A key-value structure.\"}";

        String createResponse =
                mockMvc.perform(
                                post("/api/interview-questions")
                                        .header("Authorization", "Bearer " + adminToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(createBody))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode created = objectMapper.readTree(createResponse);
        long questionId = created.get("id").asLong();
        assertThat(created.get("source").asString()).isEqualTo("CURATED");
        assertThat(created.get("mine").asBoolean()).isFalse();

        String listResponse =
                mockMvc.perform(
                                get("/api/interview-questions")
                                        .header("Authorization", "Bearer " + studentToken)
                                        .param("q", t))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode content = objectMapper.readTree(listResponse).get("content");
        boolean found = false;
        for (JsonNode node : content) {
            if (node.get("id").asLong() == questionId) {
                found = true;
            }
        }
        assertThat(found).isTrue();

        // Direct fetch by id also succeeds for the student.
        mockMvc.perform(
                        get("/api/interview-questions/" + questionId)
                                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // (2) A private AI-generated question is invisible to a second student and to
    //     admin — a non-visible id is 404, never 403.
    // ------------------------------------------------------------------

    @Test
    void privateAiQuestion_invisibleToOtherStudentAndAdmin_404NotForbidden() throws Exception {
        Student owner = persistActiveStudent();
        Student otherStudent = persistActiveStudent();
        User admin = persistUser(Role.ADMIN);

        InterviewQuestion privateQuestion = persistAiQuestion(owner);
        long questionId = privateQuestion.getId();

        String ownerToken = loginAsStudent(owner);
        String otherToken = loginAsStudent(otherStudent);
        String adminToken = loginAsUser(admin);

        // Owner sees it.
        String ownerGet =
                mockMvc.perform(
                                get("/api/interview-questions/" + questionId)
                                        .header("Authorization", "Bearer " + ownerToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode ownerNode = objectMapper.readTree(ownerGet);
        assertThat(ownerNode.get("id").asLong()).isEqualTo(questionId);
        assertThat(ownerNode.get("mine").asBoolean()).isTrue();
        assertThat(ownerNode.get("ownerStudentId").asLong()).isEqualTo(owner.getId());

        // A second student gets a clean 404, not 403.
        mockMvc.perform(
                        get("/api/interview-questions/" + questionId)
                                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        // Admin also gets 404 — admin visibility is the global bank only.
        mockMvc.perform(
                        get("/api/interview-questions/" + questionId)
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        // The owner's own list ("mine=true") contains the id; the other student's list
        // (default visibility) does not; the admin's list does not either.
        String ownerListResponse =
                mockMvc.perform(
                                get("/api/interview-questions")
                                        .header("Authorization", "Bearer " + ownerToken)
                                        .param("mine", "true"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(containsId(objectMapper.readTree(ownerListResponse).get("content"), questionId)).isTrue();

        String otherListResponse =
                mockMvc.perform(
                                get("/api/interview-questions")
                                        .header("Authorization", "Bearer " + otherToken)
                                        .param("size", "500"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(containsId(objectMapper.readTree(otherListResponse).get("content"), questionId)).isFalse();

        String adminListResponse =
                mockMvc.perform(
                                get("/api/interview-questions")
                                        .header("Authorization", "Bearer " + adminToken)
                                        .param("size", "500"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(containsId(objectMapper.readTree(adminListResponse).get("content"), questionId)).isFalse();
    }

    private boolean containsId(JsonNode array, long id) {
        for (JsonNode node : array) {
            if (node.get("id").asLong() == id) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // (3) Mark complete then un-mark: completedAt is null, the row still exists;
    //     bookmarking does not clear completion.
    // ------------------------------------------------------------------

    @Test
    void markComplete_thenUnmark_leavesCompletedAtNull_rowStillPresent() throws Exception {
        Student student = persistActiveStudent();
        InterviewQuestion question = persistCuratedQuestion(InterviewQuestionCategory.HR);
        String token = loginAsStudent(student);

        String markCompleteResponse =
                mockMvc.perform(
                                put("/api/interview-questions/" + question.getId() + "/progress")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"completed\":true}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode completedNode = objectMapper.readTree(markCompleteResponse);
        assertThat(completedNode.get("completed").asBoolean()).isTrue();
        assertThat(completedNode.get("completedAt").isNull()).isFalse();

        InterviewQuestionProgress afterComplete =
                interviewQuestionProgressRepository
                        .findByStudentIdAndQuestionId(student.getId(), question.getId())
                        .orElseThrow();
        assertThat(afterComplete.isCompleted()).isTrue();
        assertThat(afterComplete.getCompletedAt()).isNotNull();

        String unmarkResponse =
                mockMvc.perform(
                                put("/api/interview-questions/" + question.getId() + "/progress")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"completed\":false}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode unmarkedNode = objectMapper.readTree(unmarkResponse);
        assertThat(unmarkedNode.get("completed").asBoolean()).isFalse();
        assertThat(unmarkedNode.get("completedAt").isNull()).isTrue();

        InterviewQuestionProgress afterUnmark =
                interviewQuestionProgressRepository
                        .findByStudentIdAndQuestionId(student.getId(), question.getId())
                        .orElseThrow();
        assertThat(afterUnmark.isCompleted()).isFalse();
        assertThat(afterUnmark.getCompletedAt()).isNull();
        // The row still exists — un-marking updates it in place, never deletes it.
        assertThat(afterUnmark.getId()).isEqualTo(afterComplete.getId());
    }

    @Test
    void bookmarking_doesNotClearCompletion() throws Exception {
        Student student = persistActiveStudent();
        InterviewQuestion question = persistCuratedQuestion(InterviewQuestionCategory.CODING);
        String token = loginAsStudent(student);

        mockMvc.perform(
                        put("/api/interview-questions/" + question.getId() + "/progress")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"completed\":true}"))
                .andExpect(status().isOk());

        String bookmarkResponse =
                mockMvc.perform(
                                put("/api/interview-questions/" + question.getId() + "/progress")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"bookmarked\":true}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode node = objectMapper.readTree(bookmarkResponse);
        assertThat(node.get("bookmarked").asBoolean()).isTrue();
        // completed must NOT have been silently cleared by the bookmark-only request.
        assertThat(node.get("completed").asBoolean()).isTrue();
        assertThat(node.get("completedAt").isNull()).isFalse();

        InterviewQuestionProgress progress =
                interviewQuestionProgressRepository
                        .findByStudentIdAndQuestionId(student.getId(), question.getId())
                        .orElseThrow();
        assertThat(progress.isBookmarked()).isTrue();
        assertThat(progress.isCompleted()).isTrue();
        assertThat(progress.getCompletedAt()).isNotNull();
    }

    // ------------------------------------------------------------------
    // (4) Progress summary numbers equal real row counts, all six categories present.
    // ------------------------------------------------------------------

    @Test
    void progressSummary_matchesRealCounts_andListsAllSixCategories() throws Exception {
        Student student = persistActiveStudent();
        String token = loginAsStudent(student);

        InterviewQuestion technical = persistCuratedQuestion(InterviewQuestionCategory.TECHNICAL);
        InterviewQuestion hr = persistCuratedQuestion(InterviewQuestionCategory.HR);
        persistCuratedQuestion(InterviewQuestionCategory.TECHNICAL); // not completed

        mockMvc.perform(
                        put("/api/interview-questions/" + technical.getId() + "/progress")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"completed\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(
                        put("/api/interview-questions/" + hr.getId() + "/progress")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"completed\":true,\"bookmarked\":true}"))
                .andExpect(status().isOk());

        long realTotal = interviewQuestionRepository.countVisible(student.getId());
        long realCompleted =
                interviewQuestionProgressRepository.countByStudentIdAndCompletedTrue(student.getId());
        long realBookmarked =
                interviewQuestionProgressRepository.countByStudentIdAndBookmarkedTrue(student.getId());

        String summaryResponse =
                mockMvc.perform(
                                get("/api/interview-questions/progress/summary")
                                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode summary = objectMapper.readTree(summaryResponse);

        assertThat(summary.get("totalQuestions").asLong()).isEqualTo(realTotal);
        assertThat(summary.get("completed").asLong()).isEqualTo(realCompleted);
        assertThat(summary.get("bookmarked").asLong()).isEqualTo(realBookmarked);
        assertThat(summary.get("notStarted").asLong()).isEqualTo(realTotal - realCompleted);

        JsonNode byCategory = summary.get("byCategory");
        assertThat(byCategory.size()).isEqualTo(InterviewQuestionCategory.values().length);
        for (InterviewQuestionCategory category : InterviewQuestionCategory.values()) {
            boolean present = false;
            for (JsonNode node : byCategory) {
                if (node.get("category").asString().equals(category.name())) {
                    present = true;
                }
            }
            assertThat(present).as("category %s must be present even with zero questions", category).isTrue();
        }
    }

    // ------------------------------------------------------------------
    // (5) A student PUT to /{id} (edit) is 403.
    // ------------------------------------------------------------------

    @Test
    void studentCannotUpdateAQuestion_403() throws Exception {
        Student student = persistActiveStudent();
        InterviewQuestion question = persistCuratedQuestion(InterviewQuestionCategory.APTITUDE);
        String token = loginAsStudent(student);

        String updateBody =
                "{\"category\":\"APTITUDE\",\"question\":\"Attempted student edit?\"}";
        mockMvc.perform(
                        put("/api/interview-questions/" + question.getId())
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateBody))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // (6) A COMPANY_SPECIFIC question with no company is 400, not 500.
    // ------------------------------------------------------------------

    @Test
    void companySpecificQuestionWithoutCompany_isBadRequest_not500() throws Exception {
        User admin = persistUser(Role.ADMIN);
        String token = loginAsUser(admin);

        String createBody =
                "{\"category\":\"COMPANY_SPECIFIC\",\"question\":\"What do you know about us?\"}";
        mockMvc.perform(
                        post("/api/interview-questions")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody))
                .andExpect(status().isBadRequest());
    }
}
