package smartcampus.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.PageResponse;
import smartcampus.dto.StudentActivateRequest;
import smartcampus.dto.StudentAdminUpdateRequest;
import smartcampus.dto.StudentResponse;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.User;
import smartcampus.service.StudentService;

/**
 * {@code /api/students} — admin CRUD, the G1 pending-approval activation flow, and
 * self-service reads for the student the JWT belongs to.
 *
 * <p>Every route-level restriction here is deliberately thin: role and ownership are
 * enforced once, centrally, in {@link StudentService} (see its class javadoc), not
 * re-implemented per handler. Method security ({@code @PreAuthorize}) is not enabled
 * on this build (see PROJECT_PLAN.md Phase 2 addendum), so a {@code STUDENT} or {@code
 * FACULTY} JWT can physically reach every method below — the service layer is what
 * turns an unauthorized call into a clean 403/404, not the route mapping.
 */
@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    /**
     * Admin/faculty listing with server-side search/filter/pagination (§44). A {@code
     * FACULTY} caller is automatically narrowed to the students they teach — the same
     * endpoint, not a separate nested route, is how "faculty may read the students
     * they actually teach" is satisfied.
     */
    @GetMapping
    public ResponseEntity<PageResponse<StudentResponse>> list(
            @AuthenticationPrincipal User caller,
            @RequestParam(required = false) StudentStatus status,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Integer currentSemester,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                studentService.list(
                        caller, status, departmentId, courseId, currentSemester, section, q, page, size));
    }

    /** Admin convenience view of the G1 pending-activation queue. */
    @GetMapping("/pending")
    public ResponseEntity<PageResponse<StudentResponse>> pending(
            @AuthenticationPrincipal User caller,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                studentService.list(
                        caller, StudentStatus.PENDING, null, null, null, null, null, page, size));
    }

    /** The authenticated student's own profile. */
    @GetMapping("/me")
    public ResponseEntity<StudentResponse> me(@AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(studentService.getOwnProfile(caller));
    }

    /**
     * Direct by-id lookup — the route an ID-enumeration attack targets. See {@link
     * StudentService#getById} for the ownership rule.
     */
    @GetMapping("/{id}")
    public ResponseEntity<StudentResponse> getById(
            @PathVariable Long id, @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(studentService.getById(id, caller));
    }

    /** Admin edit of an already-assigned student's department/course/semester/section. */
    @PutMapping("/{id}")
    public ResponseEntity<StudentResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody StudentAdminUpdateRequest request,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(studentService.update(id, request, caller));
    }

    /** G1: admin activation of a PENDING student profile. */
    @PostMapping("/{id}/activate")
    public ResponseEntity<StudentResponse> activate(
            @PathVariable Long id,
            @Valid @RequestBody StudentActivateRequest request,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(studentService.activate(id, request, caller));
    }

    /** Soft-delete: admin deactivation (ACTIVE/PENDING → INACTIVE). */
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<StudentResponse> deactivate(
            @PathVariable Long id, @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(studentService.deactivate(id, caller));
    }

    /** Admin reactivation of a previously-activated, currently INACTIVE student. */
    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<StudentResponse> reactivate(
            @PathVariable Long id, @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(studentService.reactivate(id, caller));
    }
}
