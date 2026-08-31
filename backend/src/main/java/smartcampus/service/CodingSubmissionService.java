package smartcampus.service;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.CodingStatsResponse;
import smartcampus.dto.LanguageResponse;
import smartcampus.dto.PageResponse;
import smartcampus.dto.RunRequest;
import smartcampus.dto.RunResponse;
import smartcampus.dto.SampleRunCaseResponse;
import smartcampus.dto.SampleRunResponse;
import smartcampus.dto.SubmissionCreateRequest;
import smartcampus.dto.SubmissionDetailResponse;
import smartcampus.dto.SubmissionSummaryResponse;
import smartcampus.entity.CodingContest;
import smartcampus.entity.CodingProblem;
import smartcampus.entity.CodingSubmission;
import smartcampus.entity.ContestStatus;
import smartcampus.entity.ProblemTestCase;
import smartcampus.entity.ProgrammingLanguage;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.SubmissionStatus;
import smartcampus.entity.User;
import smartcampus.exception.BadRequestException;
import smartcampus.exception.CodeExecutionUnavailableException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.CodingContestRepository;
import smartcampus.repository.CodingProblemRepository;
import smartcampus.repository.CodingSubmissionRepository;
import smartcampus.repository.ContestParticipantRepository;
import smartcampus.repository.ContestProblemRepository;
import smartcampus.repository.ProblemTestCaseRepository;
import smartcampus.repository.StudentRepository;

/**
 * Orchestrates the playground ("Run") endpoints and the graded submission flow
 * ({@code POST /api/coding/submissions}), plus submission history reads and
 * {@code GET /api/coding/stats/me}.
 *
 * <p><b>{@link #submit} is deliberately NOT {@code @Transactional}.</b> It creates the
 * PENDING submission row, calls out to the external judge (a slow, fallible HTTP
 * round-trip), and then records the verdict — three steps that each need to commit on
 * their own. If this method carried {@code @Transactional}, a thrown exception from
 * the judge call would roll back the PENDING row along with it, silently erasing the
 * student's attempt from their history — the exact shape of the Phase 2 brute-force
 * counter bug (a save immediately followed by a throw, rolled back together by
 * Spring's default unchecked-rollback rule). The persistence steps instead live in
 * {@link CodingSubmissionRecorder}, a separate {@code @Service} bean so each of its
 * methods commits through the real Spring proxy rather than a `this`-call that would
 * bypass it.
 *
 * <p>Nothing here calls {@code AcademicAccessGuard} — problems and contests are
 * institution-wide, not scoped to a (subject, academicYear, semester, section) tuple
 * (R7), and this class implements its own authorization directly.
 */
@Service
public class CodingSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(CodingSubmissionService.class);

    /** Fixed limits for the free-form playground "Run" — there is no problem to take them from. */
    private static final int PLAYGROUND_CPU_TIME_LIMIT_MS = 5000;

    private static final int PLAYGROUND_MEMORY_LIMIT_KB = 262144;

    private static final String JAVA_TEMPLATE =
            """
            import java.util.Scanner;

            public class Main {
                public static void main(String[] args) {
                    Scanner scanner = new Scanner(System.in);
                    // Read input with scanner.nextInt() / scanner.nextLine() / etc.
                    // and print your answer with System.out.println(...).
                }
            }
            """;

    private static final String CPP_TEMPLATE =
            """
            #include <iostream>
            using namespace std;

            int main() {
                // Read input with cin and print your answer with cout.
                return 0;
            }
            """;

    private final CodingProblemRepository codingProblemRepository;
    private final ProblemTestCaseRepository problemTestCaseRepository;
    private final CodingSubmissionRepository codingSubmissionRepository;
    private final CodingContestRepository codingContestRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final ContestParticipantRepository contestParticipantRepository;
    private final StudentRepository studentRepository;
    private final CodeExecutionService codeExecutionService;
    private final CodingSubmissionRecorder recorder;
    private final ContestScoringService contestScoringService;

    private final Integer javaLanguageId;
    private final Integer cppLanguageId;

    public CodingSubmissionService(
            CodingProblemRepository codingProblemRepository,
            ProblemTestCaseRepository problemTestCaseRepository,
            CodingSubmissionRepository codingSubmissionRepository,
            CodingContestRepository codingContestRepository,
            ContestProblemRepository contestProblemRepository,
            ContestParticipantRepository contestParticipantRepository,
            StudentRepository studentRepository,
            CodeExecutionService codeExecutionService,
            CodingSubmissionRecorder recorder,
            ContestScoringService contestScoringService,
            @Value("${smartcampus.judge0.language-id.java:62}") Integer javaLanguageId,
            @Value("${smartcampus.judge0.language-id.cpp:54}") Integer cppLanguageId) {
        this.codingProblemRepository = codingProblemRepository;
        this.problemTestCaseRepository = problemTestCaseRepository;
        this.codingSubmissionRepository = codingSubmissionRepository;
        this.codingContestRepository = codingContestRepository;
        this.contestProblemRepository = contestProblemRepository;
        this.contestParticipantRepository = contestParticipantRepository;
        this.studentRepository = studentRepository;
        this.codeExecutionService = codeExecutionService;
        this.recorder = recorder;
        this.contestScoringService = contestScoringService;
        this.javaLanguageId = javaLanguageId;
        this.cppLanguageId = cppLanguageId;
    }

    // ---------------------------------------------------------------------------
    // Languages / playground
    // ---------------------------------------------------------------------------

    public List<LanguageResponse> listLanguages() {
        return List.of(
                new LanguageResponse(ProgrammingLanguage.JAVA, "Java", javaLanguageId, "java", JAVA_TEMPLATE),
                new LanguageResponse(ProgrammingLanguage.CPP, "C++", cppLanguageId, "cpp", CPP_TEMPLATE));
    }

    /**
     * Free-form single execution against caller-supplied stdin. Persists nothing, so a
     * {@link CodeExecutionUnavailableException} is left to propagate as-is (503) —
     * there is no stored record that could become inconsistent.
     */
    public RunResponse run(RunRequest request, User caller) {
        ExecutionResult result =
                codeExecutionService.executeOnce(
                        request.language(),
                        request.sourceCode(),
                        request.stdin(),
                        PLAYGROUND_CPU_TIME_LIMIT_MS,
                        PLAYGROUND_MEMORY_LIMIT_KB);
        return new RunResponse(
                result.status(),
                result.judge0StatusId(),
                result.judge0StatusDescription(),
                result.stdout(),
                result.stderr(),
                result.compileOutput(),
                result.message(),
                result.executionTimeMs(),
                result.memoryKb());
    }

    /**
     * Runs a problem's SAMPLE cases only. {@code request.stdin()} is ignored — the
     * samples supply their own input. Persists nothing; a {@link
     * CodeExecutionUnavailableException} propagates as 503.
     */
    @Transactional(readOnly = true)
    public SampleRunResponse runSample(Long problemId, RunRequest request, User caller) {
        CodingProblem problem = loadViewableProblem(problemId, caller);
        List<ProblemTestCase> samples =
                problemTestCaseRepository.findByProblemIdAndSampleTrueOrderByOrdinalAsc(problem.getId());
        if (samples.isEmpty()) {
            // Nothing to run against; there is no meaningful "all passed" here, so
            // this deliberately does not report success.
            return new SampleRunResponse(List.of(), false);
        }

        List<ExecutionCase> execCases =
                samples.stream().map(tc -> new ExecutionCase(tc.getInput(), tc.getExpectedOutput())).toList();
        List<ExecutionResult> results =
                codeExecutionService.executeBatch(
                        request.language(),
                        request.sourceCode(),
                        execCases,
                        problem.getTimeLimitMs(),
                        problem.getMemoryLimitKb());

        List<SampleRunCaseResponse> caseResponses = new ArrayList<>(samples.size());
        boolean allPassed = true;
        for (int i = 0; i < samples.size(); i++) {
            ProblemTestCase testCase = samples.get(i);
            ExecutionResult result = results.get(i);
            boolean passed = result.status() == SubmissionStatus.ACCEPTED;
            allPassed = allPassed && passed;
            caseResponses.add(
                    new SampleRunCaseResponse(
                            testCase.getOrdinal(),
                            testCase.getInput(),
                            testCase.getExpectedOutput(),
                            result.stdout(),
                            result.stderr(),
                            result.status(),
                            passed,
                            result.executionTimeMs(),
                            result.memoryKb()));
        }
        return new SampleRunResponse(caseResponses, allPassed);
    }

    // ---------------------------------------------------------------------------
    // Graded submission
    // ---------------------------------------------------------------------------

    /**
     * NOT {@code @Transactional} — see the class javadoc. Always returns 201 once the
     * PENDING row exists, including when the judge is unreachable: the caller gets the
     * real {@code INTERNAL_ERROR} status and an honest {@code errorMessage} rather than
     * a thrown 503 and a vanished attempt.
     */
    public SubmissionDetailResponse submit(SubmissionCreateRequest request, User caller) {
        Student student = resolveActingStudent(caller);

        CodingProblem problem =
                codingProblemRepository
                        .findById(request.problemId())
                        .orElseThrow(() -> new ResourceNotFoundException("Problem not found."));
        if (!problem.isPublished() && caller.getRole() != Role.ADMIN) {
            throw new ResourceNotFoundException("Problem not found.");
        }

        List<ProblemTestCase> cases =
                problemTestCaseRepository.findByProblemIdOrderByOrdinalAsc(problem.getId());
        if (cases.isEmpty()) {
            // Reject BEFORE any row is created — total_test_cases == 0 with a judged
            // status must never reach the database.
            throw new BadRequestException("This problem has no test cases and cannot be judged.");
        }

        Long contestId = request.contestId();
        if (contestId != null) {
            validateContestSubmission(contestId, problem.getId(), student.getId());
        }

        Long submissionId =
                recorder.createPending(
                        problem.getId(), student.getId(), contestId, request.language(), request.sourceCode());

        List<ExecutionResult> results;
        try {
            List<ExecutionCase> execCases =
                    cases.stream()
                            .map(tc -> new ExecutionCase(tc.getInput(), tc.getExpectedOutput()))
                            .toList();
            results =
                    codeExecutionService.executeBatch(
                            request.language(),
                            request.sourceCode(),
                            execCases,
                            problem.getTimeLimitMs(),
                            problem.getMemoryLimitKb());
        } catch (CodeExecutionUnavailableException ex) {
            // The attempt is real and belongs in the history. Store the honest
            // failure and hand it back — never rethrow, never fabricate a verdict.
            recorder.recordFailure(submissionId, ex.getMessage());
            return recorder.detail(submissionId, caller.getRole() == Role.ADMIN);
        }

        recorder.recordVerdict(submissionId, cases, results);

        if (contestId != null) {
            // Runs for EVERY contest submission, accepted or not — a wrong attempt
            // still moves the penalty clock. A scoring hiccup must never erase the
            // verdict that was just committed above.
            try {
                contestScoringService.recomputeParticipant(contestId, student.getId());
            } catch (Exception ex) {
                log.error(
                        "Failed to recompute contest {} leaderboard for student {} after submission {}",
                        contestId,
                        student.getId(),
                        submissionId,
                        ex);
            }
        }

        return recorder.detail(submissionId, caller.getRole() == Role.ADMIN);
    }

    /**
     * Section 6, "Contest submission validation" — checked, in this exact order,
     * before any submission row is created, so the caller gets a 400 with a specific
     * sentence rather than the 409 the database's composite FK would otherwise produce.
     */
    private void validateContestSubmission(Long contestId, Long problemId, Long studentId) {
        contestParticipantRepository
                .findByContestIdAndStudentId(contestId, studentId)
                .orElseThrow(() -> new BadRequestException("You are not registered for this contest."));

        CodingContest contest =
                codingContestRepository
                        .findById(contestId)
                        .orElseThrow(() -> new ResourceNotFoundException("Contest not found."));
        if (contest.getStatus() != ContestStatus.PUBLISHED) {
            // The caller reaching this method is always a STUDENT (resolveActingStudent
            // already enforced that) so a DRAFT/CANCELLED contest is always invisible
            // to them — 404, matching the general problem/contest visibility rule (R8).
            throw new ResourceNotFoundException("Contest not found.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(contest.getStartTime()) || now.isAfter(contest.getEndTime())) {
            throw new BadRequestException("This contest is not currently running.");
        }
        if (!contestProblemRepository.existsByContestIdAndProblemId(contestId, problemId)) {
            // The database's composite FK enforces this too; this check exists purely
            // so the caller gets a 400 with a sentence instead of a raw 409.
            throw new BadRequestException("That problem is not part of this contest.");
        }
    }

    // ---------------------------------------------------------------------------
    // Reads
    // ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<SubmissionSummaryResponse> listSubmissions(
            User caller,
            Long problemId,
            Long contestId,
            SubmissionStatus status,
            Long studentId,
            Pageable pageable) {
        if (caller.getRole() == Role.FACULTY) {
            // Coding is not subject-scoped (R7), so there is no tuple that would scope
            // a faculty read here, and handing every student's source code to any
            // faculty member is not a rule anyone asked for.
            throw new AccessDeniedException("Faculty cannot view coding submissions.");
        }

        Long effectiveStudentId =
                caller.getRole() == Role.STUDENT
                        ? resolveOwnStudentId(caller) // a STUDENT-supplied studentId param is ignored
                        : studentId; // ADMIN: honoured, may be null for "any"

        Specification<CodingSubmission> spec =
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    if (problemId != null) {
                        predicates.add(cb.equal(root.get("problem").get("id"), problemId));
                    }
                    if (contestId != null) {
                        predicates.add(cb.equal(root.get("contest").get("id"), contestId));
                    }
                    if (status != null) {
                        predicates.add(cb.equal(root.get("status"), status));
                    }
                    if (effectiveStudentId != null) {
                        predicates.add(cb.equal(root.get("student").get("id"), effectiveStudentId));
                    }
                    return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
                };

        Page<CodingSubmission> page = codingSubmissionRepository.findAll(spec, pageable);
        return PageResponse.of(page, this::toSummary);
    }

    /** Owner or ADMIN; anyone else — including FACULTY — gets 404, never 403 (R8). */
    @Transactional(readOnly = true)
    public SubmissionDetailResponse getSubmission(Long id, User caller) {
        CodingSubmission submission =
                codingSubmissionRepository
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Submission not found."));
        boolean admin = caller.getRole() == Role.ADMIN;
        if (!admin) {
            Student callerStudent = resolveOwnStudentIfStudent(caller);
            if (callerStudent == null || !submission.getStudent().getId().equals(callerStudent.getId())) {
                throw new ResourceNotFoundException("Submission not found.");
            }
        }
        return recorder.detail(id, admin);
    }

    @Transactional(readOnly = true)
    public CodingStatsResponse getStats(User caller) {
        if (caller.getRole() != Role.STUDENT) {
            throw new AccessDeniedException("Only students have coding statistics.");
        }
        Student student =
                studentRepository
                        .findByUserId(caller.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Student profile not found."));

        long total = codingSubmissionRepository.countByStudentId(student.getId());
        long accepted =
                codingSubmissionRepository.countByStudentIdAndStatus(student.getId(), SubmissionStatus.ACCEPTED);
        long attempted = codingSubmissionRepository.findAttemptedProblemIds(student.getId()).size();
        List<Long> solvedIds = codingSubmissionRepository.findSolvedProblemIds(student.getId());
        long solved = solvedIds.size();

        long easy = 0;
        long medium = 0;
        long hard = 0;
        if (!solvedIds.isEmpty()) {
            for (CodingProblem problem : codingProblemRepository.findAllById(solvedIds)) {
                switch (problem.getDifficulty()) {
                    case EASY -> easy++;
                    case MEDIUM -> medium++;
                    case HARD -> hard++;
                }
            }
        }

        return new CodingStatsResponse(total, accepted, attempted, solved, easy, medium, hard);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Section 6, "Who is a student here": no {@code students} row (ADMIN/FACULTY) is
     * 403; an {@code INACTIVE} student row is 403; {@code PENDING} and {@code ACTIVE}
     * may both submit.
     */
    private Student resolveActingStudent(User caller) {
        if (caller.getRole() != Role.STUDENT) {
            throw new AccessDeniedException("Only students can submit solutions.");
        }
        Student student =
                studentRepository
                        .findByUserId(caller.getId())
                        .orElseThrow(() -> new AccessDeniedException("Only students can submit solutions."));
        if (student.getStatus() == StudentStatus.INACTIVE) {
            throw new AccessDeniedException("This student account is deactivated.");
        }
        return student;
    }

    private Long resolveOwnStudentId(User caller) {
        return studentRepository
                .findByUserId(caller.getId())
                .map(Student::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found."));
    }

    private Student resolveOwnStudentIfStudent(User caller) {
        if (caller.getRole() != Role.STUDENT) {
            return null;
        }
        return studentRepository.findByUserId(caller.getId()).orElse(null);
    }

    private CodingProblem loadViewableProblem(Long id, User caller) {
        if (caller.getRole() == Role.ADMIN) {
            return codingProblemRepository
                    .findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Problem not found."));
        }
        return codingProblemRepository
                .findByIdAndPublishedTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found."));
    }

    private SubmissionSummaryResponse toSummary(CodingSubmission submission) {
        CodingProblem problem = submission.getProblem();
        Student student = submission.getStudent();
        User studentUser = student.getUser();
        CodingContest contest = submission.getContest();
        return new SubmissionSummaryResponse(
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
                submission.getCreatedAt());
    }
}
