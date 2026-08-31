package smartcampus.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import smartcampus.dto.CompanyCreateRequest;
import smartcampus.dto.CompanyResponse;
import smartcampus.dto.CompanyUpdateRequest;
import smartcampus.dto.PageResponse;
import smartcampus.entity.CompanyStatus;
import smartcampus.entity.User;
import smartcampus.service.CompanyService;

/**
 * {@code /api/companies} — §33 the recruiting-organisation catalog.
 *
 * <p>Method security is not enabled on this build; role enforcement lives in {@link
 * CompanyService}, which routes every write through {@code ScopedWriteAuthorizer}.
 */
@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    /** ADMIN only. */
    @PostMapping
    public ResponseEntity<CompanyResponse> create(
            @Valid @RequestBody CompanyCreateRequest request, @AuthenticationPrincipal User caller) {
        CompanyResponse response = companyService.create(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Any authenticated role — server-side search/filter/sort/pagination per §44. */
    @GetMapping
    public PageResponse<CompanyResponse> list(
            @RequestParam(required = false) CompanyStatus status,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return companyService.list(status, industry, search, pageable);
    }

    /** Any authenticated role. */
    @GetMapping("/{id}")
    public CompanyResponse getById(@PathVariable Long id) {
        return companyService.getById(id);
    }

    /** ADMIN only. */
    @PutMapping("/{id}")
    public CompanyResponse update(
            @PathVariable Long id,
            @Valid @RequestBody CompanyUpdateRequest request,
            @AuthenticationPrincipal User caller) {
        return companyService.update(id, request, caller);
    }

    /** ADMIN only; 409 when any job references this company. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        companyService.delete(id, caller);
        return ResponseEntity.noContent().build();
    }
}
