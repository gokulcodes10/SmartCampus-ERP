package smartcampus.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.FacultySubjectAssignmentRequest;
import smartcampus.dto.FacultySubjectAssignmentResponse;
import smartcampus.dto.PageResponse;
import smartcampus.service.FacultySubjectAssignmentService;

/**
 * Admin management of which faculty teaches which subject/section, in which academic
 * year and semester (PROJECT_PLAN.md clarification G2).
 *
 * <p>Every route under {@code /api/faculty-subject-assignments/**} is ADMIN-only,
 * declared in {@code smartcampus.config.SecurityConfig} (owned by the integrator). This
 * is deliberately the <em>only</em> way assignment rows are created or removed —
 * {@code smartcampus.service.AcademicAccessGuard} is the read side that every faculty
 * authorization check in this and later phases routes through, and it only ever sees
 * what was granted here.
 */
@RestController
@RequestMapping("/api/faculty-subject-assignments")
public class FacultySubjectAssignmentController {

    private final FacultySubjectAssignmentService assignmentService;

    public FacultySubjectAssignmentController(FacultySubjectAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /** Assigns a faculty member to teach a subject/section for an academic year/semester. */
    @PostMapping
    public ResponseEntity<FacultySubjectAssignmentResponse> assign(
            @Valid @RequestBody FacultySubjectAssignmentRequest request) {
        FacultySubjectAssignmentResponse response = assignmentService.assign(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Server-side search/filter/sort/pagination per §44 — every filter is optional and
     * combines with AND.
     */
    @GetMapping
    public PageResponse<FacultySubjectAssignmentResponse> list(
            @RequestParam(required = false) Long facultyId,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) String section,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return assignmentService.list(facultyId, subjectId, academicYear, semester, section, pageable);
    }

    @GetMapping("/{id}")
    public FacultySubjectAssignmentResponse getById(@PathVariable Long id) {
        return assignmentService.getById(id);
    }

    /** Revokes an assignment — the faculty immediately loses {@code AcademicAccessGuard} access to it. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> unassign(@PathVariable Long id) {
        assignmentService.unassign(id);
        return ResponseEntity.noContent().build();
    }
}
