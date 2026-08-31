package smartcampus.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.PageResponse;
import smartcampus.dto.ResumeDuplicateRequest;
import smartcampus.dto.ResumePrefillResponse;
import smartcampus.dto.ResumeResponse;
import smartcampus.dto.ResumeSaveRequest;
import smartcampus.dto.ResumeSummaryResponse;
import smartcampus.entity.User;
import smartcampus.service.ResumeService;

/**
 * {@code /api/resumes} - §37 resume builder.
 *
 * <p>Method security is not enabled on this build; role/ownership enforcement lives in
 * {@link ResumeService}.
 */
@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    /** STUDENT only. */
    @PostMapping
    public ResponseEntity<ResumeResponse> create(
            @Valid @RequestBody ResumeSaveRequest request, @AuthenticationPrincipal User caller) {
        ResumeResponse response = resumeService.create(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** STUDENT only - the caller's own resumes. */
    @GetMapping("/me")
    public PageResponse<ResumeSummaryResponse> myResumes(
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User caller) {
        return resumeService.myResumes(caller, pageable);
    }

    /** STUDENT only - §69-honest suggested starting values for a new resume. */
    @GetMapping("/prefill")
    public ResumePrefillResponse prefill(@AuthenticationPrincipal User caller) {
        return resumeService.prefill(caller);
    }

    /** Owner STUDENT or ADMIN; 404 (never 403) otherwise - an id must not be probeable. */
    @GetMapping("/{id}")
    public ResumeResponse getById(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        return resumeService.getById(id, caller);
    }

    /** Owner STUDENT only. 409 if the resume is locked (attached to an application). */
    @PutMapping("/{id}")
    public ResumeResponse update(
            @PathVariable Long id,
            @Valid @RequestBody ResumeSaveRequest request,
            @AuthenticationPrincipal User caller) {
        return resumeService.update(id, request, caller);
    }

    /** Owner STUDENT only. 409 if the resume is locked (attached to an application). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        resumeService.delete(id, caller);
        return ResponseEntity.noContent().build();
    }

    /** Owner STUDENT only. Works even on a locked resume - this is the §35 escape hatch. */
    @PostMapping("/{id}/duplicate")
    public ResponseEntity<ResumeResponse> duplicate(
            @PathVariable Long id,
            @Valid @RequestBody ResumeDuplicateRequest request,
            @AuthenticationPrincipal User caller) {
        ResumeResponse response = resumeService.duplicate(id, request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Owner STUDENT or ADMIN; 404 (never 403) otherwise. */
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        ResumeService.ResumePdf pdf = resumeService.renderPdf(id, caller);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.fileName()).build().toString())
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(pdf.bytes().length))
                .body(pdf.bytes());
    }
}
