package smartcampus.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.AttendanceBulkRequest;
import smartcampus.dto.AttendanceBulkResponse;
import smartcampus.dto.AttendanceClassSummaryResponse;
import smartcampus.dto.AttendanceResponse;
import smartcampus.dto.AttendanceRosterResponse;
import smartcampus.dto.AttendanceSummaryResponse;
import smartcampus.dto.AttendanceUpdateRequest;
import smartcampus.dto.PageResponse;
import smartcampus.entity.User;
import smartcampus.service.AttendanceService;

/**
 * {@code /api/attendance} — bulk roster marking, single-row correction, the roster
 * pre-fill screen, and every attendance-percentage view.
 *
 * <p>Every route-level restriction here is deliberately thin: role and ownership are
 * enforced once, centrally, in {@link AttendanceService} via {@link
 * smartcampus.service.ScopedWriteAuthorizer}, not re-implemented per handler. Method
 * security ({@code @PreAuthorize}) is not enabled on this build, so route mapping
 * alone never decides who may call what — the service layer does.
 */
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    /** Faculty (via {@code ScopedWriteAuthorizer}) or admin bulk-marks a class session. UPSERT, so 200. */
    @PostMapping("/bulk")
    public ResponseEntity<AttendanceBulkResponse> bulkMark(
            @Valid @RequestBody AttendanceBulkRequest request, @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(attendanceService.bulkMark(request, caller));
    }

    /** The enrolled roster for one class session, pre-filled with any attendance already recorded. */
    @GetMapping("/roster")
    public ResponseEntity<AttendanceRosterResponse> roster(
            @RequestParam Long subjectId,
            @RequestParam String academicYear,
            @RequestParam Integer semester,
            @RequestParam String section,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Integer period,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(
                attendanceService.roster(subjectId, academicYear, semester, section, date, period, caller));
    }

    /** Corrects a single, already-recorded attendance row. */
    @PutMapping("/{id}")
    public ResponseEntity<AttendanceResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody AttendanceUpdateRequest request,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(attendanceService.update(id, request, caller));
    }

    /** The authenticated student's own attendance rows, paged. */
    @GetMapping("/me")
    public ResponseEntity<PageResponse<AttendanceResponse>> myAttendance(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(
                attendanceService.myAttendance(caller, academicYear, semester, subjectId, page, size));
    }

    /** The authenticated student's own attendance summary and percentage. */
    @GetMapping("/me/summary")
    public ResponseEntity<AttendanceSummaryResponse> mySummary(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(attendanceService.mySummary(caller, academicYear, semester));
    }

    /** Admin-only: any student's attendance summary and percentage. */
    @GetMapping("/summary/{studentId}")
    public ResponseEntity<AttendanceSummaryResponse> adminSummary(
            @PathVariable Long studentId,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(attendanceService.adminSummary(studentId, academicYear, semester, caller));
    }

    /** Faculty (via {@code ScopedWriteAuthorizer}) or admin: per-student attendance breakdown for one taught class. */
    @GetMapping("/class-summary")
    public ResponseEntity<AttendanceClassSummaryResponse> classSummary(
            @RequestParam Long subjectId,
            @RequestParam String academicYear,
            @RequestParam Integer semester,
            @RequestParam String section,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(
                attendanceService.classSummary(subjectId, academicYear, semester, section, caller));
    }
}
