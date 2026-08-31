package smartcampus.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import smartcampus.TestcontainersConfiguration;
import smartcampus.entity.Interview;
import smartcampus.entity.InterviewDifficulty;
import smartcampus.entity.InterviewMode;
import smartcampus.entity.InterviewOutcome;
import smartcampus.entity.InterviewQuestion;
import smartcampus.entity.InterviewQuestionCategory;
import smartcampus.entity.InterviewQuestionProgress;
import smartcampus.entity.InterviewQuestionSource;
import smartcampus.entity.InterviewStatus;
import smartcampus.entity.InterviewType;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.User;
import smartcampus.repository.InterviewQuestionProgressRepository;
import smartcampus.repository.InterviewQuestionRepository;
import smartcampus.repository.InterviewRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.UserRepository;

/**
 * Wave 1 verification suite for the Phase 10 JPA domain layer.
 *
 * <p>Runs against a real, freshly-provisioned MySQL container (see {@link
 * TestcontainersConfiguration}) with Flyway applying every migration on the classpath,
 * so a passing {@code @SpringBootTest} context load IS the {@code
 * spring.jpa.hibernate.ddl-auto=validate} proof for all three entities in this phase
 * (PROJECT_PLAN.md clarification G8): if a single column name, nullability or JDBC type
 * in {@code InterviewQuestion}, {@code InterviewQuestionProgress} or {@code Interview}
 * diverged from {@code V10__interview.sql}, the context would fail to start and every
 * test below would fail with it.
 *
 * <p>On top of that, this suite asserts real behaviour through the repositories against
 * real MySQL rather than merely booting:
 *
 * <ul>
 *   <li>a MEDIUMTEXT column really round-trips a value larger than the 65,535-byte
 *       VARCHAR/TEXT ceiling byte-identical, proving the {@code
 *       @JdbcTypeCode(SqlTypes.LONGVARCHAR)} mapping is correct;
 *   <li>an AI_GENERATED question requires both model and ownership;
 *   <li>a COMPANY_SPECIFIC question requires a company name;
 *   <li>the unique constraint on (student, question) really rejects duplicate progress
 *       rows;
 *   <li>the completed / completedAt pairing is enforced by CHECK;
 *   <li>the generated-column partial-unique key really rejects two SCHEDULED interviews
 *       for the same student at the same instant, then allows it after cancellation;
 *   <li>the various scheduling constraints (window, mode+link, status+reason,
 *       status+outcome) are enforced.
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class InterviewSchemaValidationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private InterviewQuestionRepository questionRepository;
    @Autowired private InterviewQuestionProgressRepository progressRepository;
    @Autowired private InterviewRepository interviewRepository;

    private static long counter = 0;

    private String unique(String prefix) {
        return prefix + "-" + System.nanoTime() + "-" + (counter++);
    }

    private User persistAdmin() {
        String tag = unique("admin");
        return userRepository.save(User.builder()
                .email(tag + "@example.com")
                .password("irrelevant-hash")
                .fullName("Interview Verification Admin")
                .role(Role.ADMIN)
                .build());
    }

    private Student persistStudent() {
        String tag = unique("student");
        User user = userRepository.save(User.builder()
                .email(tag + "@example.com")
                .password("irrelevant-hash")
                .fullName("Interview Verification Student")
                .role(Role.STUDENT)
                .build());
        return studentRepository.save(Student.builder().user(user).build());
    }

    private InterviewQuestion persistCuratedQuestion(User admin) {
        String tag = unique("question");
        return questionRepository.saveAndFlush(InterviewQuestion.builder()
                .category(InterviewQuestionCategory.TECHNICAL)
                .difficulty(InterviewDifficulty.MEDIUM)
                .question("What is a database? " + tag)
                .answer("A structured collection of data.")
                .explanation("A database organizes data for efficient retrieval.")
                .source(InterviewQuestionSource.CURATED)
                .createdBy(admin)
                .build());
    }

    // ------------------------------------------------------------------
    // Context load == ddl-auto=validate proof for all three Phase 10 tables
    // ------------------------------------------------------------------

    @Test
    void contextLoads() {
        // Intentionally empty: a failing @JdbcTypeCode, column name, nullability or
        // length mismatch anywhere in the Phase 10 entities fails Spring context
        // startup before this test body ever runs.
        assertThat(questionRepository).isNotNull();
        assertThat(progressRepository).isNotNull();
        assertThat(interviewRepository).isNotNull();
    }

    // ------------------------------------------------------------------
    // MEDIUMTEXT / @JdbcTypeCode(LONGVARCHAR) round-trip
    // ------------------------------------------------------------------

    @Test
    void mediumTextColumn_roundTripsValueLargerThan65535Bytes_byteIdentical() {
        User admin = persistAdmin();

        // 70,000 characters comfortably exceeds the 65,535-byte VARCHAR/TEXT ceiling
        String large = "x".repeat(70_000);

        InterviewQuestion saved = questionRepository.saveAndFlush(InterviewQuestion.builder()
                .category(InterviewQuestionCategory.TECHNICAL)
                .difficulty(InterviewDifficulty.HARD)
                .question(large)
                .source(InterviewQuestionSource.CURATED)
                .createdBy(admin)
                .build());

        InterviewQuestion reloaded = questionRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getQuestion()).hasSize(70_000);
        assertThat(reloaded.getQuestion()).isEqualTo(large);
    }

    // ------------------------------------------------------------------
    // AI_GENERATED questions require model AND ownership
    // ------------------------------------------------------------------

    @Test
    void aiGeneratedQuestion_withoutModel_throwsDataIntegrityViolationException() {
        Student student = persistStudent();

        assertThatThrownBy(() -> questionRepository.saveAndFlush(InterviewQuestion.builder()
                        .category(InterviewQuestionCategory.TECHNICAL)
                        .difficulty(InterviewDifficulty.MEDIUM)
                        .question("What is an API?")
                        .source(InterviewQuestionSource.AI_GENERATED)
                        .ownerStudent(student)
                        .model(null)
                        .createdBy(null)
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aiGeneratedQuestion_withoutOwnership_throwsDataIntegrityViolationException() {
        User admin = persistAdmin();

        assertThatThrownBy(() -> questionRepository.saveAndFlush(InterviewQuestion.builder()
                        .category(InterviewQuestionCategory.TECHNICAL)
                        .difficulty(InterviewDifficulty.MEDIUM)
                        .question("What is a REST API?")
                        .source(InterviewQuestionSource.AI_GENERATED)
                        .ownerStudent(null)
                        .model("gpt-4")
                        .createdBy(admin)
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void curatedQuestion_withOwnership_throwsDataIntegrityViolationException() {
        User admin = persistAdmin();
        Student student = persistStudent();

        assertThatThrownBy(() -> questionRepository.saveAndFlush(InterviewQuestion.builder()
                        .category(InterviewQuestionCategory.TECHNICAL)
                        .difficulty(InterviewDifficulty.MEDIUM)
                        .question("What is a transaction?")
                        .source(InterviewQuestionSource.CURATED)
                        .ownerStudent(student)
                        .createdBy(admin)
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // COMPANY_SPECIFIC questions require a company name
    // ------------------------------------------------------------------

    @Test
    void companySpecificQuestion_withoutCompanyName_throwsDataIntegrityViolationException() {
        User admin = persistAdmin();

        assertThatThrownBy(() -> questionRepository.saveAndFlush(InterviewQuestion.builder()
                        .category(InterviewQuestionCategory.COMPANY_SPECIFIC)
                        .difficulty(InterviewDifficulty.MEDIUM)
                        .question("Tell me about your experience with Kubernetes.")
                        .companyName(null)
                        .source(InterviewQuestionSource.CURATED)
                        .createdBy(admin)
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // Interview question progress uniqueness and completed/completedAt pairing
    // ------------------------------------------------------------------

    @Test
    void interviewQuestionProgress_duplicateStudentQuestion_throwsDataIntegrityViolationException() {
        Student student = persistStudent();
        User admin = persistAdmin();
        InterviewQuestion question = persistCuratedQuestion(admin);

        progressRepository.saveAndFlush(InterviewQuestionProgress.builder()
                .student(student)
                .question(question)
                .completed(false)
                .bookmarked(false)
                .build());

        assertThatThrownBy(() -> progressRepository.saveAndFlush(InterviewQuestionProgress.builder()
                        .student(student)
                        .question(question)
                        .completed(true)
                        .bookmarked(true)
                        .completedAt(LocalDateTime.now())
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void interviewQuestionProgress_completedWithoutCompletedAt_throwsDataIntegrityViolationException() {
        Student student = persistStudent();
        User admin = persistAdmin();
        InterviewQuestion question = persistCuratedQuestion(admin);

        assertThatThrownBy(() -> progressRepository.saveAndFlush(InterviewQuestionProgress.builder()
                        .student(student)
                        .question(question)
                        .completed(true)
                        .bookmarked(false)
                        .completedAt(null)
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // Interview scheduling constraints
    // ------------------------------------------------------------------

    @Test
    void interview_windowInvalid_throwsDataIntegrityViolationException() {
        Student student = persistStudent();
        User admin = persistAdmin();
        LocalDateTime start = LocalDateTime.now().plusHours(1);

        assertThatThrownBy(() -> interviewRepository.saveAndFlush(Interview.builder()
                        .student(student)
                        .title("Mock Interview")
                        .interviewType(InterviewType.MOCK)
                        .mode(InterviewMode.PHONE)
                        .scheduledStart(start)
                        .scheduledEnd(start.minusMinutes(1))
                        .status(InterviewStatus.SCHEDULED)
                        .createdBy(admin)
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void interview_onlineModeWithoutLink_throwsDataIntegrityViolationException() {
        Student student = persistStudent();
        User admin = persistAdmin();
        LocalDateTime start = LocalDateTime.now().plusHours(1);

        assertThatThrownBy(() -> interviewRepository.saveAndFlush(Interview.builder()
                        .student(student)
                        .title("Online Interview")
                        .interviewType(InterviewType.TECHNICAL)
                        .mode(InterviewMode.ONLINE)
                        .meetingLink(null)
                        .scheduledStart(start)
                        .scheduledEnd(start.plusHours(1))
                        .status(InterviewStatus.SCHEDULED)
                        .createdBy(admin)
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void interview_cancelledWithoutReason_throwsDataIntegrityViolationException() {
        Student student = persistStudent();
        User admin = persistAdmin();
        LocalDateTime start = LocalDateTime.now().plusHours(1);

        assertThatThrownBy(() -> interviewRepository.saveAndFlush(Interview.builder()
                        .student(student)
                        .title("Cancelled Interview")
                        .interviewType(InterviewType.TECHNICAL)
                        .mode(InterviewMode.PHONE)
                        .scheduledStart(start)
                        .scheduledEnd(start.plusHours(1))
                        .status(InterviewStatus.CANCELLED)
                        .cancellationReason(null)
                        .createdBy(admin)
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void interview_outcomeonNonCompletedStatus_throwsDataIntegrityViolationException() {
        Student student = persistStudent();
        User admin = persistAdmin();
        LocalDateTime start = LocalDateTime.now().plusHours(1);

        assertThatThrownBy(() -> interviewRepository.saveAndFlush(Interview.builder()
                        .student(student)
                        .title("Interview with outcome")
                        .interviewType(InterviewType.TECHNICAL)
                        .mode(InterviewMode.PHONE)
                        .scheduledStart(start)
                        .scheduledEnd(start.plusHours(1))
                        .status(InterviewStatus.SCHEDULED)
                        .outcome(InterviewOutcome.SELECTED)
                        .createdBy(admin)
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // Generated column partial-unique key: same-instant slot detection
    // ------------------------------------------------------------------

    @Test
    void interview_twoScheduledAtSameInstant_firstSucceeds_secondFails_afterCancellationSucceeds() {
        Student student = persistStudent();
        User admin = persistAdmin();
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        LocalDateTime end = start.plusHours(1);

        // First interview at this time succeeds
        Interview first = interviewRepository.saveAndFlush(Interview.builder()
                .student(student)
                .title("First Interview")
                .interviewType(InterviewType.TECHNICAL)
                .mode(InterviewMode.ONLINE)
                .meetingLink("https://meet.example.com/1")
                .scheduledStart(start)
                .scheduledEnd(end)
                .status(InterviewStatus.SCHEDULED)
                .createdBy(admin)
                .build());
        assertThat(first.getId()).isNotNull();

        // Second interview at the exact same start instant fails due to the generated-column
        // partial-unique key
        assertThatThrownBy(() -> interviewRepository.saveAndFlush(Interview.builder()
                        .student(student)
                        .title("Second Interview")
                        .interviewType(InterviewType.HR)
                        .mode(InterviewMode.ONLINE)
                        .meetingLink("https://meet.example.com/2")
                        .scheduledStart(start)
                        .scheduledEnd(end.plusHours(1))
                        .status(InterviewStatus.SCHEDULED)
                        .createdBy(admin)
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Cancel the first interview
        first.setStatus(InterviewStatus.CANCELLED);
        first.setCancellationReason("Rescheduling");
        interviewRepository.saveAndFlush(first);

        // Now a new interview at the same start instant succeeds because the slot is no longer
        // "active" (the cancelled interview's active_slot_start is NULL)
        Interview third = interviewRepository.saveAndFlush(Interview.builder()
                .student(student)
                .title("Third Interview")
                .interviewType(InterviewType.HR)
                .mode(InterviewMode.PHONE)
                .scheduledStart(start)
                .scheduledEnd(end.plusHours(1))
                .status(InterviewStatus.SCHEDULED)
                .createdBy(admin)
                .build());
        assertThat(third.getId()).isNotNull();
    }

    // ------------------------------------------------------------------
    // Back-to-back interviews are allowed (half-open window semantics)
    // ------------------------------------------------------------------

    @Test
    void interview_backToBackSchedules_bothSucceed() {
        Student student = persistStudent();
        User admin = persistAdmin();
        LocalDateTime start1 = LocalDateTime.now().plusHours(1);
        LocalDateTime end1 = start1.plusHours(1);
        LocalDateTime start2 = end1; // Exactly where the first one ends

        // First interview 10:00-11:00
        Interview first = interviewRepository.saveAndFlush(Interview.builder()
                .student(student)
                .title("Morning Interview")
                .interviewType(InterviewType.TECHNICAL)
                .mode(InterviewMode.ONLINE)
                .meetingLink("https://meet.example.com/1")
                .scheduledStart(start1)
                .scheduledEnd(end1)
                .status(InterviewStatus.SCHEDULED)
                .createdBy(admin)
                .build());
        assertThat(first.getId()).isNotNull();

        // Second interview 11:00-12:00 (back-to-back, no conflict)
        Interview second = interviewRepository.saveAndFlush(Interview.builder()
                .student(student)
                .title("Afternoon Interview")
                .interviewType(InterviewType.HR)
                .mode(InterviewMode.PHONE)
                .scheduledStart(start2)
                .scheduledEnd(start2.plusHours(1))
                .status(InterviewStatus.SCHEDULED)
                .createdBy(admin)
                .build());
        assertThat(second.getId()).isNotNull();
    }
}
