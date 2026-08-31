package smartcampus.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.ApplicationBulkStatusRequest;
import smartcampus.dto.ApplicationBulkStatusResponse;
import smartcampus.dto.ApplicationResumeUpdateRequest;
import smartcampus.dto.ApplicationStatusUpdateRequest;
import smartcampus.dto.PageResponse;
import smartcampus.dto.PlacementApplicationCreateRequest;
import smartcampus.dto.PlacementApplicationResponse;
import smartcampus.entity.ApplicationStatus;
import smartcampus.entity.User;
import smartcampus.service.PlacementApplicationService;

/**
 * {@code /api/applications} — §35 apply flow and §36 admin status pipeline.
 *
 * <p>Method security is not enabled on this build; role/ownership enforcement lives in
 * {@link PlacementApplicationService}.
 */
@RestController
@RequestMapping("/api/applications")
public class PlacementApplicationController {

    private final PlacementApplicationService placementApplicationService;

    public PlacementApplicationController(PlacementApplicationService placementApplicationService) {
        this.placementApplicationService = placementApplicationService;
    }

    /**
     * STUDENT. 403 when not eligible, 409 when already applied, 400 when the drive is
     * not OPEN or the deadline has passed.
     */
    @PostMapping
    public ResponseEntity<PlacementApplicationResponse> apply(
            @Valid @RequestBody PlacementApplicationCreateRequest request, @AuthenticationPrincipal User caller) {
        PlacementApplicationResponse response = placementApplicationService.apply(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** STUDENT — the caller's own application history. */
    @GetMapping("/me")
    public PageResponse<PlacementApplicationResponse> myApplications(
            @RequestParam(required = false) ApplicationStatus status,
            @PageableDefault(size = 20, sort = "appliedAt") Pageable pageable,
            @AuthenticationPrincipal User caller) {
        return placementApplicationService.myApplications(caller, status, pageable);
    }

    /** ADMIN — {@code search} matches student name OR register number. */
    @GetMapping
    public PageResponse<PlacementApplicationResponse> list(
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User caller) {
        return placementApplicationService.list(jobId, companyId, status, departmentId, search, caller, pageable);
    }

    /** Owner STUDENT or ADMIN; 404 (never 403) otherwise — an id must not be probeable. */
    @GetMapping("/{id}")
    public PlacementApplicationResponse getById(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        return placementApplicationService.getById(id, caller);
    }

    /** ADMIN only. An admin may never set WITHDRAWN. */
    @PatchMapping("/{id}/status")
    public PlacementApplicationResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ApplicationStatusUpdateRequest request,
            @AuthenticationPrincipal User caller) {
        return placementApplicationService.updateStatus(id, request, caller);
    }

    /** ADMIN only. Illegal transitions within the batch are skipped, not fatal to the whole request. */
    @PostMapping("/bulk-status")
    public ApplicationBulkStatusResponse bulkUpdateStatus(
            @Valid @RequestBody ApplicationBulkStatusRequest request, @AuthenticationPrincipal User caller) {
        return placementApplicationService.bulkUpdateStatus(request, caller);
    }

    /** Owner STUDENT only. */
    @PostMapping("/{id}/withdraw")
    public PlacementApplicationResponse withdraw(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        return placementApplicationService.withdraw(id, caller);
    }

    /**
     * Owner STUDENT only. Allowed only while the application is APPLIED, UNDER_REVIEW,
     * SHORTLISTED or INTERVIEW_SCHEDULED — SELECTED/REJECTED/WITHDRAWN are terminal and
     * their attached artifact must not change.
     */
    @PatchMapping("/{id}/resume")
    public PlacementApplicationResponse updateResume(
            @PathVariable Long id,
            @Valid @RequestBody ApplicationResumeUpdateRequest request,
            @AuthenticationPrincipal User caller) {
        return placementApplicationService.updateResume(id, request, caller);
    }
}
