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
import smartcampus.dto.CourseCreateRequest;
import smartcampus.dto.CourseResponse;
import smartcampus.dto.CourseUpdateRequest;
import smartcampus.dto.PageResponse;
import smartcampus.entity.Course;
import smartcampus.service.CourseService;

/**
 * REST endpoints for {@code Course} CRUD operations.
 *
 * <p>All write operations (POST, PUT, DELETE) are restricted to ADMIN role via
 * {@code SecurityConfig}. Read operations (GET) are unrestricted. Server-side search,
 * filtering, sorting and pagination follow the §44 envelope specification.
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    /**
     * Create a new course.
     *
     * @param request the create request
     * @return 201 CREATED with the new course
     */
    @PostMapping
    public ResponseEntity<CourseResponse> create(
            @Valid @RequestBody CourseCreateRequest request) {
        CourseResponse created = courseService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Get a single course by ID.
     *
     * @param id the course ID
     * @return 200 OK with the course
     */
    @GetMapping("/{id}")
    public ResponseEntity<CourseResponse> getById(@PathVariable Long id) {
        CourseResponse response = courseService.getById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * List all courses with server-side search, filtering, sorting and pagination.
     *
     * <p>Query parameters:
     * <ul>
     *   <li>{@code search} - search by code or name (case-insensitive substring match)</li>
     *   <li>{@code departmentId} - filter by department ID</li>
     *   <li>{@code sort} - sort by column, e.g. "code,asc" or "name,desc" (default: id,asc)</li>
     *   <li>{@code page} - zero-indexed page number (default: 0)</li>
     *   <li>{@code size} - page size (default: 20)</li>
     * </ul>
     *
     * @param search optional search term
     * @param departmentId optional department filter
     * @param pageable pagination and sorting info
     * @return 200 OK with the paginated list matching §44 envelope
     */
    @GetMapping
    public ResponseEntity<PageResponse<CourseResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long departmentId,
            Pageable pageable) {
        Specification<Course> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(root.get("name")), pattern)));
            }

            if (departmentId != null) {
                predicates.add(cb.equal(root.get("department").get("id"), departmentId));
            }

            return predicates.isEmpty() ? null : cb.and(predicates.toArray(
                    new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<CourseResponse> page = courseService.list(spec, pageable);
        return ResponseEntity.ok(PageResponse.of(page));
    }

    /**
     * Update an existing course.
     *
     * @param id the course ID
     * @param request the update request
     * @return 200 OK with the updated course
     */
    @PutMapping("/{id}")
    public ResponseEntity<CourseResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CourseUpdateRequest request) {
        CourseResponse updated = courseService.update(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete a course.
     *
     * @param id the course ID
     * @return 204 NO CONTENT
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        courseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
