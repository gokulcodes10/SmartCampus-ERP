package smartcampus.service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.PageResponse;
import smartcampus.dto.ProblemCreateRequest;
import smartcampus.dto.ProblemDetailResponse;
import smartcampus.dto.ProblemSummaryResponse;
import smartcampus.dto.ProblemUpdateRequest;
import smartcampus.dto.SampleTestCaseResponse;
import smartcampus.dto.TestCaseRequest;
import smartcampus.dto.TestCaseResponse;
import smartcampus.entity.CodingProblem;
import smartcampus.entity.ProblemDifficulty;
import smartcampus.entity.ProblemTestCase;
import smartcampus.entity.Role;
import smartcampus.entity.User;
import smartcampus.exception.BadRequestException;
import smartcampus.exception.DuplicateResourceException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.CodingProblemRepository;
import smartcampus.repository.ContestProblemRepository;
import smartcampus.repository.ProblemTestCaseRepository;

/**
 * Business logic behind {@code /api/problems} — authoring (ADMIN only, per README "For
 * Administrators": coding contest creation and problem authoring) and the visibility
 * rule every read routes through: an unpublished problem is invisible to anyone who
 * isn't ADMIN, and — per the project's R8 enumeration rule — a non-admin fetching one
 * by id gets {@link ResourceNotFoundException} (404), never a 403, so an id cannot be
 * probed to distinguish "unpublished" from "does not exist".
 *
 * <p>Nothing here calls {@code AcademicAccessGuard}: problems are institution-wide, not
 * scoped to a (subject, academicYear, semester, section) tuple, and faculty have no
 * write surface in the coding module at all (R7).
 */
@Service
@RequiredArgsConstructor
public class CodingProblemService {

    private final CodingProblemRepository codingProblemRepository;
    private final ProblemTestCaseRepository problemTestCaseRepository;
    private final ContestProblemRepository contestProblemRepository;

    // ---------------------------------------------------------------------------
    // Problems
    // ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<ProblemSummaryResponse> list(
            User caller,
            String search,
            ProblemDifficulty difficulty,
            String tag,
            Boolean published,
            Pageable pageable) {
        boolean admin = caller.getRole() == Role.ADMIN;

        Specification<CodingProblem> spec =
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    if (!admin) {
                        // Non-admin listing NEVER includes an unpublished problem,
                        // regardless of what the `published` query param asked for.
                        predicates.add(cb.isTrue(root.get("published")));
                    } else if (published != null) {
                        predicates.add(cb.equal(root.get("published"), published));
                    }
                    if (difficulty != null) {
                        predicates.add(cb.equal(root.get("difficulty"), difficulty));
                    }
                    if (tag != null && !tag.isBlank()) {
                        predicates.add(cb.like(cb.lower(root.get("tags")), "%" + tag.toLowerCase() + "%"));
                    }
                    if (search != null && !search.isBlank()) {
                        String pattern = "%" + search.toLowerCase() + "%";
                        predicates.add(
                                cb.or(
                                        cb.like(cb.lower(root.get("title")), pattern),
                                        cb.like(cb.lower(root.get("slug")), pattern)));
                    }
                    return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
                };

        Page<CodingProblem> page = codingProblemRepository.findAll(spec, pageable);
        return PageResponse.of(page, this::toSummary);
    }

    @Transactional(readOnly = true)
    public ProblemDetailResponse getById(Long id, User caller) {
        CodingProblem problem = loadViewable(id, caller);
        return toDetail(problem);
    }

    @Transactional
    public ProblemDetailResponse create(ProblemCreateRequest request, User caller) {
        requireAdmin(caller);
        if (codingProblemRepository.existsBySlug(request.slug())) {
            throw new DuplicateResourceException("A problem with this slug already exists.");
        }
        CodingProblem problem =
                CodingProblem.builder()
                        .slug(request.slug())
                        .title(request.title())
                        .description(request.description())
                        .inputFormat(request.inputFormat())
                        .outputFormat(request.outputFormat())
                        .constraintsText(request.constraintsText())
                        .sampleInput(request.sampleInput())
                        .sampleOutput(request.sampleOutput())
                        .difficulty(request.difficulty())
                        .timeLimitMs(request.timeLimitMs())
                        .memoryLimitKb(request.memoryLimitKb())
                        .tags(joinTags(request.tags()))
                        .published(request.published() != null && request.published())
                        .createdBy(caller)
                        .build();
        try {
            problem = codingProblemRepository.saveAndFlush(problem);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("A problem with this slug already exists.");
        }
        return toDetail(problem);
    }

    @Transactional
    public ProblemDetailResponse update(Long id, ProblemUpdateRequest request, User caller) {
        requireAdmin(caller);
        CodingProblem problem =
                codingProblemRepository
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Problem not found."));

        if (!problem.getSlug().equals(request.slug())
                && codingProblemRepository.existsBySlug(request.slug())) {
            throw new DuplicateResourceException("A problem with this slug already exists.");
        }

        problem.setSlug(request.slug());
        problem.setTitle(request.title());
        problem.setDescription(request.description());
        problem.setInputFormat(request.inputFormat());
        problem.setOutputFormat(request.outputFormat());
        problem.setConstraintsText(request.constraintsText());
        problem.setSampleInput(request.sampleInput());
        problem.setSampleOutput(request.sampleOutput());
        problem.setDifficulty(request.difficulty());
        problem.setTimeLimitMs(request.timeLimitMs());
        problem.setMemoryLimitKb(request.memoryLimitKb());
        problem.setTags(joinTags(request.tags()));
        problem.setPublished(request.published() != null && request.published());

        try {
            problem = codingProblemRepository.saveAndFlush(problem);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("A problem with this slug already exists.");
        }
        return toDetail(problem);
    }

    @Transactional
    public void delete(Long id, User caller) {
        requireAdmin(caller);
        CodingProblem problem =
                codingProblemRepository
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Problem not found."));
        if (contestProblemRepository.existsByProblemId(problem.getId())) {
            throw new BadRequestException(
                    "This problem is part of one or more contests and cannot be deleted. "
                            + "Remove it from every contest first.");
        }
        // A problem with existing submissions is RESTRICTed by the database FK; the
        // resulting DataIntegrityViolationException is turned into a 409 by
        // GlobalExceptionHandler, since CodingSubmissionRepository exposes no
        // "existsByProblemId" method for an explicit pre-check here.
        codingProblemRepository.delete(problem);
    }

    // ---------------------------------------------------------------------------
    // Test cases (ADMIN only, always)
    // ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TestCaseResponse> listTestCases(Long problemId, User caller) {
        requireAdmin(caller);
        CodingProblem problem = loadOrThrow(problemId);
        return problemTestCaseRepository.findByProblemIdOrderByOrdinalAsc(problem.getId()).stream()
                .map(this::toTestCaseResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public TestCaseResponse createTestCase(Long problemId, TestCaseRequest request, User caller) {
        requireAdmin(caller);
        CodingProblem problem = loadOrThrow(problemId);
        if (problemTestCaseRepository.existsByProblemIdAndOrdinal(problem.getId(), request.ordinal())) {
            throw new DuplicateResourceException(
                    "A test case with ordinal " + request.ordinal() + " already exists for this problem.");
        }
        ProblemTestCase testCase =
                ProblemTestCase.builder()
                        .problem(problem)
                        .ordinal(request.ordinal())
                        .input(request.input())
                        .expectedOutput(request.expectedOutput())
                        .sample(request.isSample())
                        .weight(request.weight())
                        .build();
        try {
            testCase = problemTestCaseRepository.saveAndFlush(testCase);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException(
                    "A test case with ordinal " + request.ordinal() + " already exists for this problem.");
        }
        return toTestCaseResponse(testCase);
    }

    @Transactional
    public TestCaseResponse updateTestCase(
            Long problemId, Long testCaseId, TestCaseRequest request, User caller) {
        requireAdmin(caller);
        CodingProblem problem = loadOrThrow(problemId);
        ProblemTestCase testCase =
                problemTestCaseRepository
                        .findByIdAndProblemId(testCaseId, problem.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Test case not found."));

        if (!testCase.getOrdinal().equals(request.ordinal())
                && problemTestCaseRepository.existsByProblemIdAndOrdinal(problem.getId(), request.ordinal())) {
            throw new DuplicateResourceException(
                    "A test case with ordinal " + request.ordinal() + " already exists for this problem.");
        }

        testCase.setOrdinal(request.ordinal());
        testCase.setInput(request.input());
        testCase.setExpectedOutput(request.expectedOutput());
        testCase.setSample(request.isSample());
        testCase.setWeight(request.weight());

        try {
            testCase = problemTestCaseRepository.saveAndFlush(testCase);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException(
                    "A test case with ordinal " + request.ordinal() + " already exists for this problem.");
        }
        return toTestCaseResponse(testCase);
    }

    @Transactional
    public void deleteTestCase(Long problemId, Long testCaseId, User caller) {
        requireAdmin(caller);
        CodingProblem problem = loadOrThrow(problemId);
        ProblemTestCase testCase =
                problemTestCaseRepository
                        .findByIdAndProblemId(testCaseId, problem.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Test case not found."));
        problemTestCaseRepository.delete(testCase);
    }

    // ---------------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------------

    /**
     * The one gate every by-id problem read routes through. An unpublished problem is
     * {@link ResourceNotFoundException} (404) for anyone who is not ADMIN — never a
     * 403 — so an id cannot be used to probe "exists but unpublished" vs "does not
     * exist" (R8).
     */
    CodingProblem loadViewable(Long id, User caller) {
        if (caller.getRole() == Role.ADMIN) {
            return loadOrThrow(id);
        }
        return codingProblemRepository
                .findByIdAndPublishedTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found."));
    }

    private CodingProblem loadOrThrow(Long id) {
        return codingProblemRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found."));
    }

    private void requireAdmin(User caller) {
        if (caller.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only administrators can perform this action.");
        }
    }

    private String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        String joined =
                tags.stream()
                        .filter(t -> t != null && !t.isBlank())
                        .map(String::trim)
                        .collect(Collectors.joining(","));
        if (joined.isBlank()) {
            return null;
        }
        if (joined.length() > 255) {
            throw new BadRequestException("Tags exceed the 255-character limit once joined.");
        }
        return joined;
    }

    static List<String> splitTags(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private ProblemSummaryResponse toSummary(CodingProblem problem) {
        long sampleCount =
                problemTestCaseRepository.findByProblemIdAndSampleTrueOrderByOrdinalAsc(problem.getId())
                        .size();
        long total = problemTestCaseRepository.countByProblemId(problem.getId());
        return new ProblemSummaryResponse(
                problem.getId(),
                problem.getSlug(),
                problem.getTitle(),
                problem.getDifficulty(),
                problem.getTimeLimitMs(),
                problem.getMemoryLimitKb(),
                splitTags(problem.getTags()),
                problem.isPublished(),
                sampleCount,
                total - sampleCount,
                problem.getCreatedAt(),
                problem.getUpdatedAt());
    }

    ProblemDetailResponse toDetail(CodingProblem problem) {
        List<ProblemTestCase> samples =
                problemTestCaseRepository.findByProblemIdAndSampleTrueOrderByOrdinalAsc(problem.getId());
        long total = problemTestCaseRepository.countByProblemId(problem.getId());
        User createdBy = problem.getCreatedBy();
        return new ProblemDetailResponse(
                problem.getId(),
                problem.getSlug(),
                problem.getTitle(),
                problem.getDescription(),
                problem.getInputFormat(),
                problem.getOutputFormat(),
                problem.getConstraintsText(),
                problem.getSampleInput(),
                problem.getSampleOutput(),
                problem.getDifficulty(),
                problem.getTimeLimitMs(),
                problem.getMemoryLimitKb(),
                splitTags(problem.getTags()),
                problem.isPublished(),
                createdBy != null ? createdBy.getId() : null,
                createdBy != null ? createdBy.getFullName() : null,
                samples.stream()
                        .map(
                                tc ->
                                        new SampleTestCaseResponse(
                                                tc.getId(), tc.getOrdinal(), tc.getInput(), tc.getExpectedOutput()))
                        .collect(Collectors.toList()),
                total - samples.size(),
                problem.getCreatedAt(),
                problem.getUpdatedAt());
    }

    private TestCaseResponse toTestCaseResponse(ProblemTestCase testCase) {
        return new TestCaseResponse(
                testCase.getId(),
                testCase.getProblem().getId(),
                testCase.getOrdinal(),
                testCase.getInput(),
                testCase.getExpectedOutput(),
                testCase.isSample(),
                testCase.getWeight(),
                testCase.getCreatedAt(),
                testCase.getUpdatedAt());
    }
}
