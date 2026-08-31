package smartcampus.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.AcademicResultResponse;
import smartcampus.dto.MarksBulkRequest;
import smartcampus.dto.MarksBulkResponse;
import smartcampus.dto.MarksEntrySheetResponse;
import smartcampus.dto.MarksResponse;
import smartcampus.dto.MarksUpdateRequest;
import smartcampus.dto.PageResponse;
import smartcampus.entity.User;
import smartcampus.service.MarksService;

/**
 * {@code /api/marks} — bulk marks entry and G7 grade reporting.
 *
 * <p>Method security is not enabled on this build; role/ownership enforcement lives in
 * {@link MarksService}, which routes every write through {@code ScopedWriteAuthorizer}
 * against the tuple of the exam the marks belong to, and every {@code /me*} read through
 * {@code ScopedWriteAuthorizer.requireOwnStudent} — a {@code studentId} in a query
 * parameter is never honored on those routes.
 */
@RestController
@RequestMapping("/api/marks")
public class MarksController {

    private final MarksService marksService;

    public MarksController(MarksService marksService) {
        this.marksService = marksService;
    }

    /** FACULTY (via the assignment guard on the exam's tuple) or ADMIN. UPSERT, so 200. */
    @PostMapping("/bulk")
    public MarksBulkResponse bulk(
            @Valid @RequestBody MarksBulkRequest request, @AuthenticationPrincipal User caller) {
        return marksService.bulkUpsert(request, caller);
    }

    /** FACULTY (via the assignment guard) or ADMIN — the marks-entry screen's roster + existing scores. */
    @GetMapping("/entry-sheet")
    public MarksEntrySheetResponse entrySheet(
            @RequestParam Long examId, @AuthenticationPrincipal User caller) {
        return marksService.entrySheet(examId, caller);
    }

    /** FACULTY (via the assignment guard) or ADMIN. */
    @GetMapping("/exam/{examId}")
    public List<MarksResponse> listByExam(
            @PathVariable Long examId, @AuthenticationPrincipal User caller) {
        return marksService.listByExam(examId, caller);
    }

    /** FACULTY (via the assignment guard on the mark's exam tuple) or ADMIN. */
    @PutMapping("/{id}")
    public MarksResponse update(
            @PathVariable Long id,
            @Valid @RequestBody MarksUpdateRequest request,
            @AuthenticationPrincipal User caller) {
        return marksService.update(id, request, caller);
    }

    /** STUDENT — own rows only. */
    @GetMapping("/me")
    public PageResponse<MarksResponse> myMarks(
            @AuthenticationPrincipal User caller,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long examId,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return marksService.myMarks(caller, academicYear, semester, subjectId, examId, pageable);
    }

    /** STUDENT — own graded record only. Omit both query params for every year/semester + CGPA. */
    @GetMapping("/me/summary")
    public AcademicResultResponse mySummary(
            @AuthenticationPrincipal User caller,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester) {
        return marksService.mySummary(caller, academicYear, semester);
    }

    /** ADMIN only. */
    @GetMapping("/summary/{studentId}")
    public AcademicResultResponse summaryForStudent(
            @PathVariable Long studentId,
            @AuthenticationPrincipal User caller,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester) {
        return marksService.summaryForStudent(studentId, academicYear, semester, caller);
    }
}
