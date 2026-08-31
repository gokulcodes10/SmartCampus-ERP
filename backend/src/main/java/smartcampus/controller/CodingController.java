package smartcampus.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.CodingStatsResponse;
import smartcampus.dto.LanguageResponse;
import smartcampus.dto.PageResponse;
import smartcampus.dto.RunRequest;
import smartcampus.dto.RunResponse;
import smartcampus.dto.SampleRunResponse;
import smartcampus.dto.SubmissionCreateRequest;
import smartcampus.dto.SubmissionDetailResponse;
import smartcampus.dto.SubmissionSummaryResponse;
import smartcampus.entity.SubmissionStatus;
import smartcampus.entity.User;
import smartcampus.service.CodingSubmissionService;

/**
 * {@code /api/coding} — the playground ("Run") endpoints, the graded submission flow,
 * submission history and the caller's own stats.
 *
 * <p>Route-level restriction is thin by this project's house style; the real
 * authorization (STUDENT-only submit, FACULTY-forbidden submission reads, owner-or-
 * ADMIN detail) lives in {@link CodingSubmissionService}. See this class's javadoc in
 * the final report for the {@code SecurityConfig} rules this route family needs.
 */
@RestController
@RequestMapping("/api/coding")
@RequiredArgsConstructor
public class CodingController {

    private final CodingSubmissionService codingSubmissionService;

    @GetMapping("/languages")
    public ResponseEntity<List<LanguageResponse>> languages() {
        return ResponseEntity.ok(codingSubmissionService.listLanguages());
    }

    /** Any authenticated caller. Free-form execution; nothing is persisted. */
    @PostMapping("/run")
    public ResponseEntity<RunResponse> run(
            @Valid @RequestBody RunRequest request, @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(codingSubmissionService.run(request, caller));
    }

    /** Any authenticated caller. Runs a problem's sample cases only; nothing is persisted. */
    @PostMapping("/problems/{problemId}/run")
    public ResponseEntity<SampleRunResponse> runSample(
            @PathVariable Long problemId,
            @Valid @RequestBody RunRequest request,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(codingSubmissionService.runSample(problemId, request, caller));
    }

    /** STUDENT only. Always 201 once the row exists — see {@link CodingSubmissionService#submit}. */
    @PostMapping("/submissions")
    public ResponseEntity<SubmissionDetailResponse> submit(
            @Valid @RequestBody SubmissionCreateRequest request, @AuthenticationPrincipal User caller) {
        SubmissionDetailResponse created = codingSubmissionService.submit(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** STUDENT sees only their own rows; ADMIN sees any; FACULTY gets 403. */
    @GetMapping("/submissions")
    public ResponseEntity<PageResponse<SubmissionSummaryResponse>> listSubmissions(
            @AuthenticationPrincipal User caller,
            @RequestParam(required = false) Long problemId,
            @RequestParam(required = false) Long contestId,
            @RequestParam(required = false) SubmissionStatus status,
            @RequestParam(required = false) Long studentId,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(
                codingSubmissionService.listSubmissions(
                        caller, problemId, contestId, status, studentId, pageable));
    }

    /** Owner or ADMIN; anyone else — including FACULTY — gets 404, never 403 (R8). */
    @GetMapping("/submissions/{id}")
    public ResponseEntity<SubmissionDetailResponse> getSubmission(
            @PathVariable Long id, @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(codingSubmissionService.getSubmission(id, caller));
    }

    /** STUDENT only. */
    @GetMapping("/stats/me")
    public ResponseEntity<CodingStatsResponse> myStats(@AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(codingSubmissionService.getStats(caller));
    }
}
