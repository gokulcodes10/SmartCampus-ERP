package smartcampus.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.JobCreateRequest;
import smartcampus.dto.JobResponse;
import smartcampus.dto.JobStatusUpdateRequest;
import smartcampus.dto.JobUpdateRequest;
import smartcampus.dto.PageResponse;
import smartcampus.entity.JobStatus;
import smartcampus.entity.JobType;
import smartcampus.entity.User;
import smartcampus.service.JobService;

/**
 * {@code /api/jobs} — §33-§35 placement drives (job postings).
 *
 * <p>A separate {@code PlacementEligibilityController} also maps {@code /api/jobs} for
 * {@code GET /{jobId}/eligibility} and {@code GET /{jobId}/eligible-students} — legal in
 * Spring MVC since no (path, method) pair collides with this class. Do not add either of
 * those mappings here.
 *
 * <p>Method security is not enabled on this build; role enforcement lives in {@link
 * JobService}, which routes every write through {@code ScopedWriteAuthorizer}.
 */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /** ADMIN only. */
    @PostMapping
    public ResponseEntity<JobResponse> create(
            @Valid @RequestBody JobCreateRequest request, @AuthenticationPrincipal User caller) {
        JobResponse response = jobService.create(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Any authenticated role — server-side search/filter/sort/pagination per §44. A
     * non-admin caller's {@code status} filter is forced to {@code {OPEN, CLOSED}}
     * regardless of what was requested (see {@link JobService#list}), so a DRAFT/CANCELLED
     * request yields an empty page rather than a 403.
     */
    @GetMapping
    public PageResponse<JobResponse> list(
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) JobType jobType,
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean acceptingOnly,
            @AuthenticationPrincipal User caller,
            @PageableDefault(size = 20, sort = "applicationDeadline") Pageable pageable) {
        return jobService.list(companyId, jobType, status, departmentId, search, acceptingOnly, caller, pageable);
    }

    /** Any authenticated role; 404 (never 403) for a non-admin on a DRAFT/CANCELLED drive. */
    @GetMapping("/{id}")
    public JobResponse getById(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        return jobService.getById(id, caller);
    }

    /** ADMIN only. */
    @PutMapping("/{id}")
    public JobResponse update(
            @PathVariable Long id, @Valid @RequestBody JobUpdateRequest request, @AuthenticationPrincipal User caller) {
        return jobService.update(id, request, caller);
    }

    /** ADMIN only. */
    @PatchMapping("/{id}/status")
    public JobResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody JobStatusUpdateRequest request,
            @AuthenticationPrincipal User caller) {
        return jobService.updateStatus(id, request, caller);
    }

    /** ADMIN only; 409 when any application references this drive. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        jobService.delete(id, caller);
        return ResponseEntity.noContent().build();
    }
}
