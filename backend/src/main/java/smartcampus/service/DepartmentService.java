package smartcampus.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.DepartmentCreateRequest;
import smartcampus.dto.DepartmentResponse;
import smartcampus.dto.DepartmentUpdateRequest;
import smartcampus.entity.Department;
import smartcampus.exception.DuplicateResourceException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.DepartmentRepository;

/**
 * Service for Department entity operations: CRUD with server-side search, filtering,
 * sorting and pagination (all ADMIN-restricted operations).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    /**
     * Create a new department.
     *
     * @param request the create request
     * @return the created department's response DTO
     * @throws DuplicateResourceException if code or name already exists
     */
    public DepartmentResponse create(DepartmentCreateRequest request) {
        if (departmentRepository.existsByCode(request.code())) {
            throw new DuplicateResourceException(
                    "Department with code '" + request.code() + "' already exists.");
        }
        if (departmentRepository.existsByName(request.name())) {
            throw new DuplicateResourceException(
                    "Department with name '" + request.name() + "' already exists.");
        }

        Department department = Department.builder()
                .code(request.code())
                .name(request.name())
                .build();

        Department saved = departmentRepository.save(department);
        return DepartmentResponse.from(saved);
    }

    /**
     * Get a department by ID.
     *
     * @param id the department ID
     * @return the department's response DTO
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public DepartmentResponse getById(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Department with ID " + id + " not found."));
        return DepartmentResponse.from(department);
    }

    /**
     * List all departments with server-side search, filtering, sorting and pagination.
     *
     * @param spec the dynamic filter specification
     * @param pageable pagination info
     * @return a page of department response DTOs
     */
    @Transactional(readOnly = true)
    public Page<DepartmentResponse> list(Specification<Department> spec, Pageable pageable) {
        return departmentRepository.findAll(spec, pageable)
                .map(DepartmentResponse::from);
    }

    /**
     * Update an existing department.
     *
     * @param id the department ID
     * @param request the update request
     * @return the updated department's response DTO
     * @throws ResourceNotFoundException if department not found
     * @throws DuplicateResourceException if new code or name conflicts with another department
     */
    public DepartmentResponse update(Long id, DepartmentUpdateRequest request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Department with ID " + id + " not found."));

        if (request.code() != null && !request.code().equals(department.getCode())) {
            if (departmentRepository.existsByCode(request.code())) {
                throw new DuplicateResourceException(
                        "Department with code '" + request.code() + "' already exists.");
            }
            department.setCode(request.code());
        }

        if (request.name() != null && !request.name().equals(department.getName())) {
            if (departmentRepository.existsByName(request.name())) {
                throw new DuplicateResourceException(
                        "Department with name '" + request.name() + "' already exists.");
            }
            department.setName(request.name());
        }

        Department updated = departmentRepository.save(department);
        return DepartmentResponse.from(updated);
    }

    /**
     * Delete a department.
     *
     * @param id the department ID
     * @throws ResourceNotFoundException if department not found
     * @throws {@link org.springframework.dao.DataIntegrityViolationException} if the
     *     department has dependent courses (cascading deletes are not allowed per the
     *     constraint definition in V3__academic.sql)
     */
    public void delete(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Department with ID " + id + " not found."));
        departmentRepository.delete(department);
    }
}
