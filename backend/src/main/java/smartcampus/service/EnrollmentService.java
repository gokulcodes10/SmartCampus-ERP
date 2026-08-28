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
import smartcampus.dto.EnrollmentRequest;
import smartcampus.dto.EnrollmentResponse;
import smartcampus.dto.PageResponse;
import smartcampus.entity.Enrollment;
import smartcampus.entity.EnrollmentStatus;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.Subject;
import smartcampus.exception.BadRequestException;
import smartcampus.exception.DuplicateResourceException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.EnrollmentRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.SubjectRepository;

/**
 * Admin enrollment of a student into a subject for a given academic year, semester and
 * section — the roster that Phase 4 attendance and marks entry read from.
 *
 * <p>Duplicate enrollment (same student + subject + academic year + semester,
 * regardless of the row's {@code status}) is rejected cleanly with a 409, matching the
 * database's {@code uk_enrollments_student_subject_year_sem} unique constraint. The
 * check happens twice on purpose: an application-level pre-check
 * ({@link EnrollmentRepository#findByStudentIdAndSubjectIdAndAcademicYearAndSemester})
 * gives a clean {@link DuplicateResourceException} in the common case, and a catch
 * around the actual {@code save} translates a concurrent race that slips past the
 * pre-check (two requests, same tuple, both pass the check before either commits) into
 * the same clean error instead of a raw {@link DataIntegrityViolationException} leaking
 * out as a 500.
 */
@Service
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;

    public EnrollmentService(
            EnrollmentRepository enrollmentRepository,
            StudentRepository studentRepository,
            SubjectRepository subjectRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.studentRepository = studentRepository;
        this.subjectRepository = subjectRepository;
    }

    @Transactional
    public EnrollmentResponse enroll(EnrollmentRequest request) {
        Student student =
                studentRepository
                        .findById(request.studentId())
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Student not found: " + request.studentId()));

        // An ACTIVE student is the one with department/course/current_semester already
        // assigned (G1) — enrolling a still-PENDING student would attach a roster row
        // to a profile with no department/course context to make sense of it.
        if (student.getStatus() != StudentStatus.ACTIVE) {
            throw new BadRequestException(
                    "Student " + request.studentId() + " is not ACTIVE (status is "
                            + student.getStatus() + "); activate the student before enrolling.");
        }

        Subject subject =
                subjectRepository
                        .findById(request.subjectId())
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Subject not found: " + request.subjectId()));

        enrollmentRepository
                .findByStudentIdAndSubjectIdAndAcademicYearAndSemester(
                        request.studentId(),
                        request.subjectId(),
                        request.academicYear(),
                        request.semester())
                .ifPresent(existing -> {
                    throw duplicateException(request);
                });

        Enrollment enrollment =
                Enrollment.builder()
                        .student(student)
                        .subject(subject)
                        .academicYear(request.academicYear())
                        .semester(request.semester())
                        .section(request.section())
                        .status(EnrollmentStatus.ACTIVE)
                        .build();

        try {
            enrollment = enrollmentRepository.save(enrollment);
            enrollmentRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw duplicateException(request);
        }

        return EnrollmentResponse.from(enrollment);
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse getById(Long id) {
        return EnrollmentResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<EnrollmentResponse> list(
            Long studentId,
            Long subjectId,
            String academicYear,
            Integer semester,
            String section,
            EnrollmentStatus status,
            Pageable pageable) {
        Specification<Enrollment> spec =
                buildFilter(studentId, subjectId, academicYear, semester, section, status);
        Page<Enrollment> page = enrollmentRepository.findAll(spec, pageable);
        return PageResponse.of(page, EnrollmentResponse::from);
    }

    @Transactional
    public EnrollmentResponse updateStatus(Long id, EnrollmentStatus status) {
        Enrollment enrollment = findOrThrow(id);
        enrollment.setStatus(status);
        return EnrollmentResponse.from(enrollment);
    }

    @Transactional
    public void delete(Long id) {
        if (!enrollmentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Enrollment not found: " + id);
        }
        enrollmentRepository.deleteById(id);
    }

    private Enrollment findOrThrow(Long id) {
        return enrollmentRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found: " + id));
    }

    private static DuplicateResourceException duplicateException(EnrollmentRequest request) {
        return new DuplicateResourceException(
                "Student " + request.studentId() + " is already enrolled in subject "
                        + request.subjectId() + " for " + request.academicYear() + " semester "
                        + request.semester() + ".");
    }

    private Specification<Enrollment> buildFilter(
            Long studentId,
            Long subjectId,
            String academicYear,
            Integer semester,
            String section,
            EnrollmentStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (studentId != null) {
                predicates.add(cb.equal(root.get("student").get("id"), studentId));
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
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
