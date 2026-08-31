package smartcampus.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.GlobalLeaderboardRowResponse;
import smartcampus.dto.PageResponse;
import smartcampus.service.CodingLeaderboardService;

/**
 * {@code /api/leaderboard} — the global coding leaderboard across every student's
 * practice and contest submissions.
 *
 * <p>Any authenticated role may read this; no route-level restriction is needed beyond
 * requiring a valid JWT, which {@code SecurityConfig}'s default authenticated-by-default
 * rule already applies.
 */
@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final CodingLeaderboardService codingLeaderboardService;

    @GetMapping("/global")
    public ResponseEntity<PageResponse<GlobalLeaderboardRowResponse>> global(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(codingLeaderboardService.globalLeaderboard(pageable));
    }
}
