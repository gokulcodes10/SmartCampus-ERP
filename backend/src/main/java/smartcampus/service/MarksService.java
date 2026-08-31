package smartcampus.service;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.AcademicResultResponse;
import smartcampus.dto.MarksBulkRequest;
import smartcampus.dto.MarksBulkResponse;
import smartcampus.dto.MarksEntry;
import smartcampus.dto.MarksEntrySheetEntry;
import smartcampus.dto.MarksEntrySheetResponse;
import smartcampus.dto.MarksResponse;
import smartcampus.dto.MarksUpdateRequest;
import smartcampus.dto.PageResponse;
import smartcampus.dto.SemesterGradeSummary;
import smartcampus.dto.SubjectGradeSummary;
import smartcampus.entity.Enrollment;
import smartcampus.entity.EnrollmentStatus;
import smartcampus.entity.Exam;
import smartcampus.entity.Faculty;
import smartcampus.entity.GradeBand;
import smartcampus.entity.Marks;
import smartcampus.entity.Student;
import smartcampus.entity.User;
import smartcampus.exception.BadRequestException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.EnrollmentRepository;
import smartcampus.repository.ExamRepository;
import smartcampus.repository.MarksRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.projection.MarksSubjectTotals;

/**
 * Marks entry (bulk upsert against the {@code (exam_id, student_id)} unique key,
 * exactly like attendance's bulk roster marking) and G7 grade reporting.
 *
 * <p>Every write routes through {@link ScopedWriteAuthorizer#requireScopedWrite} using
 * the tuple of the EXAM the marks belong to (never a tuple restated by the caller), and
 * every batch is fully validated before any row is saved — see {@link
 * #bulkUpsert(MarksBulkRequest, User)}. The upper marks bound ({@code marksObtained <=
 * exam.maximumMarks}) is a cross-table condition the database schema cannot express as a
 * CHECK constraint, so it is enforced here against the exam the mark's {@code examId}
 * actually points at.
 */
@Service
public class MarksService {

    private final MarksRepository marksRepository;
    private final ExamRepository examRepository;
    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ScopedWriteAuthorizer scopedWriteAuthorizer;
    private final GradeCalculationService gradeCalculationService;

    public MarksService(
            MarksRepository marksRepository,
            ExamRepository examRepository,
            StudentRepository studentRepository,
            EnrollmentRepository enrollmentRepository,
            ScopedWriteAuthorizer scopedWriteAuthorizer,
            GradeCalculationService gradeCalculationService) {
        this.marksRepository = marksRepository;
        this.examRepository = examRepository;
        this.studentRepository = studentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.scopedWriteAuthorizer = scopedWriteAuthorizer;
        this.gradeCalculationService = gradeCalculationService;
    }

    /**
     * VALIDATES THE WHOLE BATCH BEFORE SAVING ANY ROW — Phase 2's postmortem documents a
     * brute-force cap that silently never engaged because a save happened before an
     * unchecked throw inside the same {@code @Transactional} method, and Spring's
     * default rollback-on-unchecked rule discarded the save along with the throw. Here
     * every entry is checked (duplicate studentId within the request, marks within
     * bounds, an ACTIVE enrollment exists) before the first {@code save} call, so a
     * rejected batch never needs {@code noRollbackFor} — nothing was ever written.
     */
    @Transactional
    public MarksBulkResponse bulkUpsert(MarksBulkRequest request, User caller) {
        Exam exam =
                examRepository
                        .findById(request.examId())
                        .orElseThrow(() -> new ResourceNotFoundException("Exam not found: " + request.examId()));

        scopedWriteAuthorizer.requireScopedWrite(
                caller,
                exam.getSubject().getId(),
                exam.getAcademicYear(),
                exam.getSemester(),
                exam.getSection());

        List<MarksEntry> entries = request.entries();

        Set<Long> seenStudentIds = new HashSet<>();
        for (MarksEntry entry : entries) {
            if (!seenStudentIds.add(entry.studentId())) {
                throw new BadRequestException(
                        "Duplicate studentId " + entry.studentId() + " in the same marks bulk request.");
            }
        }

        Map<Long, Student> activeEnrolledStudents =
                enrollmentRepository
                        .findBySubjectIdAndAcademicYearAndSemesterAndSection(
                                exam.getSubject().getId(),
                                exam.getAcademicYear(),
                                exam.getSemester(),
                                exam.getSection())
                        .stream()
                        .filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE)
                        .collect(
                                Collectors.toMap(
                                        e -> e.getStudent().getId(), Enrollment::getStudent, (a, b) -> a));

        for (MarksEntry entry : entries) {
            if (entry.marksObtained().compareTo(exam.getMaximumMarks()) > 0) {
                throw new BadRequestException(
                        "Marks obtained (" + entry.marksObtained() + ") for student " + entry.studentId()
                                + " exceeds this exam's maximum marks (" + exam.getMaximumMarks() + ").");
            }
            if (!activeEnrolledStudents.containsKey(entry.studentId())) {
                throw new BadRequestException(
                        "Student " + entry.studentId() + " has no ACTIVE enrollment in "
                                + exam.getSubject().getCode() + " for " + exam.getAcademicYear() + " semester "
                                + exam.getSemester() + " section " + exam.getSection() + ".");
            }
        }

        Faculty enteredBy = scopedWriteAuthorizer.facultyOrNull(caller);

        int createdCount = 0;
        int updatedCount = 0;
        List<Marks> savedRows = new ArrayList<>();
        for (MarksEntry entry : entries) {
            Optional<Marks> existing =
                    marksRepository.findByExamIdAndStudentId(exam.getId(), entry.studentId());
            Marks marks;
            if (existing.isPresent()) {
                marks = existing.get();
                marks.setMarksObtained(entry.marksObtained());
                marks.setRemarks(entry.remarks());
                marks.setEnteredByFaculty(enteredBy);
                updatedCount++;
            } else {
                marks =
                        Marks.builder()
                                .exam(exam)
                                .student(activeEnrolledStudents.get(entry.studentId()))
                                .marksObtained(entry.marksObtained())
                                .remarks(entry.remarks())
                                .enteredByFaculty(enteredBy)
                                .build();
                createdCount++;
            }
            savedRows.add(marksRepository.save(marks));
        }
        marksRepository.flush();

        List<MarksResponse> records = savedRows.stream().map(this::toResponse).toList();
        return new MarksBulkResponse(
                exam.getId(), exam.getTitle(), exam.getMaximumMarks(), createdCount, updatedCount, records);
    }

    @Transactional(readOnly = true)
    public MarksEntrySheetResponse entrySheet(Long examId, User caller) {
        Exam exam = findExamOrThrow(examId);
        scopedWriteAuthorizer.requireScopedWrite(
                caller,
                exam.getSubject().getId(),
                exam.getAcademicYear(),
                exam.getSemester(),
                exam.getSection());

        List<Enrollment> activeEnrollments =
                enrollmentRepository
                        .findBySubjectIdAndAcademicYearAndSemesterAndSection(
                                exam.getSubject().getId(),
                                exam.getAcademicYear(),
                                exam.getSemester(),
                                exam.getSection())
                        .stream()
                        .filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE)
                        .sorted(
                                Comparator.comparing(
                                        e -> e.getStudent().getRegisterNumber(),
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList();

        Map<Long, Marks> marksByStudentId =
                marksRepository.findByExamIdOrderByStudentId(examId).stream()
                        .collect(Collectors.toMap(m -> m.getStudent().getId(), m -> m, (a, b) -> a, HashMap::new));

        List<MarksEntrySheetEntry> entries =
                activeEnrollments.stream()
                        .map(
                                e -> {
                                    Student student = e.getStudent();
                                    Marks marks = marksByStudentId.get(student.getId());
                                    return new MarksEntrySheetEntry(
                                            student.getId(),
                                            student.getRegisterNumber(),
                                            student.getUser().getFullName(),
                                            marks != null ? marks.getId() : null,
                                            marks != null ? marks.getMarksObtained() : null,
                                            marks != null ? marks.getRemarks() : null);
                                })
                        .toList();

        int enteredCount = (int) entries.stream().filter(e -> e.marksId() != null).count();

        return new MarksEntrySheetResponse(
                exam.getId(),
                exam.getTitle(),
                exam.getExamType(),
                exam.getSubject().getId(),
                exam.getSubject().getCode(),
                exam.getSubject().getName(),
                exam.getAcademicYear(),
                exam.getSemester(),
                exam.getSection(),
                exam.getExamDate(),
                exam.getMaximumMarks(),
                entries.size(),
                enteredCount,
                entries);
    }

    @Transactional(readOnly = true)
    public List<MarksResponse> listByExam(Long examId, User caller) {
        Exam exam = findExamOrThrow(examId);
        scopedWriteAuthorizer.requireScopedWrite(
                caller,
                exam.getSubject().getId(),
                exam.getAcademicYear(),
                exam.getSemester(),
                exam.getSection());
        return marksRepository.findByExamIdOrderByStudentId(examId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public MarksResponse update(Long id, MarksUpdateRequest request, User caller) {
        Marks marks = findMarksOrThrow(id);
        Exam exam = marks.getExam();
        scopedWriteAuthorizer.requireScopedWrite(
                caller,
                exam.getSubject().getId(),
                exam.getAcademicYear(),
                exam.getSemester(),
                exam.getSection());

        if (request.marksObtained().compareTo(exam.getMaximumMarks()) > 0) {
            throw new BadRequestException(
                    "Marks obtained (" + request.marksObtained() + ") exceeds this exam's maximum marks ("
                            + exam.getMaximumMarks() + ").");
        }

        marks.setMarksObtained(request.marksObtained());
        marks.setRemarks(request.remarks());
        return toResponse(marks);
    }

    @Transactional(readOnly = true)
    public PageResponse<MarksResponse> myMarks(
            User caller,
            String academicYear,
            Integer semester,
            Long subjectId,
            Long examId,
            Pageable pageable) {
        Student student = scopedWriteAuthorizer.requireOwnStudent(caller);
        Specification<Marks> spec = buildMineFilter(student.getId(), academicYear, semester, subjectId, examId);
        Page<Marks> page = marksRepository.findAll(spec, pageable);
        return PageResponse.of(page, this::toResponse);
    }

    @Transactional(readOnly = true)
    public AcademicResultResponse mySummary(User caller, String academicYear, Integer semester) {
        Student student = scopedWriteAuthorizer.requireOwnStudent(caller);
        return buildResult(student, academicYear, semester);
    }

    @Transactional(readOnly = true)
    public AcademicResultResponse summaryForStudent(
            Long studentId, String academicYear, Integer semester, User caller) {
        scopedWriteAuthorizer.requireAdmin(caller);
        Student student =
                studentRepository
                        .findById(studentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + studentId));
        return buildResult(student, academicYear, semester);
    }

    // ------------------------------------------------------------------
    // G7 grade computation
    // ------------------------------------------------------------------

    private AcademicResultResponse buildResult(Student student, String academicYear, Integer semester) {
        List<MarksSubjectTotals> totals =
                marksRepository.summarizeByStudent(student.getId(), academicYear, semester);

        // Query already orders by academicYear asc, semester asc, subject code asc — a
        // LinkedHashMap preserves that grouping order so buckets never bleed across each
        // other or come back scrambled.
        Map<String, List<MarksSubjectTotals>> grouped =
                totals.stream()
                        .collect(
                                Collectors.groupingBy(
                                        t -> t.getAcademicYear() + "|" + t.getSemester(),
                                        LinkedHashMap::new,
                                        Collectors.toList()));

        List<SemesterGradeSummary> semesters = new ArrayList<>();
        for (List<MarksSubjectTotals> subjectTotals : grouped.values()) {
            List<SubjectGradeSummary> subjects =
                    subjectTotals.stream().map(this::toSubjectGradeSummary).toList();
            int gradedCredits =
                    subjects.stream()
                            .filter(s -> s.gradePoint() != null)
                            .mapToInt(SubjectGradeSummary::credits)
                            .sum();
            BigDecimal gpa = gradeCalculationService.creditWeightedGpa(subjects);
            semesters.add(
                    new SemesterGradeSummary(
                            subjectTotals.get(0).getAcademicYear(),
                            subjectTotals.get(0).getSemester(),
                            subjects.size(),
                            gradedCredits,
                            gpa,
                            subjects));
        }

        List<SubjectGradeSummary> allSubjects =
                semesters.stream().flatMap(s -> s.subjects().stream()).toList();
        int totalGradedCredits =
                allSubjects.stream()
                        .filter(s -> s.gradePoint() != null)
                        .mapToInt(SubjectGradeSummary::credits)
                        .sum();
        BigDecimal cgpa = gradeCalculationService.creditWeightedGpa(allSubjects);

        return new AcademicResultResponse(
                student.getId(),
                student.getRegisterNumber(),
                student.getUser().getFullName(),
                totalGradedCredits,
                cgpa,
                semesters);
    }

    private SubjectGradeSummary toSubjectGradeSummary(MarksSubjectTotals totals) {
        BigDecimal percentage =
                gradeCalculationService.percentage(totals.getTotalObtained(), totals.getTotalMaximum());
        Optional<GradeBand> band = gradeCalculationService.bandFor(percentage);
        return new SubjectGradeSummary(
                totals.getSubjectId(),
                totals.getSubjectCode(),
                totals.getSubjectName(),
                totals.getCredits(),
                totals.getAcademicYear(),
                totals.getSemester(),
                totals.getExamCount(),
                totals.getTotalObtained(),
                totals.getTotalMaximum(),
                percentage,
                band.map(GradeBand::getGrade).orElse(null),
                band.map(GradeBand::getGradePoint).orElse(null),
                band.map(GradeBand::isPassGrade).orElse(null));
    }

    // ------------------------------------------------------------------
    // Mapping / lookups
    // ------------------------------------------------------------------

    private MarksResponse toResponse(Marks marks) {
        Exam exam = marks.getExam();
        Student student = marks.getStudent();
        BigDecimal percentage = gradeCalculationService.percentage(marks.getMarksObtained(), exam.getMaximumMarks());
        Optional<GradeBand> band = gradeCalculationService.bandFor(percentage);
        Faculty enteredBy = marks.getEnteredByFaculty();
        return new MarksResponse(
                marks.getId(),
                exam.getId(),
                exam.getTitle(),
                exam.getExamType(),
                exam.getExamDate(),
                exam.getSubject().getId(),
                exam.getSubject().getCode(),
                exam.getSubject().getName(),
                exam.getSubject().getCredits(),
                exam.getAcademicYear(),
                exam.getSemester(),
                exam.getSection(),
                student.getId(),
                student.getRegisterNumber(),
                student.getUser().getFullName(),
                marks.getMarksObtained(),
                exam.getMaximumMarks(),
                percentage,
                band.map(GradeBand::getGrade).orElse(null),
                band.map(GradeBand::getGradePoint).orElse(null),
                marks.getRemarks(),
                enteredBy != null ? enteredBy.getId() : null,
                enteredBy != null ? enteredBy.getUser().getFullName() : null,
                marks.getCreatedAt(),
                marks.getUpdatedAt());
    }

    private Exam findExamOrThrow(Long id) {
        return examRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam not found: " + id));
    }

    private Marks findMarksOrThrow(Long id) {
        return marksRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Marks not found: " + id));
    }

    private Specification<Marks> buildMineFilter(
            Long studentId, String academicYear, Integer semester, Long subjectId, Long examId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("student").get("id"), studentId));
            if (academicYear != null && !academicYear.isBlank()) {
                predicates.add(cb.equal(root.get("exam").get("academicYear"), academicYear));
            }
            if (semester != null) {
                predicates.add(cb.equal(root.get("exam").get("semester"), semester));
            }
            if (subjectId != null) {
                predicates.add(cb.equal(root.get("exam").get("subject").get("id"), subjectId));
            }
            if (examId != null) {
                predicates.add(cb.equal(root.get("exam").get("id"), examId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
