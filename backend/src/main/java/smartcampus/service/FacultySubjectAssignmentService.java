package smartcampus.service;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.FacultySubjectAssignmentRequest;
import smartcampus.dto.FacultySubjectAssignmentResponse;
import smartcampus.dto.PageResponse;
import smartcampus.entity.Faculty;
import smartcampus.entity.FacultyStatus;
import smartcampus.entity.FacultySubjectAssignment;
import smartcampus.entity.Subject;
import smartcampus.exception.BadRequestException;
import smartcampus.exception.DuplicateResourceException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.FacultyRepository;
import smartcampus.repository.FacultySubjectAssignmentRepository;
import smartcampus.repository.SubjectRepository;

/**
 * Admin management of which faculty teaches which subject/section, in which academic
 * year and semester (PROJECT_PLAN.md clarification G2).
 *
 * <p>This is the table {@link AcademicAccessGuard} reads for every faculty
 * authorization decision in this and later phases — a row created here grants exactly
 * that (faculty, subject, academicYear, semester, section) tuple and nothing broader;
 * an assignment to one section never authorizes another.
 *
 * <p>Duplicate assignment (the same tuple twice) is rejected cleanly with a 409,
 * matching {@code uk_fsa_faculty_subject_year_sem_section}. As in
 * {@link EnrollmentService}, the check happens both as an application-level pre-check
 * and as a catch around the save, so a concurrent race lands on the same clean error
 * instead of a raw {@link DataIntegrityViolationException} surfacing as a 500.
 */
@Service
public class FacultySubjectAssignmentService {

    private final FacultySubjectAssignmentRepository assignmentRepository;
    private final FacultyRepository facultyRepository;
    private final SubjectRepository subjectRepository;

    public FacultySubjectAssignmentService(
            FacultySubjectAssignmentRepository assignmentRepository,
            FacultyRepository facultyRepository,
            SubjectRepository subjectRepository) {
        this.assignmentRepository = assignmentRepository;
        this.facultyRepository = facultyRepository;
        this.subjectRepository = subjectRepository;
    }

    @Transactional
    public FacultySubjectAssignmentResponse assign(FacultySubjectAssignmentRequest request) {
        Faculty faculty =
                facultyRepository
                        .findById(request.facultyId())
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Faculty not found: " + request.facultyId()));

        if (faculty.getStatus() != FacultyStatus.ACTIVE) {
            throw new BadRequestException(
                    "Faculty " + request.facultyId()
                            + " is INACTIVE and cannot be assigned to teach a subject.");
        }

        Subject subject =
                subjectRepository
                        .findById(request.subjectId())
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Subject not found: " + request.subjectId()));

        assignmentRepository
                .findByFacultyIdAndSubjectIdAndAcademicYearAndSemesterAndSection(
                        request.facultyId(),
                        request.subjectId(),
                        request.academicYear(),
                        request.semester(),
                        request.section())
                .ifPresent(existing -> {
                    throw duplicateException(request);
                });

        FacultySubjectAssignment assignment =
                FacultySubjectAssignment.builder()
                        .faculty(faculty)
                        .subject(subject)
                        .academicYear(request.academicYear())
                        .semester(request.semester())
                        .section(request.section())
                        .build();

        try {
            assignment = assignmentRepository.save(assignment);
            assignmentRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw duplicateException(request);
        }

        return FacultySubjectAssignmentResponse.from(assignment);
    }

    @Transactional(readOnly = true)
    public FacultySubjectAssignmentResponse getById(Long id) {
        return FacultySubjectAssignmentResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<FacultySubjectAssignmentResponse> list(
            Long facultyId,
            Long subjectId,
            String academicYear,
            Integer semester,
            String section,
            Pageable pageable) {
        Specification<FacultySubjectAssignment> spec =
                buildFilter(facultyId, subjectId, academicYear, semester, section);
        Page<FacultySubjectAssignment> page = assignmentRepository.findAll(spec, pageable);
        return PageResponse.of(page, FacultySubjectAssignmentResponse::from);
    }

    @Transactional
    public void unassign(Long id) {
        if (!assignmentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Faculty-subject assignment not found: " + id);
        }
        assignmentRepository.deleteById(id);
    }

    private FacultySubjectAssignment findOrThrow(Long id) {
        return assignmentRepository
                .findById(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                "Faculty-subject assignment not found: " + id));
    }

    private static DuplicateResourceException duplicateException(
            FacultySubjectAssignmentRequest request) {
        return new DuplicateResourceException(
                "Faculty " + request.facultyId() + " is already assigned to subject "
                        + request.subjectId() + " for " + request.academicYear() + " semester "
                        + request.semester() + " section " + request.section() + ".");
    }

    private Specification<FacultySubjectAssignment> buildFilter(
            Long facultyId, Long subjectId, String academicYear, Integer semester, String section) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (facultyId != null) {
                predicates.add(cb.equal(root.get("faculty").get("id"), facultyId));
            }
            if (subjectId != null) {
                predicates.add(cb.equal(root.get("subject").get("id"), subjectId));
            }
            if (academicYear != null && !academicYear.isBlank()) {
                predicates.add(cb.equal(root.get("academicYear"), academicYear));
            }
            if (semester != null) {
                predicates.add(cb.equal(root.get("semester"), semester));
            }
            if (section != null && !section.isBlank()) {
                predicates.add(cb.equal(root.get("section"), section));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
