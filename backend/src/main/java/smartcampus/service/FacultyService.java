package smartcampus.service;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.FacultyCreateRequest;
import smartcampus.dto.FacultyResponse;
import smartcampus.dto.FacultyUpdateRequest;
import smartcampus.dto.PageResponse;
import smartcampus.entity.Department;
import smartcampus.entity.Faculty;
import smartcampus.entity.FacultyStatus;
import smartcampus.entity.Role;
import smartcampus.entity.User;
import smartcampus.exception.BadRequestException;
import smartcampus.exception.DuplicateResourceException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.FacultyRepository;
import smartcampus.repository.UserRepository;

/**
 * Business logic for the {@code faculty} resource — admin CRUD with server-side
 * search/filter/pagination, and profile creation for an already-provisioned {@code
 * FACULTY} {@link User} (PROJECT_PLAN.md clarification G1 — the {@link User} row
 * itself is created separately, by an admin, via the existing {@code POST /api/users}
 * route; this service only attaches the academic profile to it).
 *
 * <p>Authorization is centralized here exactly as in {@link StudentService}: a {@code
 * FACULTY} caller may only ever see their own profile — "nothing more" — via {@link
 * #getById} or {@link #getOwnProfile}; the list endpoint is {@code ADMIN}-only. The
 * students a faculty member teaches are exposed through {@code StudentService.list},
 * not duplicated here.
 */
@Service
public class FacultyService {

    private static final Logger log = LoggerFactory.getLogger(FacultyService.class);

    private final FacultyRepository facultyRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    public FacultyService(
            FacultyRepository facultyRepository,
            UserRepository userRepository,
            DepartmentRepository departmentRepository) {
        this.facultyRepository = facultyRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
    }

    /** Admin-only server-side search/filter/pagination (§44) over faculty profiles. */
    @Transactional(readOnly = true)
    public PageResponse<FacultyResponse> list(
            User caller,
            Long departmentId,
            FacultyStatus status,
            String q,
            int page,
            int size) {
        requireAdmin(caller);
        Specification<Faculty> spec = buildSpecification(departmentId, status, q);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Faculty> result = facultyRepository.findAll(spec, pageRequest);
        return PageResponse.from(result.map(FacultyResponse::from));
    }

    /** The caller's own profile — {@code GET /api/faculty/me}. */
    @Transactional(readOnly = true)
    public FacultyResponse getOwnProfile(User caller) {
        Faculty faculty =
                facultyRepository
                        .findByUserId(caller.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Faculty profile not found."));
        return FacultyResponse.from(faculty);
    }

    /**
     * {@code GET /api/faculty/{id}}. {@code ADMIN} sees any profile; a {@code FACULTY}
     * caller sees only their own (identical 404, not 403, for a mismatch — see {@link
     * StudentService#assertViewable} for why); a {@code STUDENT} caller is rejected
     * outright, matching "nothing more" in this class's javadoc.
     */
    @Transactional(readOnly = true)
    public FacultyResponse getById(Long id, User caller) {
        Faculty faculty = loadOrThrow(id);
        switch (caller.getRole()) {
            case ADMIN -> {
                // full access
            }
            case FACULTY -> {
                if (!faculty.getUser().getId().equals(caller.getId())) {
                    throw new ResourceNotFoundException("Faculty not found.");
                }
            }
            case STUDENT -> throw new AccessDeniedException("Students cannot view faculty records.");
        }
        return FacultyResponse.from(faculty);
    }

    @Transactional
    public FacultyResponse create(FacultyCreateRequest request, User caller) {
        requireAdmin(caller);

        User targetUser =
                userRepository
                        .findById(request.userId())
                        .orElseThrow(() -> new BadRequestException("User not found."));
        if (targetUser.getRole() != Role.FACULTY) {
            throw new BadRequestException("The linked account must have the FACULTY role.");
        }
        if (facultyRepository.findByUserId(targetUser.getId()).isPresent()) {
            throw new DuplicateResourceException("This user already has a faculty profile.");
        }

        String employeeCode = request.employeeCode().trim();
        if (facultyRepository.existsByEmployeeCode(employeeCode)) {
            throw new DuplicateResourceException("Employee code is already in use.");
        }

        Department department =
                departmentRepository
                        .findById(request.departmentId())
                        .orElseThrow(() -> new BadRequestException("Department not found."));

        Faculty faculty =
                Faculty.builder()
                        .user(targetUser)
                        .employeeCode(employeeCode)
                        .department(department)
                        .designation(request.designation())
                        .status(FacultyStatus.ACTIVE)
                        .build();

        try {
            faculty = facultyRepository.saveAndFlush(faculty);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException(
                    "Unable to create faculty profile — employee code or user is already in use.");
        }

        log.info("Admin {} created faculty profile for {}", caller.getEmail(), targetUser.getEmail());
        return FacultyResponse.from(faculty);
    }

    @Transactional
    public FacultyResponse update(Long id, FacultyUpdateRequest request, User caller) {
        requireAdmin(caller);
        Faculty faculty = loadOrThrow(id);

        if (request.employeeCode() != null) {
            String employeeCode = request.employeeCode().trim();
            if (!employeeCode.equals(faculty.getEmployeeCode())
                    && facultyRepository.existsByEmployeeCode(employeeCode)) {
                throw new DuplicateResourceException("Employee code is already in use.");
            }
            faculty.setEmployeeCode(employeeCode);
        }
        if (request.departmentId() != null) {
            Department department =
                    departmentRepository
                            .findById(request.departmentId())
                            .orElseThrow(() -> new BadRequestException("Department not found."));
            faculty.setDepartment(department);
        }
        if (request.designation() != null) {
            faculty.setDesignation(request.designation());
        }
        if (request.status() != null) {
            faculty.setStatus(request.status());
        }

        try {
            faculty = facultyRepository.saveAndFlush(faculty);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("Employee code is already in use.");
        }
        return FacultyResponse.from(faculty);
    }

    private void requireAdmin(User caller) {
        if (caller.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only administrators can perform this action.");
        }
    }

    private Faculty loadOrThrow(Long id) {
        return facultyRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Faculty not found."));
    }

    private Specification<Faculty> buildSpecification(
            Long departmentId, FacultyStatus status, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (departmentId != null) {
                predicates.add(cb.equal(root.get("department").get("id"), departmentId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                Join<Faculty, User> userJoin = root.join("user", JoinType.LEFT);
                predicates.add(
                        cb.or(
                                cb.like(cb.lower(userJoin.get("fullName")), like),
                                cb.like(cb.lower(userJoin.get("email")), like),
                                cb.like(cb.lower(root.get("employeeCode")), like)));
            }
            if (query != null) {
                query.distinct(true);
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
