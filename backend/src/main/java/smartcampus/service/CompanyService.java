package smartcampus.service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.CompanyCreateRequest;
import smartcampus.dto.CompanyResponse;
import smartcampus.dto.CompanyUpdateRequest;
import smartcampus.dto.PageResponse;
import smartcampus.entity.Company;
import smartcampus.entity.CompanyStatus;
import smartcampus.entity.User;
import smartcampus.exception.DuplicateResourceException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.CompanyRepository;
import smartcampus.repository.JobRepository;
import smartcampus.repository.projection.CompanyJobCounts;

/**
 * §33: the recruiting-organisation catalog an admin maintains. Every placement drive
 * hangs off one of these rows; a company with any drive on file may never be
 * hard-deleted (see {@link #delete}), only deactivated via {@link CompanyStatus#INACTIVE}.
 *
 * <p>Method security is not enabled on this build; every ADMIN-only method here starts
 * with {@link ScopedWriteAuthorizer#requireAdmin}.
 */
@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final JobRepository jobRepository;
    private final ScopedWriteAuthorizer scopedWriteAuthorizer;

    public CompanyService(
            CompanyRepository companyRepository,
            JobRepository jobRepository,
            ScopedWriteAuthorizer scopedWriteAuthorizer) {
        this.companyRepository = companyRepository;
        this.jobRepository = jobRepository;
        this.scopedWriteAuthorizer = scopedWriteAuthorizer;
    }

    @Transactional
    public CompanyResponse create(CompanyCreateRequest request, User caller) {
        scopedWriteAuthorizer.requireAdmin(caller);

        if (companyRepository.existsByNameIgnoreCase(request.name())) {
            throw duplicateNameException(request.name());
        }

        Company company =
                Company.builder()
                        .name(request.name())
                        .industry(request.industry())
                        .website(request.website())
                        .description(request.description())
                        .location(request.location())
                        .contactPerson(request.contactPerson())
                        .contactEmail(request.contactEmail())
                        .contactPhone(request.contactPhone())
                        .status(CompanyStatus.ACTIVE)
                        .build();

        try {
            company = companyRepository.save(company);
            companyRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw duplicateNameException(request.name());
        }

        return toResponse(company, 0L, 0L);
    }

    @Transactional(readOnly = true)
    public PageResponse<CompanyResponse> list(
            CompanyStatus status, String industry, String search, Pageable pageable) {
        Specification<Company> spec = buildFilter(status, industry, search);
        Page<Company> page = companyRepository.findAll(spec, pageable);

        List<Long> ids = page.getContent().stream().map(Company::getId).toList();
        Map<Long, CompanyJobCounts> counts = countsById(ids);

        return PageResponse.of(page, company -> toResponse(company, counts));
    }

    @Transactional(readOnly = true)
    public CompanyResponse getById(Long id) {
        Company company = findOrThrow(id);
        Map<Long, CompanyJobCounts> counts = countsById(List.of(id));
        return toResponse(company, counts);
    }

    @Transactional
    public CompanyResponse update(Long id, CompanyUpdateRequest request, User caller) {
        scopedWriteAuthorizer.requireAdmin(caller);
        Company company = findOrThrow(id);

        if (!request.name().equalsIgnoreCase(company.getName())
                && companyRepository.existsByNameIgnoreCase(request.name())) {
            throw duplicateNameException(request.name());
        }

        company.setName(request.name());
        company.setIndustry(request.industry());
        company.setWebsite(request.website());
        company.setDescription(request.description());
        company.setLocation(request.location());
        company.setContactPerson(request.contactPerson());
        company.setContactEmail(request.contactEmail());
        company.setContactPhone(request.contactPhone());
        company.setStatus(request.status());

        try {
            company = companyRepository.save(company);
            companyRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw duplicateNameException(request.name());
        }

        Map<Long, CompanyJobCounts> counts = countsById(List.of(id));
        return toResponse(company, counts);
    }

    @Transactional
    public void delete(Long id, User caller) {
        scopedWriteAuthorizer.requireAdmin(caller);
        Company company = findOrThrow(id);

        if (jobRepository.existsByCompanyId(id)) {
            throw new DuplicateResourceException(
                    "Cannot delete a company that has placement drives; deactivate it instead.");
        }

        try {
            companyRepository.delete(company);
            companyRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException(
                    "Cannot delete a company that has placement drives; deactivate it instead.");
        }
    }

    private Company findOrThrow(Long id) {
        return companyRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + id));
    }

    private Map<Long, CompanyJobCounts> countsById(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, CompanyJobCounts> map = new HashMap<>();
        for (CompanyJobCounts c : jobRepository.countByCompanyIds(ids)) {
            map.put(c.getCompanyId(), c);
        }
        return map;
    }

    private CompanyResponse toResponse(Company company, Map<Long, CompanyJobCounts> counts) {
        CompanyJobCounts c = counts.get(company.getId());
        long total = c == null ? 0L : c.getTotalJobs();
        long open = c == null ? 0L : c.getOpenJobs();
        return toResponse(company, total, open);
    }

    private CompanyResponse toResponse(Company company, long jobCount, long openJobCount) {
        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getIndustry(),
                company.getWebsite(),
                company.getDescription(),
                company.getLocation(),
                company.getContactPerson(),
                company.getContactEmail(),
                company.getContactPhone(),
                company.getStatus(),
                jobCount,
                openJobCount,
                company.getCreatedAt(),
                company.getUpdatedAt());
    }

    private static DuplicateResourceException duplicateNameException(String name) {
        return new DuplicateResourceException("A company named '" + name + "' already exists.");
    }

    private Specification<Company> buildFilter(CompanyStatus status, String industry, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (industry != null && !industry.isBlank()) {
                predicates.add(cb.equal(root.get("industry"), industry));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
