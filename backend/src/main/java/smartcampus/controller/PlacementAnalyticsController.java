package smartcampus.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.PlacementAnalyticsResponse;
import smartcampus.entity.User;
import smartcampus.service.PlacementAnalyticsService;

/**
 * {@code /api/placement} — §36 the admin placement analytics overview.
 *
 * <p>Method security is not enabled on this build; role enforcement lives in {@link
 * PlacementAnalyticsService#overview}, which starts with {@code
 * ScopedWriteAuthorizer#requireAdmin}.
 */
@RestController
@RequestMapping("/api/placement")
public class PlacementAnalyticsController {

    private final PlacementAnalyticsService placementAnalyticsService;

    public PlacementAnalyticsController(PlacementAnalyticsService placementAnalyticsService) {
        this.placementAnalyticsService = placementAnalyticsService;
    }

    /** ADMIN only. */
    @GetMapping("/analytics")
    public PlacementAnalyticsResponse analytics(@AuthenticationPrincipal User caller) {
        return placementAnalyticsService.overview(caller);
    }
}
