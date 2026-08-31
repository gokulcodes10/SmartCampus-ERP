package smartcampus.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import smartcampus.dto.AnnouncementCreateRequest;
import smartcampus.dto.AnnouncementResponse;
import smartcampus.dto.AnnouncementUpdateRequest;
import smartcampus.dto.PageResponse;
import smartcampus.entity.AnnouncementAudience;
import smartcampus.entity.User;
import smartcampus.service.AnnouncementService;

/**
 * {@code /api/announcements} — §42 admin announcements. Method security is not enabled
 * on this build; ADMIN-only enforcement lives in {@link AnnouncementService} via {@code
 * ScopedWriteAuthorizer.requireAdmin}. Route rules for the write verbs and {@code
 * /manage} are the integrator's responsibility in {@code SecurityConfig}.
 *
 * <p>{@code /manage} MUST be declared before {@code /{id}} or "manage" is parsed as an
 * id — the same shape as the Phase 7 hidden-test-cases rule and the Phase 8
 * eligible-students rule.
 */
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    /** ACTIVE board for the caller's role/department. */
    @GetMapping
    public PageResponse<AnnouncementResponse> board(
            @AuthenticationPrincipal User caller,
            @PageableDefault(size = 20, sort = "publishedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return announcementService.board(caller, pageable);
    }

    /** ADMIN only. MUST be declared before {@code GET /{id}}. */
    @GetMapping("/manage")
    public PageResponse<AnnouncementResponse> manage(
            @RequestParam(required = false) AnnouncementAudience audience,
            @RequestParam(required = false, defaultValue = "false") Boolean includeExpired,
            @RequestParam(required = false) String q,
            @AuthenticationPrincipal User caller,
            @PageableDefault(size = 20, sort = "publishedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return announcementService.manage(audience, includeExpired, q, caller, pageable);
    }

    /** 404 if not visible to the caller. */
    @GetMapping("/{id}")
    public AnnouncementResponse getById(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        return announcementService.getById(id, caller);
    }

    /** ADMIN only. */
    @PostMapping
    public ResponseEntity<AnnouncementResponse> create(
            @Valid @RequestBody AnnouncementCreateRequest request, @AuthenticationPrincipal User caller) {
        AnnouncementResponse response = announcementService.create(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** ADMIN only. Carries no audience/departmentId — re-targeting is out of scope (delete + recreate instead). */
    @PutMapping("/{id}")
    public AnnouncementResponse update(
            @PathVariable Long id,
            @Valid @RequestBody AnnouncementUpdateRequest request,
            @AuthenticationPrincipal User caller) {
        return announcementService.update(id, request, caller);
    }

    /** ADMIN only. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        announcementService.delete(id, caller);
        return ResponseEntity.noContent().build();
    }
}
