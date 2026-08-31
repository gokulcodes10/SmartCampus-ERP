package smartcampus.service;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.ExamCreateRequest;
import smartcampus.dto.ExamResponse;
import smartcampus.dto.ExamUpdateRequest;
import smartcampus.dto.PageResponse;
import smartcampus.entity.EnrollmentStatus;
import smartcampus.entity.Exam;
import smartcampus.entity.ExamStatus;
import smartcampus.entity.ExamType;
import smartcampus.entity.Faculty;
import smartcampus.entity.FacultySubjectAssignment;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.Subject;
import smartcampus.entity.User;
import smartcampus.exception.DuplicateResourceException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.EnrollmentRepository;
import smartcampus.repository.ExamRepository;
import smartcampus.repository.FacultyRepository;
import smartcampus.repository.FacultySubjectAssignmentRepository;
import smartcampus.repository.MarksRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.SubjectRepository;

/**
 * G4: scheduling and listing exams (assignments included — G5 says an "assignment" is
 * simply {@code examType = ASSIGNMENT}, there is no separate submission module).
 *
 * <p>Every write routes through {@link ScopedWriteAuthorizer#requireScopedWrite}, which
 * delegates to {@link AcademicAccessGuard}. The exam's own {@code (subjectId,
 * academicYear, semester, section)} tuple is what create is authorized against, and what
 * update/delete re-derive from the already-persisted row — never from the request body,
 * since {@link ExamUpdateRequest} deliberately does not carry that tuple.
 */
@Service
public class ExamService {

    private final ExamRepository examRepository;
    private final SubjectRepository subjectRepository;
    private final MarksRepository marksRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final FacultySubjectAssignmentRepository facultySubjectAssignmentRepository;
    private final StudentRepository studentRepository;
    private final FacultyRepository facultyRepository;
    private final ScopedWriteAuthorizer scopedWriteAuthorizer;

    public ExamService(
            ExamRepository examRepository,
            SubjectRepository subjectRepository,
            MarksRepository marksRepository,
            EnrollmentRepository enrollmentRepository,
            FacultySubjectAssignmentRepository facultySubjectAssignmentRepository,
            StudentRepository studentRepository,
            FacultyRepository facultyRepository,
            ScopedWriteAuthorizer scopedWriteAuthorizer) {
        this.examRepository = examRepository;
        this.subjectRepository = subjectRepository;
        this.marksRepository = marksRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.facultySubjectAssignmentRepository = facultySubjectAssignmentRepository;
        this.studentRepository = studentRepository;
        this.facultyRepository = facultyRepository;
        this.scopedWriteAuthorizer = scopedWriteAuthorizer;
    }

    @Transactional
    public ExamResponse create(ExamCreateRequest request, User caller) {
        scopedWriteAuthorizer.requireScopedWrite(
                caller, request.subjectId(), request.academicYear(), request.semester(), request.section());

        Subject subject =
                subjectRepository
                        .findById(request.subjectId())
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Subject not found: " + request.subjectId()));

        examRepository
                .findBySubjectIdAndAcademicYearAndSemesterAndSectionAndExamTypeAndTitle(
                        request.subjectId(),
                        request.academicYear(),
                        request.semester(),
                        request.section(),
                        request.examType(),
                        request.title())
                .ifPresent(
                        existing -> {
                            throw duplicateExamException(request.title());
                        });

        Faculty faculty = scopedWriteAuthorizer.facultyOrNull(caller);

        Exam exam =
                Exam.builder()
                        .subject(subject)
                        .faculty(faculty)
                        .title(request.title())
                        .examType(request.examType())
                        .academicYear(request.academicYear())
                        .semester(request.semester())
                        .section(request.section())
                        .examDate(request.examDate())
                        .maximumMarks(request.maximumMarks())
                        .status(ExamStatus.SCHEDULED)
                        .build();

        try {
            exam = examRepository.save(exam);
            examRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw duplicateExamException(request.title());
        }

        return ExamResponse.from(exam);
    }

    @Transactional
    public ExamResponse update(Long id, ExamUpdateRequest request, User caller) {
        Exam exam = findOrThrow(id);
        scopedWriteAuthorizer.requireScopedWrite(
                caller,
                exam.getSubject().getId(),
                exam.getAcademicYear(),
                exam.getSemester(),
                exam.getSection());

        // A CROSS-TABLE guard the schema cannot express: lowering maximumMarks below an
        // already-recorded mark would silently create rows that violate "marksObtained
        // <= maximumMarks" (the schema only enforces the lower bound). Reject with 409
        // rather than corrupting existing marks data.
        BigDecimal highestRecorded = marksRepository.findHighestMarkForExam(id);
        if (highestRecorded != null && request.maximumMarks().compareTo(highestRecorded) < 0) {
            throw new DuplicateResourceException(
                    "Cannot lower maximum marks to " + request.maximumMarks() + ": the highest mark already "
                            + "recorded for this exam is " + highestRecorded + ".");
        }

        exam.setTitle(request.title());
        exam.setExamType(request.examType());
        exam.setExamDate(request.examDate());
        exam.setMaximumMarks(request.maximumMarks());
        exam.setStatus(request.status());

        return ExamResponse.from(exam);
    }

    @Transactional
    public void delete(Long id, User caller) {
        Exam exam = findOrThrow(id);
        scopedWriteAuthorizer.requireScopedWrite(
                caller,
                exam.getSubject().getId(),
                exam.getAcademicYear(),
                exam.getSemester(),
                exam.getSection());

        long marksCount = marksRepository.countByExamId(id);
        if (marksCount > 0) {
            throw new DuplicateResourceException(
                    "Cannot delete exam " + id + ": " + marksCount + " marks row(s) reference it.");
        }

        try {
            examRepository.delete(exam);
            examRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("Cannot delete exam " + id + ": marks reference it.");
        }
    }

    @Transactional(readOnly = true)
    public ExamResponse getById(Long id) {
        return ExamResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ExamResponse> list(
            Long subjectId,
            String academicYear,
            Integer semester,
            String section,
            ExamType examType,
            ExamStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            String search,
            Pageable pageable) {
        Specification<Exam> spec =
                buildFilter(subjectId, academicYear, semester, section, examType, status, fromDate, toDate, search);
        Page<Exam> page = examRepository.findAll(spec, pageable);
        return PageResponse.of(page, ExamResponse::from);
    }

    /**
     * G4: always {@code status = SCHEDULED} and {@code examDate >= today}, ordered by
     * {@code examDate} ascending. Scope narrows by caller role: STUDENT sees exams for
     * subjects they hold an ACTIVE enrollment in (an empty enrollment set returns an
     * empty list — never "fall back to all exams"); FACULTY sees exams matching one of
     * their assignment tuples exactly, not merely the subject; ADMIN sees everything.
     */
    @Transactional(readOnly = true)
    public List<ExamResponse> upcoming(User caller, int limit) {
        if (caller == null) {
            throw new AccessDeniedException("Authentication required.");
        }
        int cappedLimit = Math.min(Math.max(limit, 1), 50);
        LocalDate today = LocalDate.now();

        if (caller.getRole() == Role.ADMIN) {
            return examRepository
                    .findByStatusAndExamDateGreaterThanEqualOrderByExamDateAscIdAsc(
                            ExamStatus.SCHEDULED, today, Pageable.ofSize(cappedLimit))
                    .stream()
                    .map(ExamResponse::from)
                    .toList();
        }

        if (caller.getRole() == Role.STUDENT) {
            Student student =
                    studentRepository
                            .findByUserId(caller.getId())
                            .orElseThrow(() -> new ResourceNotFoundException("Student profile not found."));
            Set<Long> subjectIds =
                    enrollmentRepository.findByStudentId(student.getId()).stream()
                            .filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE)
                            .map(e -> e.getSubject().getId())
                            .collect(Collectors.toSet());
            if (subjectIds.isEmpty()) {
                return List.of();
            }
            return examRepository
                    .findBySubjectIdInAndStatusAndExamDateGreaterThanEqualOrderByExamDateAscIdAsc(
                            subjectIds, ExamStatus.SCHEDULED, today, Pageable.ofSize(cappedLimit))
                    .stream()
                    .map(ExamResponse::from)
                    .toList();
        }

        if (caller.getRole() == Role.FACULTY) {
            Faculty faculty =
                    facultyRepository
                            .findByUserId(caller.getId())
                            .orElseThrow(() -> new ResourceNotFoundException("Faculty profile not found."));
            List<FacultySubjectAssignment> assignments =
                    facultySubjectAssignmentRepository.findByFacultyId(faculty.getId());
            if (assignments.isEmpty()) {
                return List.of();
            }
            Set<Long> subjectIds =
                    assignments.stream().map(a -> a.getSubject().getId()).collect(Collectors.toSet());
            Set<String> allowedTuples =
                    assignments.stream()
                            .map(
                                    a ->
                                            tupleKey(
                                                    a.getSubject().getId(),
                                                    a.getAcademicYear(),
                                                    a.getSemester(),
                                                    a.getSection()))
                            .collect(Collectors.toSet());
            // Fetch every SCHEDULED, future-dated candidate across the faculty's taught
            // subjects (unpaged — the exact-tuple filter below, not the page window,
            // decides membership), then narrow to the caller's own assignment tuples and
            // cap at the requested limit.
            List<Exam> candidates =
                    examRepository.findBySubjectIdInAndStatusAndExamDateGreaterThanEqualOrderByExamDateAscIdAsc(
                            subjectIds, ExamStatus.SCHEDULED, today, Pageable.unpaged());
            return candidates.stream()
                    .filter(
                            e ->
                                    allowedTuples.contains(
                                            tupleKey(
                                                    e.getSubject().getId(),
                                                    e.getAcademicYear(),
                                                    e.getSemester(),
                                                    e.getSection())))
                    .limit(cappedLimit)
                    .map(ExamResponse::from)
                    .toList();
        }

        throw new AccessDeniedException("Unknown role.");
    }

    private static String tupleKey(Long subjectId, String academicYear, Integer semester, String section) {
        return subjectId + "|" + academicYear + "|" + semester + "|" + section;
    }

    private Exam findOrThrow(Long id) {
        return examRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam not found: " + id));
    }

    private static DuplicateResourceException duplicateExamException(String title) {
        return new DuplicateResourceException(
                "An exam titled \"" + title + "\" already exists for this subject, exam type, "
                        + "academic year, semester and section.");
    }

    private Specification<Exam> buildFilter(
            Long subjectId,
            String academicYear,
            Integer semester,
            String section,
            ExamType examType,
            ExamStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
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
            if (examType != null) {
                predicates.add(cb.equal(root.get("examType"), examType));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("examDate"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("examDate"), toDate));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + search.toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
