package smartcampus.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.PageResponse;
import smartcampus.dto.ProblemCreateRequest;
import smartcampus.dto.ProblemDetailResponse;
import smartcampus.dto.ProblemSummaryResponse;
import smartcampus.dto.ProblemUpdateRequest;
import smartcampus.dto.TestCaseRequest;
import smartcampus.dto.TestCaseResponse;
import smartcampus.entity.ProblemDifficulty;
import smartcampus.entity.User;
import smartcampus.service.CodingProblemService;

/**
 * {@code /api/problems} — problem authoring (ADMIN only, per README "For
 * Administrators": coding contest creation and problem authoring) and problem reads
 * (any authenticated caller, narrowed to published problems for everyone but ADMIN).
 *
 * <p>Route-level restriction is deliberately thin, matching this project's house
 * style ({@code StudentController}/{@code StudentService}): role and visibility are
 * enforced once, centrally, in {@link CodingProblemService}, not re-implemented here.
 * Method security ({@code @PreAuthorize}) is not enabled on this build, so the real
 * ADMIN gate for the write endpoints below must additionally be applied at the route
 * level in {@code SecurityConfig} — see this class's javadoc for the exact rules
 * needed; that file is owned by the integrator.
 */
@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final CodingProblemService codingProblemService;

    @GetMapping
    public ResponseEntity<PageResponse<ProblemSummaryResponse>> list(
            @AuthenticationPrincipal User caller,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProblemDifficulty difficulty,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Boolean published,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(
                codingProblemService.list(caller, search, difficulty, tag, published, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProblemDetailResponse> getById(
            @PathVariable Long id, @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(codingProblemService.getById(id, caller));
    }

    /** ADMIN only. */
    @PostMapping
    public ResponseEntity<ProblemDetailResponse> create(
            @Valid @RequestBody ProblemCreateRequest request, @AuthenticationPrincipal User caller) {
        ProblemDetailResponse created = codingProblemService.create(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** ADMIN only. */
    @PutMapping("/{id}")
    public ResponseEntity<ProblemDetailResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProblemUpdateRequest request,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(codingProblemService.update(id, request, caller));
    }

    /** ADMIN only. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        codingProblemService.delete(id, caller);
        return ResponseEntity.noContent().build();
    }

    /**
     * ADMIN only — the one route that ever returns a hidden test case's input/expected
     * output.
     */
    @GetMapping("/{id}/test-cases")
    public ResponseEntity<List<TestCaseResponse>> listTestCases(
            @PathVariable Long id, @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(codingProblemService.listTestCases(id, caller));
    }

    /** ADMIN only. */
    @PostMapping("/{id}/test-cases")
    public ResponseEntity<TestCaseResponse> createTestCase(
            @PathVariable Long id,
            @Valid @RequestBody TestCaseRequest request,
            @AuthenticationPrincipal User caller) {
        TestCaseResponse created = codingProblemService.createTestCase(id, request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** ADMIN only. */
    @PutMapping("/{problemId}/test-cases/{testCaseId}")
    public ResponseEntity<TestCaseResponse> updateTestCase(
            @PathVariable Long problemId,
            @PathVariable Long testCaseId,
            @Valid @RequestBody TestCaseRequest request,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(
                codingProblemService.updateTestCase(problemId, testCaseId, request, caller));
    }

    /** ADMIN only. */
    @DeleteMapping("/{problemId}/test-cases/{testCaseId}")
    public ResponseEntity<Void> deleteTestCase(
            @PathVariable Long problemId,
            @PathVariable Long testCaseId,
            @AuthenticationPrincipal User caller) {
        codingProblemService.deleteTestCase(problemId, testCaseId, caller);
        return ResponseEntity.noContent().build();
    }
}
