package smartcampus.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.CourseCreateRequest;
import smartcampus.dto.CourseResponse;
import smartcampus.dto.CourseUpdateRequest;
import smartcampus.entity.Course;
import smartcampus.entity.Department;
import smartcampus.exception.DuplicateResourceException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.CourseRepository;
import smartcampus.repository.DepartmentRepository;

/**
 * Service for Course entity operations: CRUD with server-side search, filtering,
 * sorting and pagination (all ADMIN-restricted operations).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CourseService {

    private final CourseRepository courseRepository;
    private final DepartmentRepository departmentRepository;

    /**
     * Create a new course.
     *
     * @param request the create request
     * @return the created course's response DTO
     * @throws ResourceNotFoundException if department not found
     * @throws DuplicateResourceException if code already exists
     */
    public CourseResponse create(CourseCreateRequest request) {
        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Department with ID " + request.departmentId() + " not found."));

        if (courseRepository.existsByCode(request.code())) {
            throw new DuplicateResourceException(
                    "Course with code '" + request.code() + "' already exists.");
        }

        Course course = Course.builder()
                .code(request.code())
                .name(request.name())
                .department(department)
                .durationSemesters(request.durationSemesters() != null ? request.durationSemesters() : 8)
                .build();

        Course saved = courseRepository.save(course);
        return CourseResponse.from(saved);
    }

    /**
     * Get a course by ID.
     *
     * @param id the course ID
     * @return the course's response DTO
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public CourseResponse getById(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Course with ID " + id + " not found."));
        return CourseResponse.from(course);
    }

    /**
     * List all courses with server-side search, filtering, sorting and pagination.
     *
     * @param spec the dynamic filter specification
     * @param pageable pagination info
     * @return a page of course response DTOs
     */
    @Transactional(readOnly = true)
    public Page<CourseResponse> list(Specification<Course> spec, Pageable pageable) {
        return courseRepository.findAll(spec, pageable)
                .map(CourseResponse::from);
    }

    /**
     * Update an existing course.
     *
     * @param id the course ID
     * @param request the update request
     * @return the updated course's response DTO
     * @throws ResourceNotFoundException if course or new department not found
     * @throws DuplicateResourceException if new code conflicts with another course
     */
    public CourseResponse update(Long id, CourseUpdateRequest request) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Course with ID " + id + " not found."));

        if (request.code() != null && !request.code().equals(course.getCode())) {
            if (courseRepository.existsByCode(request.code())) {
                throw new DuplicateResourceException(
                        "Course with code '" + request.code() + "' already exists.");
            }
            course.setCode(request.code());
        }

        if (request.name() != null) {
            course.setName(request.name());
        }

        if (request.departmentId() != null && !request.departmentId().equals(
                course.getDepartment().getId())) {
            Department department = departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Department with ID " + request.departmentId() + " not found."));
            course.setDepartment(department);
        }

        if (request.durationSemesters() != null) {
            course.setDurationSemesters(request.durationSemesters());
        }

        Course updated = courseRepository.save(course);
        return CourseResponse.from(updated);
    }

    /**
     * Delete a course.
     *
     * @param id the course ID
     * @throws ResourceNotFoundException if course not found
     * @throws {@link org.springframework.dao.DataIntegrityViolationException} if the
     *     course has dependent subjects (cascading deletes are not allowed per the
     *     constraint definition in V3__academic.sql)
     */
    public void delete(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Course with ID " + id + " not found."));
        courseRepository.delete(course);
    }
}
