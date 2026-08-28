package smartcampus.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.FacultyCreateRequest;
import smartcampus.dto.FacultyResponse;
import smartcampus.dto.FacultyUpdateRequest;
import smartcampus.dto.PageResponse;
import smartcampus.entity.FacultyStatus;
import smartcampus.entity.User;
import smartcampus.service.FacultyService;

/**
 * {@code /api/faculty} — admin CRUD over faculty profiles, plus self-service read for
 * the faculty member the JWT belongs to.
 *
 * <p>As with {@link StudentController}, role/ownership enforcement lives entirely in
 * {@link FacultyService}, not in route mappings — see that class's javadoc. "The
 * students a faculty member teaches" is deliberately not exposed here; it is the same
 * {@code GET /api/students} endpoint any admin uses, automatically scoped by caller
 * role in {@code StudentService}.
 */
@RestController
@RequestMapping("/api/faculty")
public class FacultyController {

    private final FacultyService facultyService;

    public FacultyController(FacultyService facultyService) {
        this.facultyService = facultyService;
    }

    /** Admin-only server-side search/filter/pagination (§44) over faculty profiles. */
    @GetMapping
    public ResponseEntity<PageResponse<FacultyResponse>> list(
            @AuthenticationPrincipal User caller,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) FacultyStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(facultyService.list(caller, departmentId, status, q, page, size));
    }

    /** The authenticated faculty member's own profile. */
    @GetMapping("/me")
    public ResponseEntity<FacultyResponse> me(@AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(facultyService.getOwnProfile(caller));
    }

    /** Direct by-id lookup. Admin sees any profile; faculty only their own. */
    @GetMapping("/{id}")
    public ResponseEntity<FacultyResponse> getById(
            @PathVariable Long id, @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(facultyService.getById(id, caller));
    }

    /**
     * Admin creation of a faculty profile for an already-provisioned {@code FACULTY}
     * user (clarification G1 — the login account itself comes from {@code POST
     * /api/users}, owned by Phase 2).
     */
    @PostMapping
    public ResponseEntity<FacultyResponse> create(
            @Valid @RequestBody FacultyCreateRequest request, @AuthenticationPrincipal User caller) {
        FacultyResponse created = facultyService.create(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** Admin edit of employee code, department, designation, or status. */
    @PutMapping("/{id}")
    public ResponseEntity<FacultyResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody FacultyUpdateRequest request,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(facultyService.update(id, request, caller));
    }
}
