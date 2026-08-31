package smartcampus.controller;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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
import smartcampus.dto.InterviewRescheduleRequest;
import smartcampus.dto.InterviewResponse;
import smartcampus.dto.InterviewScheduleRequest;
import smartcampus.dto.InterviewStatusUpdateRequest;
import smartcampus.dto.InterviewUpdateRequest;
import smartcampus.dto.PageResponse;
import smartcampus.entity.InterviewStatus;
import smartcampus.entity.User;
import smartcampus.service.InterviewSchedulingService;

/**
 * {@code /api/interviews} — §39 interview scheduling. Method security is not enabled on
 * this build; role/ownership enforcement lives entirely in {@link
 * InterviewSchedulingService}.
 */
@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private final InterviewSchedulingService interviewSchedulingService;

    public InterviewController(InterviewSchedulingService interviewSchedulingService) {
        this.interviewSchedulingService = interviewSchedulingService;
    }

    /** STUDENT: forced to own rows. ADMIN: all rows, studentId filter honoured. FACULTY: 403. */
    @GetMapping
    public PageResponse<InterviewResponse> list(
            @RequestParam(required = false) InterviewStatus status,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String q,
            @AuthenticationPrincipal User caller,
            @PageableDefault(size = 20, sort = "scheduledStart", direction = Sort.Direction.ASC) Pageable pageable) {
        return interviewSchedulingService.list(status, studentId, from, to, q, caller, pageable);
    }

    /** STUDENT only; not paged. limit defaults to 5 and is clamped to [1, 20]. */
    @GetMapping("/upcoming")
    public List<InterviewResponse> upcoming(
            @AuthenticationPrincipal User caller, @RequestParam(defaultValue = "5") int limit) {
        return interviewSchedulingService.upcoming(caller, limit);
    }

    /** Owner student or ADMIN; 404 otherwise (never 403 — ids must not be probeable). */
    @GetMapping("/{id}")
    public InterviewResponse getById(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        return interviewSchedulingService.getById(id, caller);
    }

    /** ADMIN or STUDENT(self). */
    @PostMapping
    public ResponseEntity<InterviewResponse> schedule(
            @Valid @RequestBody InterviewScheduleRequest request, @AuthenticationPrincipal User caller) {
        InterviewResponse response = interviewSchedulingService.schedule(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** ADMIN or owner STUDENT. */
    @PutMapping("/{id}")
    public InterviewResponse update(
            @PathVariable Long id,
            @Valid @RequestBody InterviewUpdateRequest request,
            @AuthenticationPrincipal User caller) {
        return interviewSchedulingService.update(id, request, caller);
    }

    /** ADMIN or owner STUDENT. */
    @PutMapping("/{id}/reschedule")
    public InterviewResponse reschedule(
            @PathVariable Long id,
            @Valid @RequestBody InterviewRescheduleRequest request,
            @AuthenticationPrincipal User caller) {
        return interviewSchedulingService.reschedule(id, request, caller);
    }

    /** ADMIN or owner STUDENT. */
    @PutMapping("/{id}/status")
    public InterviewResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody InterviewStatusUpdateRequest request,
            @AuthenticationPrincipal User caller) {
        return interviewSchedulingService.updateStatus(id, request, caller);
    }

    /** ADMIN only. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        interviewSchedulingService.delete(id, caller);
        return ResponseEntity.noContent().build();
    }
}
