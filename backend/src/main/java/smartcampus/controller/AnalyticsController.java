package smartcampus.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.AnalyticsAdminResponse;
import smartcampus.dto.AnalyticsClassResponse;
import smartcampus.dto.AnalyticsFilterOptionsResponse;
import smartcampus.dto.AnalyticsStudentResponse;
import smartcampus.entity.User;
import smartcampus.service.AnalyticsService;

/**
 * {@code /api/analytics} — the Phase 5 dashboards. Every figure returned here traces to
 * a real database aggregation composed by {@link AnalyticsService}; nothing is
 * hard-coded.
 *
 * <p>Method security is not enabled on this build; role and ownership enforcement live
 * entirely in {@link AnalyticsService} (which in turn delegates the FACULTY/ADMIN scope
 * decision to {@code AnalyticsScopeResolver}, and identity/ownership to {@code
 * ScopedWriteAuthorizer}) — exactly the same pattern {@code MarksController} and {@code
 * AttendanceController} already use. No authorization logic belongs in this class; every
 * handler takes the authenticated {@link User} and delegates immediately.
 *
 * <p>Query-parameter names below are part of the contract (PROJECT_PLAN.md's Phase 3
 * trap: a frontend hook sending a differently-named parameter than the controller reads
 * silently returns unfiltered results) — the frontend MUST send exactly these names.
 *
 * <p><b>Integrator note:</b> {@code /api/analytics/**} must be added to {@code
 * SecurityConfig} as {@code .authenticated()} — every route here is open to any
 * authenticated role, with the actual role/ownership/scope restriction enforced inside
 * {@link AnalyticsService}.
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /** STUDENT — own record only. A {@code studentId} query parameter is never honoured here. */
    @GetMapping("/me")
    public AnalyticsStudentResponse myAnalytics(
            @AuthenticationPrincipal User caller,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) Integer months) {
        return analyticsService.myAnalytics(caller, academicYear, semester, months);
    }

    /** ADMIN only. */
    @GetMapping("/students/{studentId}")
    public AnalyticsStudentResponse studentAnalytics(
            @PathVariable Long studentId,
            @AuthenticationPrincipal User caller,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) Integer months) {
        return analyticsService.studentAnalytics(studentId, caller, academicYear, semester, months);
    }

    /** FACULTY (own assignments only, via {@code AcademicAccessGuard}) or ADMIN. */
    @GetMapping("/class")
    public AnalyticsClassResponse classAnalytics(
            @AuthenticationPrincipal User caller,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) Integer months) {
        return analyticsService.classAnalytics(caller, courseId, subjectId, academicYear, semester, section, months);
    }

    /** ADMIN only. */
    @GetMapping("/overview")
    public AnalyticsAdminResponse overview(
            @AuthenticationPrincipal User caller,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) Integer months) {
        return analyticsService.overview(caller, departmentId, courseId, academicYear, semester, section, months);
    }

    /** FACULTY or ADMIN. */
    @GetMapping("/filters")
    public AnalyticsFilterOptionsResponse filters(@AuthenticationPrincipal User caller) {
        return analyticsService.filterOptions(caller);
    }
}
