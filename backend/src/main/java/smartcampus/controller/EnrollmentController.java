package smartcampus.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.EnrollmentRequest;
import smartcampus.dto.EnrollmentResponse;
import smartcampus.dto.EnrollmentStatusUpdateRequest;
import smartcampus.dto.PageResponse;
import smartcampus.entity.EnrollmentStatus;
import smartcampus.service.EnrollmentService;

/**
 * Admin management of student enrollments — a student's registration in a subject for a
 * given academic year, semester and section (the roster Phase 4 attendance/marks entry
 * reads from).
 *
 * <p>Every route under {@code /api/enrollments/**} is ADMIN-only. That restriction is
 * declared in {@code smartcampus.config.SecurityConfig} (owned by the integrator), the
 * same pattern {@code UserAdminController} uses for {@code /api/users/**} — a
 * non-admin is rejected by the filter chain before any controller code here runs.
 */
@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    /** Enrolls a student in a subject. Rejects a duplicate (student, subject, year, semester) with 409. */
    @PostMapping
    public ResponseEntity<EnrollmentResponse> enroll(@Valid @RequestBody EnrollmentRequest request) {
        EnrollmentResponse response = enrollmentService.enroll(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Server-side search/filter/sort/pagination per §44 — every filter is optional and
     * combines with AND. Sorting and page size are driven by the standard Spring Data
     * {@code page}/{@code size}/{@code sort} query parameters.
     */
    @GetMapping
    public PageResponse<EnrollmentResponse> list(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) EnrollmentStatus status,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return enrollmentService.list(
                studentId, subjectId, academicYear, semester, section, status, pageable);
    }

    @GetMapping("/{id}")
    public EnrollmentResponse getById(@PathVariable Long id) {
        return enrollmentService.getById(id);
    }

    /** Transitions an enrollment between ACTIVE, COMPLETED and DROPPED. */
    @PatchMapping("/{id}/status")
    public EnrollmentResponse updateStatus(
            @PathVariable Long id, @Valid @RequestBody EnrollmentStatusUpdateRequest request) {
        return enrollmentService.updateStatus(id, request.status());
    }

    /** Removes an enrollment outright (data-entry correction) — prefer the DROPPED status for a withdrawal. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        enrollmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
