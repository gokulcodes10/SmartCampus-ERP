package smartcampus.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.InterviewProgressSummaryResponse;
import smartcampus.dto.InterviewQuestionCreateRequest;
import smartcampus.dto.InterviewQuestionProgressRequest;
import smartcampus.dto.InterviewQuestionResponse;
import smartcampus.dto.InterviewQuestionUpdateRequest;
import smartcampus.dto.PageResponse;
import smartcampus.entity.InterviewDifficulty;
import smartcampus.entity.InterviewQuestionCategory;
import smartcampus.entity.InterviewQuestionSource;
import smartcampus.entity.User;
import smartcampus.service.InterviewQuestionService;

/**
 * {@code /api/interview-questions} — the interview question bank (§38): browse/search
 * (any authenticated role, narrowed by visibility), admin authoring, and per-student
 * progress (completion + bookmarks).
 *
 * <p>A SEPARATE {@code InterviewGenerationController} owns {@code POST
 * /api/interview-questions/generate} on this same base path (AI generation, a different
 * agent's slice of this phase) — this class deliberately does not map that route.
 *
 * <p>Route-level restriction is deliberately thin, matching this project's house style
 * ({@code ProblemController}/{@code CodingProblemService}): role and visibility are
 * enforced once, centrally, in {@link InterviewQuestionService}, not re-implemented
 * here. Method security ({@code @PreAuthorize}) is not enabled on this build, so any
 * additional route-level ADMIN/STUDENT gate belongs in {@code SecurityConfig}, owned by
 * the integrator.
 */
@RestController
@RequestMapping("/api/interview-questions")
@RequiredArgsConstructor
public class InterviewQuestionController {

    private final InterviewQuestionService interviewQuestionService;

    @GetMapping
    public ResponseEntity<PageResponse<InterviewQuestionResponse>> list(
            @AuthenticationPrincipal User caller,
            @RequestParam(required = false) InterviewQuestionCategory category,
            @RequestParam(required = false) InterviewDifficulty difficulty,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) InterviewQuestionSource source,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean bookmarked,
            @RequestParam(required = false) Boolean completed,
            @RequestParam(required = false) Boolean mine,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                interviewQuestionService.list(
                        caller, category, difficulty, companyName, source, q, bookmarked, completed, mine,
                        pageable));
    }

    /**
     * Two path segments after the base, so this can never collide with {@code
     * GET /{id}}.
     */
    @GetMapping("/progress/summary")
    public ResponseEntity<InterviewProgressSummaryResponse> progressSummary(
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(interviewQuestionService.progressSummary(caller));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InterviewQuestionResponse> getById(
            @PathVariable Long id, @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(interviewQuestionService.getById(id, caller));
    }

    /** ADMIN only. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewQuestionResponse create(
            @Valid @RequestBody InterviewQuestionCreateRequest request, @AuthenticationPrincipal User caller) {
        return interviewQuestionService.create(request, caller);
    }

    /** ADMIN only. */
    @PutMapping("/{id}")
    public ResponseEntity<InterviewQuestionResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody InterviewQuestionUpdateRequest request,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(interviewQuestionService.update(id, request, caller));
    }

    /** ADMIN: any global question. STUDENT: own private (AI-generated) questions only. FACULTY: 403. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        interviewQuestionService.delete(id, caller);
    }

    /** STUDENT only. */
    @PutMapping("/{id}/progress")
    public ResponseEntity<InterviewQuestionResponse> upsertProgress(
            @PathVariable Long id,
            @Valid @RequestBody InterviewQuestionProgressRequest request,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(interviewQuestionService.upsertProgress(id, request, caller));
    }
}
