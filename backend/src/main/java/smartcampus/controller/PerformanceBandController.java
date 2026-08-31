package smartcampus.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.PerformanceBandRequest;
import smartcampus.dto.PerformanceBandResponse;
import smartcampus.service.PerformanceBandService;

/**
 * {@code /api/performance-bands} — the §60 admin-configurable EXCELLENT/GOOD/AVERAGE/AT_RISK
 * threshold scale. Reads are open to any authenticated role (a student must be able to see the
 * scale they were judged against); writes are ADMIN-only, enforced by {@code SecurityConfig}
 * (integrator-owned) rather than here, matching the same pattern {@code GradeBandController}
 * uses for its own admin-configurable scale.
 *
 * <p>The category set is closed — see {@code PerformanceBandService}'s javadoc — so there is no
 * {@code POST} and no {@code DELETE} here, only {@code GET} and {@code PUT}.
 */
@RestController
@RequestMapping("/api/performance-bands")
public class PerformanceBandController {

    private final PerformanceBandService performanceBandService;

    public PerformanceBandController(PerformanceBandService performanceBandService) {
        this.performanceBandService = performanceBandService;
    }

    /** Ordered {@code displayOrder} ASC — not paginated, the scale is a fixed 4-row list. */
    @GetMapping
    public List<PerformanceBandResponse> list() {
        return performanceBandService.list();
    }

    @PutMapping("/{id}")
    public PerformanceBandResponse update(
            @PathVariable Long id, @Valid @RequestBody PerformanceBandRequest request) {
        return performanceBandService.update(id, request);
    }
}
