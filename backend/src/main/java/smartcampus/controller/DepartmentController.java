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
import smartcampus.dto.DepartmentCreateRequest;
import smartcampus.dto.DepartmentResponse;
import smartcampus.dto.DepartmentUpdateRequest;
import smartcampus.dto.PageResponse;
import smartcampus.entity.Department;
import smartcampus.service.DepartmentService;

/**
 * REST endpoints for {@code Department} CRUD operations.
 *
 * <p>All write operations (POST, PUT, DELETE) are restricted to ADMIN role via
 * {@code SecurityConfig}. Read operations (GET) are unrestricted. Server-side search,
 * filtering, sorting and pagination follow the §44 envelope specification.
 */
@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    /**
     * Create a new department.
     *
     * @param request the create request
     * @return 201 CREATED with the new department
     */
    @PostMapping
    public ResponseEntity<DepartmentResponse> create(
            @Valid @RequestBody DepartmentCreateRequest request) {
        DepartmentResponse created = departmentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Get a single department by ID.
     *
     * @param id the department ID
     * @return 200 OK with the department
     */
    @GetMapping("/{id}")
    public ResponseEntity<DepartmentResponse> getById(@PathVariable Long id) {
        DepartmentResponse response = departmentService.getById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * List all departments with server-side search, filtering, sorting and pagination.
     *
     * <p>Query parameters:
     * <ul>
     *   <li>{@code search} - search by code or name (case-insensitive substring match)</li>
     *   <li>{@code sort} - sort by column, e.g. "code,asc" or "name,desc" (default: id,asc)</li>
     *   <li>{@code page} - zero-indexed page number (default: 0)</li>
     *   <li>{@code size} - page size (default: 20)</li>
     * </ul>
     *
     * @param search optional search term
     * @param pageable pagination and sorting info
     * @return 200 OK with the paginated list matching §44 envelope
     */
    @GetMapping
    public ResponseEntity<PageResponse<DepartmentResponse>> list(
            @RequestParam(required = false) String search,
            Pageable pageable) {
        Specification<Department> spec = (root, query, cb) -> {
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                return cb.or(
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(root.get("name")), pattern));
            }
            return null;
        };

        Page<DepartmentResponse> page = departmentService.list(spec, pageable);
        return ResponseEntity.ok(PageResponse.of(page));
    }

    /**
     * Update an existing department.
     *
     * @param id the department ID
     * @param request the update request
     * @return 200 OK with the updated department
     */
    @PutMapping("/{id}")
    public ResponseEntity<DepartmentResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody DepartmentUpdateRequest request) {
        DepartmentResponse updated = departmentService.update(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete a department.
     *
     * @param id the department ID
     * @return 204 NO CONTENT
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
