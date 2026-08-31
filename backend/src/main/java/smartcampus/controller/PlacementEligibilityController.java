package smartcampus.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.EligibleStudentRow;
import smartcampus.dto.JobEligibilityResponse;
import smartcampus.dto.PageResponse;
import smartcampus.entity.User;
import smartcampus.service.PlacementEligibilityService;

/**
 * {@code /api/jobs/{jobId}/eligibility} and {@code /api/jobs/{jobId}/eligible-students}
 * — the §34 eligibility engine's read surface.
 *
 * <p>This is a SEPARATE {@code @RestController} from {@link JobController} even though
 * both map the {@code /api/jobs} base path. That is legal in Spring MVC because no
 * (path, method) pair collides — {@code JobController} owns the CRUD routes under {@code
 * /api/jobs}, this class owns only the two sub-paths below. Keeping them apart matches
 * the task split that built this phase (JobController belongs to the job-authoring
 * slice; eligibility belongs to the eligibility-engine slice) and keeps each file
 * focused on one concern. Do not merge them and do not add any other mapping here.
 *
 * <p>Method security is not enabled on this build; role/ownership enforcement lives in
 * {@link PlacementEligibilityService}.
 */
@RestController
@RequestMapping("/api/jobs")
public class PlacementEligibilityController {

    private final PlacementEligibilityService placementEligibilityService;

    public PlacementEligibilityController(PlacementEligibilityService placementEligibilityService) {
        this.placementEligibilityService = placementEligibilityService;
    }

    /**
     * A STUDENT checks their own eligibility ({@code studentId} is ignored — never
     * honoured — for this caller, since honouring it would let a student read another
     * student's eligibility). An ADMIN checks a named student's eligibility via {@code
     * studentId} (required for that caller).
     */
    @GetMapping("/{jobId}/eligibility")
    public JobEligibilityResponse eligibility(
            @PathVariable Long jobId,
            @RequestParam(required = false) Long studentId,
            @AuthenticationPrincipal User caller) {
        return placementEligibilityService.evaluate(jobId, studentId, caller);
    }

    /** ADMIN only — the paged eligible-students preview for a drive. */
    @GetMapping("/{jobId}/eligible-students")
    public PageResponse<EligibleStudentRow> eligibleStudents(
            @PathVariable Long jobId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User caller) {
        return placementEligibilityService.eligibleStudents(jobId, caller, pageable);
    }
}
