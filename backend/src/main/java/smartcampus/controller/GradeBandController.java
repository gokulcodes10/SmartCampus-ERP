package smartcampus.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.GradeBandRequest;
import smartcampus.dto.GradeBandResponse;
import smartcampus.service.GradeBandService;

/**
 * {@code /api/grade-bands} — the G7 admin-configurable percentage-&gt;grade-&gt;grade-point
 * scale. Reads are open to any authenticated role (a student must be able to see the
 * scale their own grade came from); writes are ADMIN-only, enforced by {@code
 * SecurityConfig} (integrator-owned) rather than here, matching the same pattern
 * {@code DepartmentController}/{@code CourseController}/{@code SubjectController} use
 * for their own reference-data writes.
 */
@RestController
@RequestMapping("/api/grade-bands")
public class GradeBandController {

    private final GradeBandService gradeBandService;

    public GradeBandController(GradeBandService gradeBandService) {
        this.gradeBandService = gradeBandService;
    }

    /** Ordered {@code minPercentage} DESC — not paginated, the scale is a small fixed list. */
    @GetMapping
    public List<GradeBandResponse> list() {
        return gradeBandService.list();
    }

    @PostMapping
    public ResponseEntity<GradeBandResponse> create(@Valid @RequestBody GradeBandRequest request) {
        GradeBandResponse response = gradeBandService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public GradeBandResponse update(
            @PathVariable Long id, @Valid @RequestBody GradeBandRequest request) {
        return gradeBandService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        gradeBandService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
