package smartcampus.controller;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.TeachingClassResponse;
import smartcampus.entity.User;
import smartcampus.service.TeachingService;

/**
 * {@code /api/teaching} — a faculty member's own teaching schedule.
 *
 * <p>Role/ownership enforcement lives in {@link TeachingService}, not in the route
 * mapping, matching {@code FacultyController}/{@code StudentController}. {@code
 * /api/faculty-subject-assignments} stays ADMIN-only end to end; this is the only way
 * a faculty user discovers which (subject, academic year, semester, section) tuples
 * they are authorized to act on.
 */
@RestController
@RequestMapping("/api/teaching")
public class TeachingController {

    private final TeachingService teachingService;

    public TeachingController(TeachingService teachingService) {
        this.teachingService = teachingService;
    }

    /** The calling faculty's own assignment tuples, enriched for display. FACULTY only. */
    @GetMapping("/my-classes")
    public List<TeachingClassResponse> myClasses(@AuthenticationPrincipal User caller) {
        return teachingService.myClasses(caller);
    }
}
