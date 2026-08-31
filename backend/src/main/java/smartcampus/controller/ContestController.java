package smartcampus.controller;

import jakarta.validation.Valid;
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
import smartcampus.dto.ContestCreateRequest;
import smartcampus.dto.ContestDetailResponse;
import smartcampus.dto.ContestLeaderboardRowResponse;
import smartcampus.dto.ContestParticipantResponse;
import smartcampus.dto.ContestProblemRequest;
import smartcampus.dto.ContestProblemResponse;
import smartcampus.dto.ContestSummaryResponse;
import smartcampus.dto.ContestUpdateRequest;
import smartcampus.dto.PageResponse;
import smartcampus.entity.ContestPhase;
import smartcampus.entity.ContestStatus;
import smartcampus.entity.User;
import smartcampus.service.CodingContestService;

/**
 * {@code /api/contests} — contest authoring (ADMIN only, per README "For
 * Administrators": coding contest creation and problem authoring), registration,
 * per-contest leaderboard reads, and the admin recompute endpoint.
 *
 * <p>Route-level restriction is deliberately thin, matching this project's house style
 * ({@code ProblemController}/{@code CodingProblemService}): role and visibility are
 * enforced once, centrally, in {@link CodingContestService}, not re-implemented here.
 * Method security ({@code @PreAuthorize}) is not enabled on this build, so the real
 * ADMIN/STUDENT gates below must additionally be applied at the route level in {@code
 * SecurityConfig} — see this class's javadoc for the exact rules needed; that file is
 * owned by the integrator.
 *
 * <p>Route rules needed in {@code SecurityConfig}:
 * <ul>
 *   <li>{@code POST /api/contests}, {@code PUT /api/contests/{id}}, {@code DELETE
 *       /api/contests/{id}}, {@code POST /api/contests/{id}/problems}, {@code DELETE
 *       /api/contests/{contestId}/problems/{problemId}}, {@code POST
 *       /api/contests/{id}/recompute} — {@code hasRole("ADMIN")}.
 *   <li>{@code POST /api/contests/{id}/register}, {@code GET /api/contests/{id}/me} —
 *       {@code hasRole("STUDENT")}.
 *   <li>Everything else under {@code /api/contests} — any authenticated role.
 * </ul>
 */
@RestController
@RequestMapping("/api/contests")
@RequiredArgsConstructor
public class ContestController {

    private final CodingContestService codingContestService;

    @GetMapping
    public ResponseEntity<PageResponse<ContestSummaryResponse>> list(
            @AuthenticationPrincipal User caller,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ContestStatus status,
            @RequestParam(required = false) ContestPhase phase,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(codingContestService.list(caller, search, status, phase, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContestDetailResponse> getById(
            @PathVariable Long id, @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(codingContestService.getById(id, caller));
    }

    /** ADMIN only. */
    @PostMapping
    public ResponseEntity<ContestDetailResponse> create(
            @Valid @RequestBody ContestCreateRequest request, @AuthenticationPrincipal User caller) {
        ContestDetailResponse created = codingContestService.create(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** ADMIN only. */
    @PutMapping("/{id}")
    public ResponseEntity<ContestDetailResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ContestUpdateRequest request,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(codingContestService.update(id, request, caller));
    }

    /** ADMIN only. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        codingContestService.delete(id, caller);
        return ResponseEntity.noContent().build();
    }

    /** ADMIN only. */
    @PostMapping("/{id}/problems")
    public ResponseEntity<ContestProblemResponse> addProblem(
            @PathVariable Long id,
            @Valid @RequestBody ContestProblemRequest request,
            @AuthenticationPrincipal User caller) {
        ContestProblemResponse created = codingContestService.addProblem(id, request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** ADMIN only. */
    @DeleteMapping("/{contestId}/problems/{problemId}")
    public ResponseEntity<Void> removeProblem(
            @PathVariable Long contestId,
            @PathVariable Long problemId,
            @AuthenticationPrincipal User caller) {
        codingContestService.removeProblem(contestId, problemId, caller);
        return ResponseEntity.noContent().build();
    }

    /** STUDENT only. */
    @PostMapping("/{id}/register")
    public ResponseEntity<ContestParticipantResponse> register(
            @PathVariable Long id, @AuthenticationPrincipal User caller) {
        ContestParticipantResponse response = codingContestService.register(id, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** STUDENT only. 404 when the caller is not registered for this contest. */
    @GetMapping("/{id}/me")
    public ResponseEntity<ContestParticipantResponse> me(
            @PathVariable Long id, @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(codingContestService.me(id, caller));
    }

    @GetMapping("/{id}/leaderboard")
    public ResponseEntity<PageResponse<ContestLeaderboardRowResponse>> leaderboard(
            @PathVariable Long id,
            @AuthenticationPrincipal User caller,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(codingContestService.leaderboard(id, caller, pageable));
    }

    /** ADMIN only. Rebuilds every participant row for this contest from coding_submissions. */
    @PostMapping("/{id}/recompute")
    public ResponseEntity<ContestDetailResponse> recompute(
            @PathVariable Long id, @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(codingContestService.recompute(id, caller));
    }
}
