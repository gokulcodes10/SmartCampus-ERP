package smartcampus.coding;

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
import smartcampus.entity.CodingContest;
import smartcampus.entity.CodingProblem;
import smartcampus.entity.CodingSubmission;
import smartcampus.entity.ContestProblem;
import smartcampus.entity.ContestStatus;
import smartcampus.entity.ProblemDifficulty;
import smartcampus.entity.ProblemTestCase;
import smartcampus.entity.ProgrammingLanguage;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.SubmissionStatus;
import smartcampus.entity.User;
import smartcampus.repository.CodingContestRepository;
import smartcampus.repository.CodingProblemRepository;
import smartcampus.repository.CodingSubmissionRepository;
import smartcampus.repository.ContestProblemRepository;
import smartcampus.repository.ProblemTestCaseRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.UserRepository;

/**
 * Wave 1 verification suite for the Phase 7 JPA domain layer.
 *
 * <p>Runs against a real, freshly-provisioned MySQL container (see {@link
 * TestcontainersConfiguration}) with Flyway applying every migration on the classpath,
 * so a passing {@code @SpringBootTest} context load IS the {@code
 * spring.jpa.hibernate.ddl-auto=validate} proof for all seven entities in this phase
 * (PROJECT_PLAN.md clarification G8): if a single column name, nullability or JDBC type
 * in {@code CodingProblem}, {@code ProblemTestCase}, {@code CodingContest}, {@code
 * ContestProblem}, {@code CodingSubmission}, {@code SubmissionTestResult} or {@code
 * ContestParticipant} diverged from {@code V7__coding.sql}, the context would fail to
 * start and every test below would fail with it.
 *
 * <p>On top of that, this suite asserts real behaviour through the repositories against
 * real MySQL rather than merely booting:
 *
 * <ul>
 *   <li>the sample/hidden test-case split really filters at the database, not just in
 *       the Java field name;
 *   <li>a MEDIUMTEXT column really round-trips a value larger than the 65,535-byte
 *       VARCHAR/TEXT ceiling byte-identical, which is the only way to prove the {@code
 *       @JdbcTypeCode(SqlTypes.LONGVARCHAR)} mapping is actually correct rather than
 *       merely present;
 *   <li>a practice submission (no contest) persists;
 *   <li>the composite foreign key {@code (contest_id, problem_id) -> contest_problems}
 *       really rejects a submission for a problem that is not part of the contest it
 *       claims to belong to;
 *   <li>{@code chk_coding_submissions_accepted_is_earned} really rejects a fabricated
 *       ACCEPTED verdict with zero test cases run - the §69 anti-fake-verdict
 *       constraint this whole phase exists to enforce.
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CodingSchemaValidationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private CodingProblemRepository problemRepository;
    @Autowired private ProblemTestCaseRepository testCaseRepository;
    @Autowired private CodingSubmissionRepository submissionRepository;
    @Autowired private CodingContestRepository contestRepository;
    @Autowired private ContestProblemRepository contestProblemRepository;

    private static long counter = 0;

    private String unique(String prefix) {
        return prefix + "-" + System.nanoTime() + "-" + (counter++);
    }

    private User persistAdmin() {
        String tag = unique("admin");
        return userRepository.save(User.builder()
                .email(tag + "@example.com")
                .password("irrelevant-hash")
                .fullName("Coding Verification Admin")
                .role(Role.ADMIN)
                .build());
    }

    private Student persistStudent() {
        String tag = unique("student");
        User user = userRepository.save(User.builder()
                .email(tag + "@example.com")
                .password("irrelevant-hash")
                .fullName("Coding Verification Student")
                .role(Role.STUDENT)
                .build());
        return studentRepository.save(Student.builder().user(user).build());
    }

    private CodingProblem persistProblem(User admin) {
        String tag = unique("problem");
        return problemRepository.saveAndFlush(CodingProblem.builder()
                .slug(tag)
                .title("Verification Problem " + tag)
                .description("Add two numbers.")
                .difficulty(ProblemDifficulty.EASY)
                .createdBy(admin)
                .build());
    }

    // ------------------------------------------------------------------
    // Context load == ddl-auto=validate proof for all seven Phase 7 tables
    // ------------------------------------------------------------------

    @Test
    void contextLoads() {
        // Intentionally empty: a failing @JdbcTypeCode, column name, nullability or
        // length mismatch anywhere in the Phase 7 entities fails Spring context
        // startup before this test body ever runs.
        assertThat(problemRepository).isNotNull();
    }

    // ------------------------------------------------------------------
    // Sample vs hidden test cases
    // ------------------------------------------------------------------

    @Test
    void sampleTestCaseQuery_returnsOnlySampleCases() {
        User admin = persistAdmin();
        CodingProblem problem = persistProblem(admin);

        ProblemTestCase sample = testCaseRepository.saveAndFlush(ProblemTestCase.builder()
                .problem(problem)
                .ordinal(1)
                .input("2 3")
                .expectedOutput("5")
                .sample(true)
                .build());
        ProblemTestCase hidden = testCaseRepository.saveAndFlush(ProblemTestCase.builder()
                .problem(problem)
                .ordinal(2)
                .input("100 200")
                .expectedOutput("300")
                .sample(false)
                .build());

        List<ProblemTestCase> samples =
                testCaseRepository.findByProblemIdAndSampleTrueOrderByOrdinalAsc(problem.getId());

        assertThat(samples).extracting(ProblemTestCase::getId).containsExactly(sample.getId());
        assertThat(samples).extracting(ProblemTestCase::getId).doesNotContain(hidden.getId());
        assertThat(testCaseRepository.countByProblemId(problem.getId())).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // MEDIUMTEXT / @JdbcTypeCode(LONGVARCHAR) round-trip
    // ------------------------------------------------------------------

    @Test
    void mediumTextColumn_roundTripsValueLargerThan65535Bytes_byteIdentical() {
        User admin = persistAdmin();
        CodingProblem problem = persistProblem(admin);

        // 70,000 characters comfortably exceeds the 65,535-byte VARCHAR/TEXT ceiling
        // that a plain String field (mapped to VARCHAR without @JdbcTypeCode) would
        // silently truncate at or fail to write at all.
        String large = "x".repeat(70_000);

        ProblemTestCase saved = testCaseRepository.saveAndFlush(ProblemTestCase.builder()
                .problem(problem)
                .ordinal(1)
                .input(large)
                .expectedOutput("ok")
                .sample(false)
                .build());

        ProblemTestCase reloaded = testCaseRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getInput()).hasSize(70_000);
        assertThat(reloaded.getInput()).isEqualTo(large);
    }

    // ------------------------------------------------------------------
    // Practice submission (contest == null)
    // ------------------------------------------------------------------

    @Test
    void practiceSubmission_withNullContest_persists() {
        User admin = persistAdmin();
        Student student = persistStudent();
        CodingProblem problem = persistProblem(admin);

        CodingSubmission saved = submissionRepository.saveAndFlush(CodingSubmission.builder()
                .problem(problem)
                .student(student)
                .contest(null)
                .language(ProgrammingLanguage.JAVA)
                .sourceCode("public class Main { public static void main(String[] a) {} }")
                .status(SubmissionStatus.PENDING)
                .build());

        CodingSubmission reloaded = submissionRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getContest()).isNull();
        assertThat(reloaded.getStatus()).isEqualTo(SubmissionStatus.PENDING);
    }

    // ------------------------------------------------------------------
    // Composite FK (contest_id, problem_id) -> contest_problems
    // ------------------------------------------------------------------

    @Test
    void contestSubmission_forProblemInContest_succeeds_forProblemNotInContest_fails() {
        User admin = persistAdmin();
        Student student = persistStudent();
        CodingProblem problemInContest = persistProblem(admin);
        CodingProblem problemNotInContest = persistProblem(admin);

        CodingContest contest = contestRepository.saveAndFlush(CodingContest.builder()
                .slug(unique("contest"))
                .title("Verification Contest")
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusHours(1))
                .status(ContestStatus.PUBLISHED)
                .createdBy(admin)
                .build());

        contestProblemRepository.saveAndFlush(ContestProblem.builder()
                .contest(contest)
                .problem(problemInContest)
                .ordinal(1)
                .build());

        // Same (contest, problem) pair as an existing contest_problems row -> allowed.
        CodingSubmission validSubmission = submissionRepository.saveAndFlush(CodingSubmission.builder()
                .problem(problemInContest)
                .student(student)
                .contest(contest)
                .language(ProgrammingLanguage.CPP)
                .sourceCode("int main() { return 0; }")
                .status(SubmissionStatus.PENDING)
                .build());
        assertThat(validSubmission.getId()).isNotNull();

        // problemNotInContest was never added to `contest` via contest_problems, so the
        // composite FK on coding_submissions must reject this at flush time.
        assertThatThrownBy(() -> submissionRepository.saveAndFlush(CodingSubmission.builder()
                        .problem(problemNotInContest)
                        .student(student)
                        .contest(contest)
                        .language(ProgrammingLanguage.CPP)
                        .sourceCode("int main() { return 0; }")
                        .status(SubmissionStatus.PENDING)
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // §69 anti-fake-verdict constraint: chk_coding_submissions_accepted_is_earned
    // ------------------------------------------------------------------

    @Test
    void acceptedStatus_withZeroTestCasesRun_isRejectedByTheDatabase() {
        User admin = persistAdmin();
        Student student = persistStudent();
        CodingProblem problem = persistProblem(admin);

        assertThatThrownBy(() -> submissionRepository.saveAndFlush(CodingSubmission.builder()
                        .problem(problem)
                        .student(student)
                        .contest(null)
                        .language(ProgrammingLanguage.JAVA)
                        .sourceCode("public class Main { public static void main(String[] a) {} }")
                        .status(SubmissionStatus.ACCEPTED)
                        .passedTestCases(0)
                        .totalTestCases(0)
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
