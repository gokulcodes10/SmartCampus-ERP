package smartcampus.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.PageResponse;
import smartcampus.dto.SubjectCreateRequest;
import smartcampus.dto.SubjectResponse;
import smartcampus.dto.SubjectUpdateRequest;
import smartcampus.entity.Subject;
import smartcampus.service.SubjectService;

/**
 * REST endpoints for {@code Subject} CRUD operations.
 *
 * <p>All write operations (POST, PUT, DELETE) are restricted to ADMIN role via
 * {@code SecurityConfig}. Read operations (GET) are unrestricted. Server-side search,
 * filtering, sorting and pagination follow the §44 envelope specification.
 */
@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;

    /**
     * Create a new subject.
     *
     * @param request the create request
     * @return 201 CREATED with the new subject
     */
    @PostMapping
    public ResponseEntity<SubjectResponse> create(
            @Valid @RequestBody SubjectCreateRequest request) {
        SubjectResponse created = subjectService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Get a single subject by ID.
     *
     * @param id the subject ID
     * @return 200 OK with the subject
     */
    @GetMapping("/{id}")
    public ResponseEntity<SubjectResponse> getById(@PathVariable Long id) {
        SubjectResponse response = subjectService.getById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * List all subjects with server-side search, filtering, sorting and pagination.
     *
     * <p>Query parameters:
     * <ul>
     *   <li>{@code search} - search by code or name (case-insensitive substring match)</li>
     *   <li>{@code courseId} - filter by course ID</li>
     *   <li>{@code semester} - filter by semester</li>
     *   <li>{@code sort} - sort by column, e.g. "code,asc" or "name,desc" (default: id,asc)</li>
     *   <li>{@code page} - zero-indexed page number (default: 0)</li>
     *   <li>{@code size} - page size (default: 20)</li>
     * </ul>
     *
     * @param search optional search term
     * @param courseId optional course filter
     * @param semester optional semester filter
     * @param pageable pagination and sorting info
     * @return 200 OK with the paginated list matching §44 envelope
     */
    @GetMapping
    public ResponseEntity<PageResponse<SubjectResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Integer semester,
            Pageable pageable) {
        Specification<Subject> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(root.get("name")), pattern)));
            }

            if (courseId != null) {
                predicates.add(cb.equal(root.get("course").get("id"), courseId));
            }

            if (semester != null) {
                predicates.add(cb.equal(root.get("semester"), semester));
            }

            return predicates.isEmpty() ? null : cb.and(predicates.toArray(
                    new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<SubjectResponse> page = subjectService.list(spec, pageable);
        return ResponseEntity.ok(PageResponse.of(page));
    }

    /**
     * Update an existing subject.
     *
     * @param id the subject ID
     * @param request the update request
     * @return 200 OK with the updated subject
     */
    @PutMapping("/{id}")
    public ResponseEntity<SubjectResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody SubjectUpdateRequest request) {
        SubjectResponse updated = subjectService.update(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete a subject.
     *
     * @param id the subject ID
     * @return 204 NO CONTENT
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        subjectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
