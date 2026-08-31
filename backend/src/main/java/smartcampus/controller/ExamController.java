package smartcampus.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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
import smartcampus.dto.ExamCreateRequest;
import smartcampus.dto.ExamResponse;
import smartcampus.dto.ExamUpdateRequest;
import smartcampus.dto.PageResponse;
import smartcampus.entity.ExamStatus;
import smartcampus.entity.ExamType;
import smartcampus.entity.User;
import smartcampus.service.ExamService;

/**
 * {@code /api/exams} — G4 exam scheduling (an "assignment" per G5 is simply {@code
 * examType = ASSIGNMENT}).
 *
 * <p>Method security is not enabled on this build; role/ownership enforcement lives in
 * {@link ExamService}, which routes every write through {@code ScopedWriteAuthorizer}.
 */
@RestController
@RequestMapping("/api/exams")
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    /** FACULTY (via the assignment guard) or ADMIN. */
    @PostMapping
    public ResponseEntity<ExamResponse> create(
            @Valid @RequestBody ExamCreateRequest request, @AuthenticationPrincipal User caller) {
        ExamResponse response = examService.create(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Any authenticated role — server-side search/filter/sort/pagination per §44. */
    @GetMapping
    public PageResponse<ExamResponse> list(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) ExamType examType,
            @RequestParam(required = false) ExamStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "examDate") Pageable pageable) {
        return examService.list(
                subjectId, academicYear, semester, section, examType, status, fromDate, toDate, search, pageable);
    }

    /**
     * G4: upcoming SCHEDULED exams (examDate &gt;= today), scoped by caller role — see
     * {@link ExamService#upcoming}.
     */
    @GetMapping("/upcoming")
    public List<ExamResponse> upcoming(
            @AuthenticationPrincipal User caller, @RequestParam(defaultValue = "10") int limit) {
        return examService.upcoming(caller, limit);
    }

    /** Any authenticated role. */
    @GetMapping("/{id}")
    public ExamResponse getById(@PathVariable Long id) {
        return examService.getById(id);
    }

    /** FACULTY (via the assignment guard, against the exam's OWN tuple) or ADMIN. */
    @PutMapping("/{id}")
    public ExamResponse update(
            @PathVariable Long id,
            @Valid @RequestBody ExamUpdateRequest request,
            @AuthenticationPrincipal User caller) {
        return examService.update(id, request, caller);
    }

    /** FACULTY (via the assignment guard) or ADMIN; 409 if any marks row references it. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        examService.delete(id, caller);
        return ResponseEntity.noContent().build();
    }
}
