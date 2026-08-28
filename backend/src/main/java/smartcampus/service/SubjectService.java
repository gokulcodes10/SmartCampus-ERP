package smartcampus.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.SubjectCreateRequest;
import smartcampus.dto.SubjectResponse;
import smartcampus.dto.SubjectUpdateRequest;
import smartcampus.entity.Course;
import smartcampus.entity.Subject;
import smartcampus.exception.DuplicateResourceException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.CourseRepository;
import smartcampus.repository.SubjectRepository;

/**
 * Service for Subject entity operations: CRUD with server-side search, filtering,
 * sorting and pagination (all ADMIN-restricted operations).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final CourseRepository courseRepository;

    /**
     * Create a new subject.
     *
     * @param request the create request
     * @return the created subject's response DTO
     * @throws ResourceNotFoundException if course not found
     * @throws DuplicateResourceException if code already exists
     * @throws {@link org.springframework.dao.DataIntegrityViolationException} if credits
     *     or semester validation fails (DB CHECK constraints)
     */
    public SubjectResponse create(SubjectCreateRequest request) {
        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Course with ID " + request.courseId() + " not found."));

        if (subjectRepository.existsByCode(request.code())) {
            throw new DuplicateResourceException(
                    "Subject with code '" + request.code() + "' already exists.");
        }

        Subject subject = Subject.builder()
                .code(request.code())
                .name(request.name())
                .credits(request.credits())
                .semester(request.semester())
                .course(course)
                .build();

        Subject saved = subjectRepository.save(subject);
        return SubjectResponse.from(saved);
    }

    /**
     * Get a subject by ID.
     *
     * @param id the subject ID
     * @return the subject's response DTO
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public SubjectResponse getById(Long id) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subject with ID " + id + " not found."));
        return SubjectResponse.from(subject);
    }

    /**
     * List all subjects with server-side search, filtering, sorting and pagination.
     *
     * @param spec the dynamic filter specification
     * @param pageable pagination info
     * @return a page of subject response DTOs
     */
    @Transactional(readOnly = true)
    public Page<SubjectResponse> list(Specification<Subject> spec, Pageable pageable) {
        return subjectRepository.findAll(spec, pageable)
                .map(SubjectResponse::from);
    }

    /**
     * Update an existing subject.
     *
     * @param id the subject ID
     * @param request the update request
     * @return the updated subject's response DTO
     * @throws ResourceNotFoundException if subject or new course not found
     * @throws DuplicateResourceException if new code conflicts with another subject
     * @throws {@link org.springframework.dao.DataIntegrityViolationException} if credits
     *     or semester validation fails (DB CHECK constraints)
     */
    public SubjectResponse update(Long id, SubjectUpdateRequest request) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subject with ID " + id + " not found."));

        if (request.code() != null && !request.code().equals(subject.getCode())) {
            if (subjectRepository.existsByCode(request.code())) {
                throw new DuplicateResourceException(
                        "Subject with code '" + request.code() + "' already exists.");
            }
            subject.setCode(request.code());
        }

        if (request.name() != null) {
            subject.setName(request.name());
        }

        if (request.credits() != null) {
            subject.setCredits(request.credits());
        }

        if (request.semester() != null) {
            subject.setSemester(request.semester());
        }

        if (request.courseId() != null && !request.courseId().equals(
                subject.getCourse().getId())) {
            Course course = courseRepository.findById(request.courseId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Course with ID " + request.courseId() + " not found."));
            subject.setCourse(course);
        }

        Subject updated = subjectRepository.save(subject);
        return SubjectResponse.from(updated);
    }

    /**
     * Delete a subject.
     *
     * @param id the subject ID
     * @throws ResourceNotFoundException if subject not found
     * @throws {@link org.springframework.dao.DataIntegrityViolationException} if the
     *     subject has dependent enrollments (cascading deletes are not allowed per the
     *     constraint definition in V3__academic.sql)
     */
    public void delete(Long id) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subject with ID " + id + " not found."));
        subjectRepository.delete(subject);
    }
}
