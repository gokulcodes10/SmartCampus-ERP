package smartcampus.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.SubmissionDetailResponse;
import smartcampus.dto.SubmissionTestResultResponse;
import smartcampus.entity.CodingContest;
import smartcampus.entity.CodingProblem;
import smartcampus.entity.CodingSubmission;
import smartcampus.entity.ProblemTestCase;
import smartcampus.entity.ProgrammingLanguage;
import smartcampus.entity.Student;
import smartcampus.entity.SubmissionStatus;
import smartcampus.entity.SubmissionTestResult;
import smartcampus.entity.User;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.CodingContestRepository;
import smartcampus.repository.CodingProblemRepository;
import smartcampus.repository.CodingSubmissionRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.SubmissionTestResultRepository;

/**
 * All persistence for one coding submission's lifecycle, in its own bean so every
 * method truly commits independently — see {@link CodingSubmissionService} for why.
 *
 * <p><b>THE TRANSACTION SPLIT (this project's Phase 2 brute-force-counter trap, in a
 * new shape).</b> {@code CodingSubmissionService.submit(...)} calls out to an external
 * HTTP judge between recording the PENDING row and recording the verdict. If that
 * whole sequence lived in one {@code @Transactional} method, a thrown exception from
 * the judge call would roll back the PENDING row along with everything else — the
 * student's attempt would silently vanish from their history, exactly like the
 * brute-force cap that never engaged in Phase 2. Because a {@code @Transactional}
 * method invoked on {@code this} bypasses the Spring AOP proxy entirely and would
 * silently not even start a new transaction, the fix isn't a try/catch inside one
 * method — it's this separate bean, injected into the orchestrating service, so each
 * of {@link #createPending}, {@link #recordVerdict} and {@link #recordFailure} really
 * does commit on its own before control returns to the uncommitted, non-transactional
 * orchestration in {@code CodingSubmissionService.submit}.
 */
@Service
@RequiredArgsConstructor
public class CodingSubmissionRecorder {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 4000;

    private final CodingSubmissionRepository codingSubmissionRepository;
    private final SubmissionTestResultRepository submissionTestResultRepository;
    private final CodingProblemRepository codingProblemRepository;
    private final StudentRepository studentRepository;
    private final CodingContestRepository codingContestRepository;

    /** Inserts the PENDING row and commits. Returns its id. */
    @Transactional
    public Long createPending(
            Long problemId,
            Long studentId,
            Long contestId,
            ProgrammingLanguage language,
            String sourceCode) {
        CodingProblem problem = codingProblemRepository.getReferenceById(problemId);
        Student student = studentRepository.getReferenceById(studentId);
        CodingContest contest =
                contestId != null ? codingContestRepository.getReferenceById(contestId) : null;

        CodingSubmission submission =
                CodingSubmission.builder()
                        .problem(problem)
                        .student(student)
                        .contest(contest)
                        .language(language)
                        .sourceCode(sourceCode)
                        .build();
        submission = codingSubmissionRepository.saveAndFlush(submission);
        return submission.getId();
    }

    /**
     * Writes the per-case evidence and the aggregate verdict computed from it, and
     * commits. {@code results} is index-aligned with {@code cases} — both ordered by
     * ordinal ascending, exactly as {@link CodeExecutionService#executeBatch} promises.
     *
     * <p>NEVER PERSISTS actual/stderr output for a hidden case (G3) — see the
     * per-result loop below.
     */
    @Transactional
    public void recordVerdict(
            Long submissionId, List<ProblemTestCase> cases, List<ExecutionResult> results) {
        CodingSubmission submission = requireSubmission(submissionId);

        int total = cases.size();
        int maxScore = cases.stream().mapToInt(ProblemTestCase::getWeight).sum();

        List<SubmissionTestResult> perCase = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            ProblemTestCase testCase = cases.get(i);
            ExecutionResult result = results.get(i);
            boolean sample = testCase.isSample();
            perCase.add(
                    SubmissionTestResult.builder()
                            .submission(submission)
                            .testCase(testCase)
                            .ordinal(testCase.getOrdinal())
                            .sample(sample)
                            .status(result.status())
                            .executionTimeMs(result.executionTimeMs())
                            .memoryKb(result.memoryKb())
                            .judge0Token(result.token())
                            .judge0StatusId(result.judge0StatusId())
                            // A hidden case's expected output must never leak through
                            // stored actual/stderr output (G3) — only sample cases are
                            // ever persisted here.
                            .actualOutput(sample ? result.stdout() : null)
                            .stderrOutput(sample ? result.stderr() : null)
                            .build());
        }
        submissionTestResultRepository.saveAll(perCase);

        boolean anyCompileError =
                results.stream().anyMatch(r -> r.status() == SubmissionStatus.COMPILATION_ERROR);

        int passed = 0;
        int score = 0;
        SubmissionStatus aggregateStatus;
        Integer failedOrdinal;

        if (anyCompileError) {
            // Judge0 returns COMPILATION_ERROR for every case of a program that
            // failed to compile — nothing "passed" in any meaningful sense.
            aggregateStatus = SubmissionStatus.COMPILATION_ERROR;
            failedOrdinal = null;
        } else {
            for (int i = 0; i < total; i++) {
                if (results.get(i).status() == SubmissionStatus.ACCEPTED) {
                    passed++;
                    score += cases.get(i).getWeight();
                }
            }
            if (total > 0 && passed == total) {
                aggregateStatus = SubmissionStatus.ACCEPTED;
                failedOrdinal = null;
            } else {
                // First non-accepted result in ordinal order wins — a TLE on case 3
                // with cases 1-2 passing is reported as TLE at case 3, not whatever
                // came last.
                SubmissionStatus firstBad = SubmissionStatus.INTERNAL_ERROR;
                Integer badOrdinal = null;
                for (int i = 0; i < total; i++) {
                    if (results.get(i).status() != SubmissionStatus.ACCEPTED) {
                        firstBad = results.get(i).status();
                        badOrdinal = cases.get(i).getOrdinal();
                        break;
                    }
                }
                aggregateStatus = firstBad;
                failedOrdinal = badOrdinal;
            }
        }

        Integer executionTimeMs =
                results.stream()
                        .map(ExecutionResult::executionTimeMs)
                        .filter(Objects::nonNull)
                        .max(Comparator.naturalOrder())
                        .orElse(null);
        Integer memoryKb =
                results.stream()
                        .map(ExecutionResult::memoryKb)
                        .filter(Objects::nonNull)
                        .max(Comparator.naturalOrder())
                        .orElse(null);
        String compileOutput =
                results.stream()
                        .map(ExecutionResult::compileOutput)
                        .filter(s -> s != null && !s.isBlank())
                        .findFirst()
                        .orElse(null);

        submission.setStatus(aggregateStatus);
        submission.setPassedTestCases(passed);
        submission.setTotalTestCases(total);
        submission.setScore(score);
        submission.setMaxScore(maxScore);
        submission.setExecutionTimeMs(executionTimeMs);
        submission.setMemoryKb(memoryKb);
        submission.setFailedTestCaseOrdinal(failedOrdinal);
        submission.setCompileOutput(compileOutput);
        submission.setJudgedAt(LocalDateTime.now());

        codingSubmissionRepository.save(submission);
    }

    /**
     * The honest outcome when the judge could not be reached: writes {@code
     * INTERNAL_ERROR} with the real reason and commits. Never throws — the caller
     * (which caught {@link smartcampus.exception.CodeExecutionUnavailableException} to
     * get here) must be able to return this attempt to the client rather than lose it.
     */
    @Transactional
    public void recordFailure(Long submissionId, String errorMessage) {
        CodingSubmission submission = requireSubmission(submissionId);
        String truncated =
                errorMessage != null && errorMessage.length() > ERROR_MESSAGE_MAX_LENGTH
                        ? errorMessage.substring(0, ERROR_MESSAGE_MAX_LENGTH)
                        : errorMessage;
        submission.setStatus(SubmissionStatus.INTERNAL_ERROR);
        submission.setErrorMessage(truncated);
        submission.setPassedTestCases(0);
        submission.setTotalTestCases(0);
        submission.setScore(0);
        submission.setMaxScore(0);
        submission.setJudgedAt(LocalDateTime.now());
        codingSubmissionRepository.save(submission);
    }

    /**
     * Full detail view, including the per-case breakdown. {@code revealHidden} is true
     * only for an ADMIN caller — everyone else gets {@code null} input/expected/actual/
     * stderr for a hidden case (G3), even though the row exists in the database.
     */
    @Transactional(readOnly = true)
    public SubmissionDetailResponse detail(Long submissionId, boolean revealHidden) {
        CodingSubmission submission = requireSubmission(submissionId);
        List<SubmissionTestResult> results =
                submissionTestResultRepository.findBySubmissionIdOrderByOrdinalAsc(submissionId);

        CodingProblem problem = submission.getProblem();
        Student student = submission.getStudent();
        User studentUser = student.getUser();
        CodingContest contest = submission.getContest();

        List<SubmissionTestResultResponse> testResults =
                results.stream().map(r -> toTestResultResponse(r, revealHidden)).toList();

        return new SubmissionDetailResponse(
                submission.getId(),
                problem.getId(),
                problem.getTitle(),
                problem.getDifficulty(),
                student.getId(),
                studentUser != null ? studentUser.getFullName() : null,
                student.getRegisterNumber(),
                contest != null ? contest.getId() : null,
                contest != null ? contest.getTitle() : null,
                submission.getLanguage(),
                submission.getStatus(),
                submission.getPassedTestCases(),
                submission.getTotalTestCases(),
                submission.getScore(),
                submission.getMaxScore(),
                submission.getExecutionTimeMs(),
                submission.getMemoryKb(),
                submission.getFailedTestCaseOrdinal(),
                submission.getCreatedAt(),
                submission.getSourceCode(),
                submission.getCompileOutput(),
                submission.getErrorMessage(),
                submission.getJudgedAt(),
                testResults);
    }

    private SubmissionTestResultResponse toTestResultResponse(
            SubmissionTestResult result, boolean revealHidden) {
        boolean sample = result.isSample();
        boolean reveal = sample || revealHidden;
        return new SubmissionTestResultResponse(
                result.getOrdinal(),
                sample,
                result.getStatus(),
                result.getStatus() == SubmissionStatus.ACCEPTED,
                result.getExecutionTimeMs(),
                result.getMemoryKb(),
                reveal ? result.getTestCase().getInput() : null,
                reveal ? result.getTestCase().getExpectedOutput() : null,
                reveal ? result.getActualOutput() : null,
                reveal ? result.getStderrOutput() : null);
    }

    private CodingSubmission requireSubmission(Long submissionId) {
        return codingSubmissionRepository
                .findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found."));
    }
}
