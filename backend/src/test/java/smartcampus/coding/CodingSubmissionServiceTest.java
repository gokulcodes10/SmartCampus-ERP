package smartcampus.coding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import smartcampus.dto.SubmissionCreateRequest;
import smartcampus.dto.SubmissionDetailResponse;
import smartcampus.entity.CodingProblem;
import smartcampus.entity.CodingSubmission;
import smartcampus.entity.ProblemDifficulty;
import smartcampus.entity.ProblemTestCase;
import smartcampus.entity.ProgrammingLanguage;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.SubmissionStatus;
import smartcampus.entity.SubmissionTestResult;
import smartcampus.entity.User;
import smartcampus.exception.CodeExecutionUnavailableException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.CodingContestRepository;
import smartcampus.repository.CodingProblemRepository;
import smartcampus.repository.CodingSubmissionRepository;
import smartcampus.repository.ContestParticipantRepository;
import smartcampus.repository.ContestProblemRepository;
import smartcampus.repository.ProblemTestCaseRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.SubmissionTestResultRepository;
import smartcampus.service.CodeExecutionService;
import smartcampus.service.CodingSubmissionRecorder;
import smartcampus.service.CodingSubmissionService;
import smartcampus.service.ContestScoringService;
import smartcampus.service.ExecutionResult;

/**
 * Unit tests for {@link CodingSubmissionService} and {@link CodingSubmissionRecorder}
 * wired together, with {@link CodeExecutionService} stubbed via Mockito — no live
 * Judge0 required (see PROJECT_PLAN.md clarification G10: Judge0 cannot run on this
 * machine). Repositories are Mockito-backed in-memory fakes so the real persistence
 * logic in {@code CodingSubmissionRecorder} actually runs, which is what makes the
 * verdict-aggregation and hidden-case-redaction assertions below meaningful rather
 * than tautological.
 *
 * <p>Together, the "correct solution" and "wrong solution" cases below are the closest
 * this machine can come to the deferred end-to-end Judge0 checkpoint — see this
 * class's owning report for why live execution could not be verified here.
 */
@ExtendWith(MockitoExtension.class)
class CodingSubmissionServiceTest {

    @Mock private CodingProblemRepository codingProblemRepository;
    @Mock private ProblemTestCaseRepository problemTestCaseRepository;
    @Mock private CodingSubmissionRepository codingSubmissionRepository;
    @Mock private CodingContestRepository codingContestRepository;
    @Mock private ContestProblemRepository contestProblemRepository;
    @Mock private ContestParticipantRepository contestParticipantRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private SubmissionTestResultRepository submissionTestResultRepository;
    @Mock private CodeExecutionService codeExecutionService;
    @Mock private ContestScoringService contestScoringService;

    private CodingSubmissionService service;

    private User studentUser;
    private Student student;
    private CodingProblem problem;

    /** In-memory fakes standing in for the database, keyed exactly like the real tables. */
    private final Map<Long, CodingSubmission> submissionStore = new HashMap<>();

    private final AtomicLong submissionIdSequence = new AtomicLong();
    private List<SubmissionTestResult> lastSavedTestResults = new ArrayList<>();

    @BeforeEach
    void setUp() {
        studentUser =
                User.builder()
                        .id(10L)
                        .email("alice@example.com")
                        .password("hash")
                        .fullName("Alice Student")
                        .role(Role.STUDENT)
                        .enabled(true)
                        .build();
        student =
                Student.builder()
                        .id(100L)
                        .user(studentUser)
                        .registerNumber("R100")
                        .status(StudentStatus.ACTIVE)
                        .build();
        User adminUser =
                User.builder()
                        .id(1L)
                        .email("admin@example.com")
                        .password("hash")
                        .fullName("Admin")
                        .role(Role.ADMIN)
                        .enabled(true)
                        .build();
        problem =
                CodingProblem.builder()
                        .id(1L)
                        .slug("two-sum")
                        .title("Two Sum")
                        .description("Add two numbers.")
                        .difficulty(ProblemDifficulty.EASY)
                        .timeLimitMs(2000)
                        .memoryLimitKb(262144)
                        .published(true)
                        .createdBy(adminUser)
                        .build();

        // -- Repository fakes: createPending() resolves problem/student via getReferenceById --
        lenient().when(codingProblemRepository.getReferenceById(1L)).thenReturn(problem);
        lenient().when(codingProblemRepository.findById(1L)).thenReturn(Optional.of(problem));
        lenient().when(studentRepository.getReferenceById(100L)).thenReturn(student);
        lenient().when(studentRepository.findByUserId(10L)).thenReturn(Optional.of(student));

        // -- CodingSubmission persistence: an in-memory map keyed by a generated id --
        lenient()
                .when(codingSubmissionRepository.saveAndFlush(any(CodingSubmission.class)))
                .thenAnswer(
                        inv -> {
                            CodingSubmission s = inv.getArgument(0);
                            if (s.getId() == null) {
                                s.setId(submissionIdSequence.incrementAndGet());
                            }
                            submissionStore.put(s.getId(), s);
                            return s;
                        });
        lenient()
                .when(codingSubmissionRepository.save(any(CodingSubmission.class)))
                .thenAnswer(
                        inv -> {
                            CodingSubmission s = inv.getArgument(0);
                            submissionStore.put(s.getId(), s);
                            return s;
                        });
        lenient()
                .when(codingSubmissionRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(submissionStore.get((Long) inv.getArgument(0))));

        // -- SubmissionTestResult persistence: capture what was actually written --
        lenient()
                .when(submissionTestResultRepository.saveAll(any()))
                .thenAnswer(
                        inv -> {
                            List<SubmissionTestResult> saved = new ArrayList<>();
                            ((Iterable<SubmissionTestResult>) inv.getArgument(0)).forEach(saved::add);
                            lastSavedTestResults = saved;
                            return saved;
                        });
        lenient()
                .when(submissionTestResultRepository.findBySubmissionIdOrderByOrdinalAsc(anyLong()))
                .thenAnswer(inv -> lastSavedTestResults);

        CodingSubmissionRecorder recorder =
                new CodingSubmissionRecorder(
                        codingSubmissionRepository,
                        submissionTestResultRepository,
                        codingProblemRepository,
                        studentRepository,
                        codingContestRepository);

        service =
                new CodingSubmissionService(
                        codingProblemRepository,
                        problemTestCaseRepository,
                        codingSubmissionRepository,
                        codingContestRepository,
                        contestProblemRepository,
                        contestParticipantRepository,
                        studentRepository,
                        codeExecutionService,
                        recorder,
                        contestScoringService,
                        62,
                        54);
    }

    private ProblemTestCase testCase(long id, int ordinal, boolean sample, int weight) {
        return ProblemTestCase.builder()
                .id(id)
                .problem(problem)
                .ordinal(ordinal)
                .input("in" + ordinal)
                .expectedOutput("out" + ordinal)
                .sample(sample)
                .weight(weight)
                .build();
    }

    private ExecutionResult result(SubmissionStatus status) {
        return new ExecutionResult(status, null, null, "stdout", "stderr", null, null, 100, 1024, "token");
    }

    private SubmissionCreateRequest practiceRequest() {
        return new SubmissionCreateRequest(1L, ProgrammingLanguage.JAVA, "public class Main {}", null);
    }

    @Test
    void correctSolutionIsRecordedAsAccepted() {
        List<ProblemTestCase> cases = List.of(testCase(1, 1, true, 2), testCase(2, 2, false, 3));
        when(problemTestCaseRepository.findByProblemIdOrderByOrdinalAsc(1L)).thenReturn(cases);
        when(codeExecutionService.executeBatch(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(result(SubmissionStatus.ACCEPTED), result(SubmissionStatus.ACCEPTED)));

        SubmissionDetailResponse response = service.submit(practiceRequest(), studentUser);

        assertThat(response.status()).isEqualTo(SubmissionStatus.ACCEPTED);
        assertThat(response.passedTestCases()).isEqualTo(2);
        assertThat(response.totalTestCases()).isEqualTo(2);
        assertThat(response.score()).isEqualTo(response.maxScore());
        assertThat(response.score()).isEqualTo(5);
        assertThat(response.failedTestCaseOrdinal()).isNull();
    }

    @Test
    void wrongSolutionFailsOnFirstNonAcceptedCaseInOrdinalOrder() {
        // case 1 (ordinal 1, weight 3) ACCEPTED; case 2 (ordinal 2, weight 5) WRONG_ANSWER.
        List<ProblemTestCase> cases = List.of(testCase(1, 1, true, 3), testCase(2, 2, false, 5));
        when(problemTestCaseRepository.findByProblemIdOrderByOrdinalAsc(1L)).thenReturn(cases);
        when(codeExecutionService.executeBatch(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(result(SubmissionStatus.ACCEPTED), result(SubmissionStatus.WRONG_ANSWER)));

        SubmissionDetailResponse response = service.submit(practiceRequest(), studentUser);

        assertThat(response.status()).isEqualTo(SubmissionStatus.WRONG_ANSWER);
        assertThat(response.passedTestCases()).isEqualTo(1);
        assertThat(response.totalTestCases()).isEqualTo(2);
        assertThat(response.failedTestCaseOrdinal()).isEqualTo(2);
        // Score is only case 1's weight (3), not the sum of both (8).
        assertThat(response.score()).isEqualTo(3);
        assertThat(response.maxScore()).isEqualTo(8);
    }

    @Test
    void compileErrorZeroesOutPassedAndScore() {
        List<ProblemTestCase> cases = List.of(testCase(1, 1, true, 1), testCase(2, 2, false, 1));
        when(problemTestCaseRepository.findByProblemIdOrderByOrdinalAsc(1L)).thenReturn(cases);
        ExecutionResult compileError =
                new ExecutionResult(
                        SubmissionStatus.COMPILATION_ERROR,
                        6,
                        "Compilation Error",
                        null,
                        null,
                        "error: cannot find symbol",
                        null,
                        null,
                        null,
                        "token");
        when(codeExecutionService.executeBatch(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(compileError, compileError));

        SubmissionDetailResponse response = service.submit(practiceRequest(), studentUser);

        assertThat(response.status()).isEqualTo(SubmissionStatus.COMPILATION_ERROR);
        assertThat(response.passedTestCases()).isEqualTo(0);
        assertThat(response.score()).isEqualTo(0);
        assertThat(response.compileOutput()).isEqualTo("error: cannot find symbol");
        assertThat(response.failedTestCaseOrdinal()).isNull();
    }

    @Test
    void timeLimitExceededOnThirdCaseReportsThatOrdinalNotTheLast() {
        List<ProblemTestCase> cases =
                List.of(testCase(1, 1, true, 1), testCase(2, 2, false, 1), testCase(3, 3, false, 1));
        when(problemTestCaseRepository.findByProblemIdOrderByOrdinalAsc(1L)).thenReturn(cases);
        when(codeExecutionService.executeBatch(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(
                        List.of(
                                result(SubmissionStatus.ACCEPTED),
                                result(SubmissionStatus.ACCEPTED),
                                result(SubmissionStatus.TIME_LIMIT_EXCEEDED)));

        SubmissionDetailResponse response = service.submit(practiceRequest(), studentUser);

        assertThat(response.status()).isEqualTo(SubmissionStatus.TIME_LIMIT_EXCEEDED);
        assertThat(response.failedTestCaseOrdinal()).isEqualTo(3);
        assertThat(response.passedTestCases()).isEqualTo(2);
    }

    @Test
    void judgeUnreachableStillPersistsTheAttemptAsInternalErrorAndDoesNotThrow() {
        List<ProblemTestCase> cases = List.of(testCase(1, 1, true, 1));
        when(problemTestCaseRepository.findByProblemIdOrderByOrdinalAsc(1L)).thenReturn(cases);
        when(codeExecutionService.executeBatch(any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(
                        new CodeExecutionUnavailableException(
                                "Judge0 at http://localhost:2358 could not be reached (Connection refused)."));

        SubmissionDetailResponse response = service.submit(practiceRequest(), studentUser);

        // The rollback trap: the row must exist, not have vanished with the exception.
        assertThat(submissionStore).containsKey(response.id());
        assertThat(response.status()).isEqualTo(SubmissionStatus.INTERNAL_ERROR);
        assertThat(response.errorMessage()).isNotBlank();
    }

    @Test
    void hiddenCaseOutputIsNeverPersistedOrRevealedToANonAdmin() {
        ProblemTestCase sample = testCase(1, 1, true, 1);
        ProblemTestCase hidden = testCase(2, 2, false, 1);
        when(problemTestCaseRepository.findByProblemIdOrderByOrdinalAsc(1L))
                .thenReturn(List.of(sample, hidden));
        ExecutionResult sampleResult =
                new ExecutionResult(
                        SubmissionStatus.ACCEPTED, 3, "Accepted", "42", "", null, null, 10, 100, "tok-1");
        ExecutionResult hiddenResult =
                new ExecutionResult(
                        SubmissionStatus.ACCEPTED,
                        3,
                        "Accepted",
                        "hidden-secret-output",
                        "hidden-secret-stderr",
                        null,
                        null,
                        10,
                        100,
                        "tok-2");
        when(codeExecutionService.executeBatch(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(sampleResult, hiddenResult));

        SubmissionDetailResponse response = service.submit(practiceRequest(), studentUser);

        // Persisted evidence: the hidden case's actual/stderr output was never stored.
        SubmissionTestResult persistedHidden =
                lastSavedTestResults.stream().filter(r -> r.getOrdinal() == 2).findFirst().orElseThrow();
        assertThat(persistedHidden.getActualOutput()).isNull();
        assertThat(persistedHidden.getStderrOutput()).isNull();
        SubmissionTestResult persistedSample =
                lastSavedTestResults.stream().filter(r -> r.getOrdinal() == 1).findFirst().orElseThrow();
        assertThat(persistedSample.getActualOutput()).isEqualTo("42");

        // Response for a non-admin (STUDENT) caller: hidden case is fully redacted.
        var hiddenResponse =
                response.testResults().stream().filter(r -> r.ordinal() == 2).findFirst().orElseThrow();
        assertThat(hiddenResponse.input()).isNull();
        assertThat(hiddenResponse.expectedOutput()).isNull();
        assertThat(hiddenResponse.actualOutput()).isNull();
        assertThat(hiddenResponse.stderrOutput()).isNull();
        assertThat(hiddenResponse.status()).isEqualTo(SubmissionStatus.ACCEPTED);
        assertThat(hiddenResponse.passed()).isTrue();

        var sampleResponse =
                response.testResults().stream().filter(r -> r.ordinal() == 1).findFirst().orElseThrow();
        assertThat(sampleResponse.input()).isEqualTo("in1");
        assertThat(sampleResponse.expectedOutput()).isEqualTo("out1");
        assertThat(sampleResponse.actualOutput()).isEqualTo("42");
    }

    @Test
    void aStudentReadingAnotherStudentsSubmissionGetsNotFoundNotForbidden() {
        User otherUser =
                User.builder()
                        .id(20L)
                        .email("bob@example.com")
                        .password("hash")
                        .fullName("Bob Student")
                        .role(Role.STUDENT)
                        .enabled(true)
                        .build();
        Student otherStudent =
                Student.builder()
                        .id(200L)
                        .user(otherUser)
                        .registerNumber("R200")
                        .status(StudentStatus.ACTIVE)
                        .build();
        when(studentRepository.findByUserId(20L)).thenReturn(Optional.of(otherStudent));

        CodingSubmission owned =
                CodingSubmission.builder()
                        .id(999L)
                        .problem(problem)
                        .student(student)
                        .language(ProgrammingLanguage.JAVA)
                        .sourceCode("code")
                        .status(SubmissionStatus.ACCEPTED)
                        .passedTestCases(1)
                        .totalTestCases(1)
                        .score(1)
                        .maxScore(1)
                        .build();
        submissionStore.put(999L, owned);

        assertThatThrownBy(() -> service.getSubmission(999L, otherUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .isNotInstanceOf(AccessDeniedException.class);
    }
}
