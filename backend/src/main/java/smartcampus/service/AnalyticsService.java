package smartcampus.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.AcademicResultResponse;
import smartcampus.dto.AnalyticsAdminResponse;
import smartcampus.dto.AnalyticsClassResponse;
import smartcampus.dto.AnalyticsFilterOptionsResponse;
import smartcampus.dto.AnalyticsStudentResponse;
import smartcampus.dto.AttendanceSubjectSummary;
import smartcampus.dto.AttendanceSummaryResponse;
import smartcampus.dto.AttendanceTrendPoint;
import smartcampus.dto.ClassificationSlice;
import smartcampus.dto.CohortStudentRow;
import smartcampus.dto.DepartmentPerformanceRow;
import smartcampus.dto.ExamAveragePoint;
import smartcampus.dto.FilterCourseOption;
import smartcampus.dto.FilterSubjectOption;
import smartcampus.dto.GradeDistributionSlice;
import smartcampus.dto.MarksTrendPoint;
import smartcampus.dto.PerformanceClassificationResponse;
import smartcampus.dto.SemesterGpaPoint;
import smartcampus.dto.SemesterGradeSummary;
import smartcampus.dto.SemesterPerformancePoint;
import smartcampus.dto.SubjectAveragePoint;
import smartcampus.dto.SubjectGradeSummary;
import smartcampus.dto.SubjectPerformanceRow;
import smartcampus.dto.TeachingClassResponse;
import smartcampus.entity.Course;
import smartcampus.entity.Department;
import smartcampus.entity.ExamStatus;
import smartcampus.entity.FacultyStatus;
import smartcampus.entity.GradeBand;
import smartcampus.entity.PerformanceBand;
import smartcampus.entity.PerformanceCategory;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.Subject;
import smartcampus.entity.User;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.AnalyticsAttendanceRepository;
import smartcampus.repository.AnalyticsCohortRepository;
import smartcampus.repository.AnalyticsMarksRepository;
import smartcampus.repository.CourseRepository;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.GradeBandRepository;
import smartcampus.repository.PerformanceBandRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.SubjectRepository;
import smartcampus.repository.projection.AttendanceTrendTotals;
import smartcampus.repository.projection.ExamMarksTotals;
import smartcampus.repository.projection.MarksTrendTotals;
import smartcampus.repository.projection.StudentSubjectAttendanceTotals;
import smartcampus.repository.projection.StudentSubjectMarksTotals;

/**
 * The Phase 5 analytics aggregation layer — every figure on every student, class and
 * admin dashboard is composed here from real database aggregates (never a literal),
 * reusing {@link AttendanceService}/{@link MarksService}/{@link
 * GradeCalculationService} rather than re-implementing G6/G7 arithmetic, and gated by
 * {@link AnalyticsScopeResolver} — the phase's security boundary — before any cohort
 * query runs.
 *
 * <p>Every read method is {@code @Transactional(readOnly = true)}: the reused Phase 4
 * response DTOs and the projection interfaces here both touch LAZY associations, and
 * {@code spring.jpa.open-in-view=false} means those associations are only reachable
 * inside the owning transaction.
 */
@Service
public class AnalyticsService {

    private final AttendanceService attendanceService;
    private final MarksService marksService;
    private final GradeCalculationService gradeCalculationService;
    private final GradeBandRepository gradeBandRepository;
    private final PerformanceBandRepository performanceBandRepository;
    private final PerformanceClassifier performanceClassifier;
    private final AnalyticsAttendanceRepository analyticsAttendanceRepository;
    private final AnalyticsMarksRepository analyticsMarksRepository;
    private final AnalyticsCohortRepository analyticsCohortRepository;
    private final AnalyticsScopeResolver analyticsScopeResolver;
    private final ScopedWriteAuthorizer scopedWriteAuthorizer;
    private final TeachingService teachingService;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final SubjectRepository subjectRepository;
    private final DepartmentRepository departmentRepository;

    /**
     * The default trend window (months), when the caller omits {@code months} or sends
     * an out-of-range value. The inline default is mandatory: {@code
     * src/test/resources/application.properties} SHADOWS the main configuration file
     * rather than merging with it, so a property defined only in {@code src/main} would
     * be invisible to the test context and boot would fail there.
     */
    @Value("${smartcampus.analytics.trend-months:6}")
    private int defaultTrendMonths;

    /** The largest trend window a caller may request; larger values are clamped down to {@link #defaultTrendMonths}. */
    @Value("${smartcampus.analytics.max-trend-months:24}")
    private int maxTrendMonths;

    /** How many {@code AT_RISK} students the overview's {@code atRiskStudents} list carries at most. */
    @Value("${smartcampus.analytics.at-risk-list-limit:20}")
    private int atRiskListLimit;

    public AnalyticsService(
            AttendanceService attendanceService,
            MarksService marksService,
            GradeCalculationService gradeCalculationService,
            GradeBandRepository gradeBandRepository,
            PerformanceBandRepository performanceBandRepository,
            PerformanceClassifier performanceClassifier,
            AnalyticsAttendanceRepository analyticsAttendanceRepository,
            AnalyticsMarksRepository analyticsMarksRepository,
            AnalyticsCohortRepository analyticsCohortRepository,
            AnalyticsScopeResolver analyticsScopeResolver,
            ScopedWriteAuthorizer scopedWriteAuthorizer,
            TeachingService teachingService,
            StudentRepository studentRepository,
            CourseRepository courseRepository,
            SubjectRepository subjectRepository,
            DepartmentRepository departmentRepository) {
        this.attendanceService = attendanceService;
        this.marksService = marksService;
        this.gradeCalculationService = gradeCalculationService;
        this.gradeBandRepository = gradeBandRepository;
        this.performanceBandRepository = performanceBandRepository;
        this.performanceClassifier = performanceClassifier;
        this.analyticsAttendanceRepository = analyticsAttendanceRepository;
        this.analyticsMarksRepository = analyticsMarksRepository;
        this.analyticsCohortRepository = analyticsCohortRepository;
        this.analyticsScopeResolver = analyticsScopeResolver;
        this.scopedWriteAuthorizer = scopedWriteAuthorizer;
        this.teachingService = teachingService;
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.subjectRepository = subjectRepository;
        this.departmentRepository = departmentRepository;
    }

    // ------------------------------------------------------------------
    // Student dashboards
    // ------------------------------------------------------------------

    /**
     * {@code GET /api/analytics/me} — STUDENT, own record only. A {@code studentId}
     * query parameter is never honoured on this path; identity comes only from {@code
     * caller} via {@link ScopedWriteAuthorizer#requireOwnStudent}.
     */
    @Transactional(readOnly = true)
    public AnalyticsStudentResponse myAnalytics(User caller, String academicYear, Integer semester, Integer months) {
        Student student = scopedWriteAuthorizer.requireOwnStudent(caller);
        AttendanceSummaryResponse attendance = attendanceService.mySummary(caller, academicYear, semester);
        AcademicResultResponse academics = marksService.mySummary(caller, academicYear, semester);
        return assembleStudentResponse(student, academicYear, semester, months, attendance, academics);
    }

    /** {@code GET /api/analytics/students/{studentId}} — ADMIN only. */
    @Transactional(readOnly = true)
    public AnalyticsStudentResponse studentAnalytics(
            Long studentId, User caller, String academicYear, Integer semester, Integer months) {
        scopedWriteAuthorizer.requireAdmin(caller);
        Student student =
                studentRepository
                        .findById(studentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + studentId));
        AttendanceSummaryResponse attendance = attendanceService.adminSummary(studentId, academicYear, semester, caller);
        AcademicResultResponse academics = marksService.summaryForStudent(studentId, academicYear, semester, caller);
        return assembleStudentResponse(student, academicYear, semester, months, attendance, academics);
    }

    private AnalyticsStudentResponse assembleStudentResponse(
            Student student,
            String academicYear,
            Integer semester,
            Integer months,
            AttendanceSummaryResponse attendance,
            AcademicResultResponse academics) {
        int trendMonths = resolveMonths(months);
        LocalDate fromDate = LocalDate.now().withDayOfMonth(1).minusMonths(trendMonths - 1L);

        BigDecimal attendancePercentage = attendance.overallPercentage();

        BigDecimal totalObtained = null;
        BigDecimal totalMaximum = null;
        for (SemesterGradeSummary sem : academics.semesters()) {
            for (SubjectGradeSummary subj : sem.subjects()) {
                totalObtained = addNullable(totalObtained, subj.totalObtained());
                totalMaximum = addNullable(totalMaximum, subj.totalMaximum());
            }
        }
        BigDecimal marksPercentage = gradeCalculationService.percentage(totalObtained, totalMaximum);

        BigDecimal cgpa = academics.cgpa();
        BigDecimal gpa =
                academics.semesters().isEmpty()
                        ? null
                        : academics.semesters().get(academics.semesters().size() - 1).gpa();

        PerformanceClassificationResponse classification =
                performanceClassifier.classify(marksPercentage, attendancePercentage, cgpa);

        List<SubjectPerformanceRow> subjects = mergeSubjectRows(attendance.subjects(), academics.semesters());

        List<SemesterGpaPoint> gpaTrend =
                academics.semesters().stream()
                        .map(s -> new SemesterGpaPoint(s.academicYear(), s.semester(), s.subjectCount(), s.gradedCredits(), s.gpa()))
                        .toList();

        List<GradeBand> gradeBands = gradeBandRepository.findAllByOrderByMinPercentageDesc();
        Map<String, Long> gradeCounts =
                subjects.stream()
                        .filter(s -> s.grade() != null)
                        .collect(Collectors.groupingBy(SubjectPerformanceRow::grade, Collectors.counting()));
        List<GradeDistributionSlice> gradeDistribution =
                gradeBands.stream()
                        .map(
                                b ->
                                        new GradeDistributionSlice(
                                                b.getGrade(),
                                                b.getGradePoint(),
                                                b.getMinPercentage(),
                                                b.getMaxPercentage(),
                                                gradeCounts.getOrDefault(b.getGrade(), 0L)))
                        .toList();

        List<AttendanceTrendPoint> attendanceTrend =
                analyticsAttendanceRepository.trendByStudent(student.getId(), fromDate, academicYear, semester).stream()
                        .map(this::toAttendanceTrendPoint)
                        .toList();
        List<MarksTrendPoint> marksTrend =
                analyticsMarksRepository.trendByStudent(student.getId(), fromDate, academicYear, semester).stream()
                        .map(this::toMarksTrendPoint)
                        .toList();

        Department department = student.getDepartment();
        Course course = student.getCourse();

        return new AnalyticsStudentResponse(
                student.getId(),
                student.getRegisterNumber(),
                student.getUser().getFullName(),
                department != null ? department.getId() : null,
                department != null ? department.getName() : null,
                course != null ? course.getId() : null,
                course != null ? course.getName() : null,
                student.getCurrentSemester(),
                student.getSection(),
                academicYear,
                semester,
                trendMonths,
                attendance,
                academics,
                marksPercentage,
                attendancePercentage,
                gpa,
                cgpa,
                classification,
                attendanceTrend,
                marksTrend,
                gpaTrend,
                gradeDistribution,
                subjects);
    }

    /**
     * Full outer merge of a student's {@link AttendanceSubjectSummary} rows and the
     * flattened {@link SubjectGradeSummary} rows from every {@link SemesterGradeSummary},
     * keyed on (subjectId, academicYear, semester). A subject present on only one side
     * still appears, with nulls on the missing side.
     */
    private List<SubjectPerformanceRow> mergeSubjectRows(
            List<AttendanceSubjectSummary> attendanceSubjects, List<SemesterGradeSummary> semesters) {
        Map<String, AttendanceSubjectSummary> attByKey = new LinkedHashMap<>();
        for (AttendanceSubjectSummary a : attendanceSubjects) {
            attByKey.put(subjectKey(a.subjectId(), a.academicYear(), a.semester()), a);
        }
        Map<String, SubjectGradeSummary> marksByKey = new LinkedHashMap<>();
        for (SemesterGradeSummary sem : semesters) {
            for (SubjectGradeSummary subj : sem.subjects()) {
                marksByKey.put(subjectKey(subj.subjectId(), subj.academicYear(), subj.semester()), subj);
            }
        }
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(attByKey.keySet());
        allKeys.addAll(marksByKey.keySet());

        List<SubjectPerformanceRow> rows = new ArrayList<>();
        for (String key : allKeys) {
            AttendanceSubjectSummary a = attByKey.get(key);
            SubjectGradeSummary m = marksByKey.get(key);

            Long subjectId = a != null ? a.subjectId() : m.subjectId();
            String subjectCode = a != null ? a.subjectCode() : m.subjectCode();
            String subjectName = a != null ? a.subjectName() : m.subjectName();
            Integer credits = a != null ? a.credits() : m.credits();
            String rowAcademicYear = a != null ? a.academicYear() : m.academicYear();
            Integer rowSemester = a != null ? a.semester() : m.semester();

            Long held = a != null ? a.heldClasses() : null;
            Long attended = a != null ? a.attendedClasses() : null;
            BigDecimal rowAttendancePct = a != null ? a.attendancePercentage() : null;

            Long examCount = m != null ? m.examCount() : null;
            BigDecimal rowTotalObtained = m != null ? m.totalObtained() : null;
            BigDecimal rowTotalMaximum = m != null ? m.totalMaximum() : null;
            BigDecimal rowMarksPct = m != null ? m.percentage() : null;
            String grade = m != null ? m.grade() : null;
            BigDecimal gradePoint = m != null ? m.gradePoint() : null;
            Boolean passed = m != null ? m.passed() : null;

            // A single subject's "GPA" IS its grade point — same 0-10 scale.
            PerformanceClassificationResponse rowClassification =
                    performanceClassifier.classify(rowMarksPct, rowAttendancePct, gradePoint);

            rows.add(
                    new SubjectPerformanceRow(
                            subjectId,
                            subjectCode,
                            subjectName,
                            credits,
                            rowAcademicYear,
                            rowSemester,
                            held,
                            attended,
                            rowAttendancePct,
                            examCount,
                            rowTotalObtained,
                            rowTotalMaximum,
                            rowMarksPct,
                            grade,
                            gradePoint,
                            passed,
                            rowClassification.category(),
                            rowClassification.colorHex()));
        }

        rows.sort(
                Comparator.comparing(SubjectPerformanceRow::academicYear, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SubjectPerformanceRow::semester, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SubjectPerformanceRow::subjectCode, Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    private static String subjectKey(Long subjectId, String academicYear, Integer semester) {
        return subjectId + "|" + academicYear + "|" + semester;
    }

    // ------------------------------------------------------------------
    // Class / overview dashboards
    // ------------------------------------------------------------------

    /** {@code GET /api/analytics/class} — FACULTY (own assignments only) or ADMIN. */
    @Transactional(readOnly = true)
    public AnalyticsClassResponse classAnalytics(
            User caller,
            Long courseId,
            Long subjectId,
            String academicYear,
            Integer semester,
            String section,
            Integer months) {
        AnalyticsScopeResolver.ScopeResolution resolution =
                analyticsScopeResolver.forClass(caller, courseId, subjectId, academicYear, semester, section);
        int trendMonths = resolveMonths(months);

        Course course = courseId != null ? courseRepository.findById(courseId).orElse(null) : null;
        Subject subject = subjectId != null ? subjectRepository.findById(subjectId).orElse(null) : null;

        if (resolution.empty()) {
            return new AnalyticsClassResponse(
                    courseId,
                    course != null ? course.getCode() : null,
                    course != null ? course.getName() : null,
                    subjectId,
                    subject != null ? subject.getCode() : null,
                    subject != null ? subject.getName() : null,
                    academicYear,
                    semester,
                    section,
                    trendMonths,
                    0,
                    0,
                    0,
                    0L,
                    0L,
                    0L,
                    null,
                    0L,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of());
        }

        CohortAssembly assembly = assembleCohort(resolution, academicYear, semester, section, trendMonths);

        List<ExamAveragePoint> examAverages =
                analyticsMarksRepository.examTotals(resolution.subjectIds(), academicYear, semester, section).stream()
                        .map(this::toExamAveragePoint)
                        .toList();

        return new AnalyticsClassResponse(
                courseId,
                course != null ? course.getCode() : null,
                course != null ? course.getName() : null,
                subjectId,
                subject != null ? subject.getCode() : null,
                subject != null ? subject.getName() : null,
                academicYear,
                semester,
                section,
                trendMonths,
                assembly.studentCount(),
                assembly.classifiedCount(),
                assembly.unclassifiedCount(),
                assembly.heldClasses(),
                assembly.attendedClasses(),
                assembly.cancelledClasses(),
                assembly.attendancePercentage(),
                assembly.examCount(),
                assembly.totalObtained(),
                assembly.totalMaximum(),
                assembly.marksPercentage(),
                assembly.averageGpa(),
                assembly.students(),
                assembly.subjectAverages(),
                examAverages,
                assembly.attendanceTrend(),
                assembly.marksTrend(),
                assembly.classificationDistribution(),
                assembly.gradeDistribution());
    }

    /** {@code GET /api/analytics/overview} — ADMIN only. */
    @Transactional(readOnly = true)
    public AnalyticsAdminResponse overview(
            User caller,
            Long departmentId,
            Long courseId,
            String academicYear,
            Integer semester,
            String section,
            Integer months) {
        AnalyticsScopeResolver.ScopeResolution resolution =
                analyticsScopeResolver.forOverview(caller, departmentId, courseId);
        int trendMonths = resolveMonths(months);

        // Unscoped institution totals — independent of departmentId/courseId/etc.
        long totalStudents = analyticsCohortRepository.countStudents();
        long activeStudents = analyticsCohortRepository.countByStatus(StudentStatus.ACTIVE);
        long pendingStudents = analyticsCohortRepository.countByStatus(StudentStatus.PENDING);
        long totalFaculty = analyticsCohortRepository.countFaculty();
        long activeFaculty = analyticsCohortRepository.countFacultyByStatus(FacultyStatus.ACTIVE);
        long totalDepartments = analyticsCohortRepository.countDepartments();
        long totalCourses = analyticsCohortRepository.countCourses();
        long totalSubjects = analyticsCohortRepository.countSubjects();
        long totalExams = analyticsCohortRepository.countExams(ExamStatus.CANCELLED);

        Department department = departmentId != null ? departmentRepository.findById(departmentId).orElse(null) : null;
        Course course = courseId != null ? courseRepository.findById(courseId).orElse(null) : null;

        if (resolution.empty()) {
            return new AnalyticsAdminResponse(
                    departmentId,
                    department != null ? department.getName() : null,
                    courseId,
                    course != null ? course.getName() : null,
                    academicYear,
                    semester,
                    section,
                    trendMonths,
                    totalStudents,
                    activeStudents,
                    pendingStudents,
                    totalFaculty,
                    activeFaculty,
                    totalDepartments,
                    totalCourses,
                    totalSubjects,
                    totalExams,
                    0,
                    0,
                    0,
                    0L,
                    0L,
                    0L,
                    null,
                    0L,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of());
        }

        CohortAssembly assembly = assembleCohort(resolution, academicYear, semester, section, trendMonths);

        List<CohortStudentRow> atRiskStudents =
                assembly.students().stream()
                        .filter(s -> s.classification() == PerformanceCategory.AT_RISK)
                        .sorted(Comparator.comparing(CohortStudentRow::marksPercentage, Comparator.nullsLast(Comparator.naturalOrder())))
                        .limit(atRiskListLimit)
                        .toList();

        return new AnalyticsAdminResponse(
                departmentId,
                department != null ? department.getName() : null,
                courseId,
                course != null ? course.getName() : null,
                academicYear,
                semester,
                section,
                trendMonths,
                totalStudents,
                activeStudents,
                pendingStudents,
                totalFaculty,
                activeFaculty,
                totalDepartments,
                totalCourses,
                totalSubjects,
                totalExams,
                assembly.studentCount(),
                assembly.classifiedCount(),
                assembly.unclassifiedCount(),
                assembly.heldClasses(),
                assembly.attendedClasses(),
                assembly.cancelledClasses(),
                assembly.attendancePercentage(),
                assembly.examCount(),
                assembly.totalObtained(),
                assembly.totalMaximum(),
                assembly.marksPercentage(),
                assembly.averageGpa(),
                assembly.departments(),
                assembly.semesters(),
                assembly.subjectAverages(),
                assembly.attendanceTrend(),
                assembly.marksTrend(),
                assembly.classificationDistribution(),
                assembly.gradeDistribution(),
                atRiskStudents);
    }

    // ------------------------------------------------------------------
    // Shared cohort assembly — classAnalytics and overview share this ONE method.
    // ------------------------------------------------------------------

    private record CohortAssembly(
            int studentCount,
            int classifiedCount,
            int unclassifiedCount,
            Long heldClasses,
            Long attendedClasses,
            Long cancelledClasses,
            BigDecimal attendancePercentage,
            Long examCount,
            BigDecimal totalObtained,
            BigDecimal totalMaximum,
            BigDecimal marksPercentage,
            BigDecimal averageGpa,
            List<CohortStudentRow> students,
            List<SubjectAveragePoint> subjectAverages,
            List<DepartmentPerformanceRow> departments,
            List<SemesterPerformancePoint> semesters,
            List<AttendanceTrendPoint> attendanceTrend,
            List<MarksTrendPoint> marksTrend,
            List<ClassificationSlice> classificationDistribution,
            List<GradeDistributionSlice> gradeDistribution) {}

    /**
     * Fetches both cohort totals sets, drops every row whose own tuple is not in
     * {@code resolution.allowedTupleKeys()} (the single most dangerous mistake this
     * phase warns against — skipping this step lets a faculty assigned to one section
     * read another section's students), then regroups the survivors every way the
     * dashboards need: by student, by subject, by (academicYear, semester) and by
     * department.
     */
    private CohortAssembly assembleCohort(
            AnalyticsScopeResolver.ScopeResolution resolution,
            String academicYear,
            Integer semester,
            String section,
            int trendMonths) {
        LocalDate fromDate = LocalDate.now().withDayOfMonth(1).minusMonths(trendMonths - 1L);
        List<Long> subjectIds = resolution.subjectIds();
        Set<String> allowedTupleKeys = resolution.allowedTupleKeys();

        List<StudentSubjectAttendanceTotals> attRows =
                analyticsAttendanceRepository.cohortSubjectTotals(subjectIds, academicYear, semester, section).stream()
                        .filter(
                                r ->
                                        tupleAllowed(
                                                allowedTupleKeys,
                                                r.getSubjectId(),
                                                r.getAcademicYear(),
                                                r.getSemester(),
                                                r.getSection()))
                        .toList();
        List<StudentSubjectMarksTotals> mkRows =
                analyticsMarksRepository.cohortSubjectTotals(subjectIds, academicYear, semester, section).stream()
                        .filter(
                                r ->
                                        tupleAllowed(
                                                allowedTupleKeys,
                                                r.getSubjectId(),
                                                r.getAcademicYear(),
                                                r.getSemester(),
                                                r.getSection()))
                        .toList();

        // ---- Per-student accumulation (CohortStudentRow, and the basis for the
        // ---- per-department GPA rollup below). ----
        Map<Long, StudentAccumulator> studentMap = new LinkedHashMap<>();
        for (StudentSubjectAttendanceTotals row : attRows) {
            StudentAccumulator acc = studentMap.computeIfAbsent(row.getStudentId(), id -> new StudentAccumulator());
            acc.studentId = row.getStudentId();
            if (acc.registerNumber == null) {
                acc.registerNumber = row.getRegisterNumber();
                acc.studentName = row.getStudentName();
                acc.departmentId = row.getDepartmentId();
                acc.departmentCode = row.getDepartmentCode();
                acc.departmentName = row.getDepartmentName();
                acc.courseId = row.getCourseId();
                acc.courseName = row.getCourseName();
            }
            acc.heldClasses += nz(row.getHeldClasses());
            acc.attendedClasses += nz(row.getAttendedClasses());
            acc.cancelledClasses += nz(row.getCancelledClasses());
        }
        for (StudentSubjectMarksTotals row : mkRows) {
            StudentAccumulator acc = studentMap.computeIfAbsent(row.getStudentId(), id -> new StudentAccumulator());
            acc.studentId = row.getStudentId();
            if (acc.registerNumber == null) {
                acc.registerNumber = row.getRegisterNumber();
                acc.studentName = row.getStudentName();
                acc.departmentId = row.getDepartmentId();
                acc.departmentCode = row.getDepartmentCode();
                acc.departmentName = row.getDepartmentName();
                acc.courseId = row.getCourseId();
                acc.courseName = row.getCourseName();
            }
            acc.subjects.add(toSubjectGradeSummary(row));
        }

        List<CohortStudentRow> students =
                studentMap.values().stream()
                        .map(this::toCohortStudentRow)
                        .sorted(
                                Comparator.comparing(
                                        CohortStudentRow::registerNumber, Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList();

        int studentCount = students.size();
        int unclassifiedCount = (int) students.stream().filter(s -> s.classification() == null).count();
        int classifiedCount = studentCount - unclassifiedCount;

        // ---- Cohort-wide totals (sum across every survivor row, not per student). ----
        long heldClasses = attRows.stream().mapToLong(r -> nz(r.getHeldClasses())).sum();
        long attendedClasses = attRows.stream().mapToLong(r -> nz(r.getAttendedClasses())).sum();
        long cancelledClasses = attRows.stream().mapToLong(r -> nz(r.getCancelledClasses())).sum();
        long examCount = mkRows.stream().mapToLong(r -> nz(r.getExamCount())).sum();
        BigDecimal totalObtained = null;
        BigDecimal totalMaximum = null;
        for (StudentSubjectMarksTotals row : mkRows) {
            totalObtained = addNullable(totalObtained, row.getTotalObtained());
            totalMaximum = addNullable(totalMaximum, row.getTotalMaximum());
        }
        BigDecimal attendancePercentage = pctOf(attendedClasses, heldClasses);
        BigDecimal marksPercentage = gradeCalculationService.percentage(totalObtained, totalMaximum);
        BigDecimal averageGpa = meanOf(students.stream().map(CohortStudentRow::gpa).filter(Objects::nonNull).toList());

        // ---- Per-subject accumulation (subjectAverages). ----
        Map<Long, SubjectAccumulator> subjectMap = new LinkedHashMap<>();
        for (StudentSubjectAttendanceTotals row : attRows) {
            SubjectAccumulator acc = subjectMap.computeIfAbsent(row.getSubjectId(), id -> new SubjectAccumulator());
            acc.subjectId = row.getSubjectId();
            if (acc.subjectCode == null) {
                acc.subjectCode = row.getSubjectCode();
                acc.subjectName = row.getSubjectName();
                acc.credits = row.getCredits();
            }
            acc.studentIds.add(row.getStudentId());
            acc.heldClasses += nz(row.getHeldClasses());
            acc.attendedClasses += nz(row.getAttendedClasses());
        }
        for (StudentSubjectMarksTotals row : mkRows) {
            SubjectAccumulator acc = subjectMap.computeIfAbsent(row.getSubjectId(), id -> new SubjectAccumulator());
            acc.subjectId = row.getSubjectId();
            if (acc.subjectCode == null) {
                acc.subjectCode = row.getSubjectCode();
                acc.subjectName = row.getSubjectName();
                acc.credits = row.getCredits();
            }
            acc.studentIds.add(row.getStudentId());
            acc.examCount += nz(row.getExamCount());
            acc.totalObtained = addNullable(acc.totalObtained, row.getTotalObtained());
            acc.totalMaximum = addNullable(acc.totalMaximum, row.getTotalMaximum());
        }
        List<SubjectAveragePoint> subjectAverages =
                subjectMap.values().stream()
                        .map(
                                acc ->
                                        new SubjectAveragePoint(
                                                acc.subjectId,
                                                acc.subjectCode,
                                                acc.subjectName,
                                                acc.credits,
                                                (long) acc.studentIds.size(),
                                                acc.heldClasses,
                                                acc.attendedClasses,
                                                pctOf(acc.attendedClasses, acc.heldClasses),
                                                acc.examCount,
                                                acc.totalObtained,
                                                acc.totalMaximum,
                                                gradeCalculationService.percentage(acc.totalObtained, acc.totalMaximum)))
                        .sorted(Comparator.comparing(SubjectAveragePoint::subjectCode, Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList();

        // ---- Per (academicYear, semester) accumulation (semesters — overview only). ----
        Map<String, SemesterAccumulator> semesterMap = new LinkedHashMap<>();
        for (StudentSubjectAttendanceTotals row : attRows) {
            String key = row.getAcademicYear() + "|" + row.getSemester();
            SemesterAccumulator acc =
                    semesterMap.computeIfAbsent(key, k -> new SemesterAccumulator(row.getAcademicYear(), row.getSemester()));
            acc.studentIds.add(row.getStudentId());
            acc.heldClasses += nz(row.getHeldClasses());
            acc.attendedClasses += nz(row.getAttendedClasses());
        }
        Map<String, List<SubjectGradeSummary>> perStudentSemesterSubjects = new LinkedHashMap<>();
        for (StudentSubjectMarksTotals row : mkRows) {
            String semesterKey = row.getAcademicYear() + "|" + row.getSemester();
            SemesterAccumulator acc =
                    semesterMap.computeIfAbsent(semesterKey, k -> new SemesterAccumulator(row.getAcademicYear(), row.getSemester()));
            acc.studentIds.add(row.getStudentId());
            acc.totalObtained = addNullable(acc.totalObtained, row.getTotalObtained());
            acc.totalMaximum = addNullable(acc.totalMaximum, row.getTotalMaximum());

            String studentSemesterKey = row.getStudentId() + "|" + semesterKey;
            perStudentSemesterSubjects
                    .computeIfAbsent(studentSemesterKey, k -> new ArrayList<>())
                    .add(toSubjectGradeSummary(row));
        }
        for (Map.Entry<String, List<SubjectGradeSummary>> entry : perStudentSemesterSubjects.entrySet()) {
            String semesterKey = entry.getKey().substring(entry.getKey().indexOf('|') + 1);
            SemesterAccumulator acc = semesterMap.get(semesterKey);
            BigDecimal gpa = gradeCalculationService.creditWeightedGpa(entry.getValue());
            if (acc != null && gpa != null) {
                acc.gpas.add(gpa);
            }
        }
        List<SemesterPerformancePoint> semesters =
                semesterMap.values().stream()
                        .map(
                                acc ->
                                        new SemesterPerformancePoint(
                                                acc.academicYear,
                                                acc.semester,
                                                (long) acc.studentIds.size(),
                                                pctOf(acc.attendedClasses, acc.heldClasses),
                                                gradeCalculationService.percentage(acc.totalObtained, acc.totalMaximum),
                                                meanOf(acc.gpas)))
                        .sorted(
                                Comparator.comparing(
                                                SemesterPerformancePoint::academicYear, Comparator.nullsLast(Comparator.naturalOrder()))
                                        .thenComparing(
                                                SemesterPerformancePoint::semester, Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList();

        // ---- Per-department accumulation (departments — overview only), built from
        // ---- each student's OVERALL cohort-scoped GPA and attendance. ----
        Map<Long, DepartmentAccumulator> departmentMap = new LinkedHashMap<>();
        for (StudentAccumulator acc : studentMap.values()) {
            if (acc.departmentId == null) {
                continue;
            }
            DepartmentAccumulator dept =
                    departmentMap.computeIfAbsent(
                            acc.departmentId, id -> new DepartmentAccumulator(acc.departmentId, acc.departmentCode, acc.departmentName));
            dept.studentIds.add(acc.studentId);
            dept.heldClasses += acc.heldClasses;
            dept.attendedClasses += acc.attendedClasses;
            BigDecimal studentTotalObtained = null;
            BigDecimal studentTotalMaximum = null;
            for (SubjectGradeSummary s : acc.subjects) {
                studentTotalObtained = addNullable(studentTotalObtained, s.totalObtained());
                studentTotalMaximum = addNullable(studentTotalMaximum, s.totalMaximum());
            }
            dept.totalObtained = addNullable(dept.totalObtained, studentTotalObtained);
            dept.totalMaximum = addNullable(dept.totalMaximum, studentTotalMaximum);
            BigDecimal gpa = gradeCalculationService.creditWeightedGpa(acc.subjects);
            if (gpa != null) {
                dept.gpas.add(gpa);
            }
        }
        List<DepartmentPerformanceRow> departments =
                departmentMap.values().stream()
                        .map(
                                d ->
                                        new DepartmentPerformanceRow(
                                                d.departmentId,
                                                d.departmentCode,
                                                d.departmentName,
                                                (long) d.studentIds.size(),
                                                pctOf(d.attendedClasses, d.heldClasses),
                                                gradeCalculationService.percentage(d.totalObtained, d.totalMaximum),
                                                meanOf(d.gpas)))
                        .sorted(Comparator.comparing(DepartmentPerformanceRow::departmentCode, Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList();

        // ---- Classification / grade distributions, DB-driven categories and bands. ----
        List<PerformanceBand> bands = performanceBandRepository.findAllByOrderByDisplayOrderAsc();
        Map<PerformanceCategory, Long> classificationCounts =
                students.stream()
                        .filter(s -> s.classification() != null)
                        .collect(Collectors.groupingBy(CohortStudentRow::classification, Collectors.counting()));
        List<ClassificationSlice> classificationDistribution =
                bands.stream()
                        .map(
                                band -> {
                                    long count = classificationCounts.getOrDefault(band.getCategory(), 0L);
                                    BigDecimal share =
                                            gradeCalculationService.percentage(
                                                    BigDecimal.valueOf(count), BigDecimal.valueOf(classifiedCount));
                                    return new ClassificationSlice(
                                            band.getCategory(), band.getColorHex(), band.getDescription(), count, share);
                                })
                        .toList();

        List<GradeBand> gradeBands = gradeBandRepository.findAllByOrderByMinPercentageDesc();
        Map<String, Long> gradeCounts = new LinkedHashMap<>();
        for (StudentAccumulator acc : studentMap.values()) {
            for (SubjectGradeSummary s : acc.subjects) {
                if (s.grade() != null) {
                    gradeCounts.merge(s.grade(), 1L, Long::sum);
                }
            }
        }
        List<GradeDistributionSlice> gradeDistribution =
                gradeBands.stream()
                        .map(
                                b ->
                                        new GradeDistributionSlice(
                                                b.getGrade(),
                                                b.getGradePoint(),
                                                b.getMinPercentage(),
                                                b.getMaxPercentage(),
                                                gradeCounts.getOrDefault(b.getGrade(), 0L)))
                        .toList();

        // ---- Trend lines across the same subject scope. ----
        List<AttendanceTrendPoint> attendanceTrend =
                analyticsAttendanceRepository.trendByScope(subjectIds, fromDate, academicYear, semester, section).stream()
                        .map(this::toAttendanceTrendPoint)
                        .toList();
        List<MarksTrendPoint> marksTrend =
                analyticsMarksRepository.trendByScope(subjectIds, fromDate, academicYear, semester, section).stream()
                        .map(this::toMarksTrendPoint)
                        .toList();

        return new CohortAssembly(
                studentCount,
                classifiedCount,
                unclassifiedCount,
                heldClasses,
                attendedClasses,
                cancelledClasses,
                attendancePercentage,
                examCount,
                totalObtained,
                totalMaximum,
                marksPercentage,
                averageGpa,
                students,
                subjectAverages,
                departments,
                semesters,
                attendanceTrend,
                marksTrend,
                classificationDistribution,
                gradeDistribution);
    }

    private CohortStudentRow toCohortStudentRow(StudentAccumulator acc) {
        BigDecimal totalObtained = null;
        BigDecimal totalMaximum = null;
        long examCount = 0;
        for (SubjectGradeSummary s : acc.subjects) {
            totalObtained = addNullable(totalObtained, s.totalObtained());
            totalMaximum = addNullable(totalMaximum, s.totalMaximum());
            examCount += s.examCount();
        }
        BigDecimal marksPercentage = gradeCalculationService.percentage(totalObtained, totalMaximum);
        BigDecimal attendancePercentage = pctOf(acc.attendedClasses, acc.heldClasses);
        BigDecimal gpa = gradeCalculationService.creditWeightedGpa(acc.subjects);
        int gradedCredits =
                acc.subjects.stream().filter(s -> s.gradePoint() != null).mapToInt(SubjectGradeSummary::credits).sum();
        PerformanceClassificationResponse classification =
                performanceClassifier.classify(marksPercentage, attendancePercentage, gpa);

        return new CohortStudentRow(
                acc.studentId,
                acc.registerNumber,
                acc.studentName,
                acc.departmentId,
                acc.departmentName,
                acc.courseId,
                acc.courseName,
                acc.heldClasses,
                acc.attendedClasses,
                attendancePercentage,
                examCount,
                totalObtained,
                totalMaximum,
                marksPercentage,
                gradedCredits,
                gpa,
                classification.category(),
                classification.colorHex());
    }

    private SubjectGradeSummary toSubjectGradeSummary(StudentSubjectMarksTotals row) {
        BigDecimal percentage = gradeCalculationService.percentage(row.getTotalObtained(), row.getTotalMaximum());
        Optional<GradeBand> band = gradeCalculationService.bandFor(percentage);
        return new SubjectGradeSummary(
                row.getSubjectId(),
                row.getSubjectCode(),
                row.getSubjectName(),
                row.getCredits(),
                row.getAcademicYear(),
                row.getSemester(),
                nz(row.getExamCount()),
                row.getTotalObtained(),
                row.getTotalMaximum(),
                percentage,
                band.map(GradeBand::getGrade).orElse(null),
                band.map(GradeBand::getGradePoint).orElse(null),
                band.map(GradeBand::isPassGrade).orElse(null));
    }

    private static boolean tupleAllowed(
            Set<String> allowedTupleKeys, Long subjectId, String academicYear, Integer semester, String section) {
        if (allowedTupleKeys == null) {
            return true;
        }
        return allowedTupleKeys.contains(AnalyticsScopeResolver.tupleKey(subjectId, academicYear, semester, section));
    }

    private static final class StudentAccumulator {
        Long studentId;
        String registerNumber;
        String studentName;
        Long departmentId;
        String departmentCode;
        String departmentName;
        Long courseId;
        String courseName;
        long heldClasses;
        long attendedClasses;
        long cancelledClasses;
        final List<SubjectGradeSummary> subjects = new ArrayList<>();
    }

    private static final class SubjectAccumulator {
        Long subjectId;
        String subjectCode;
        String subjectName;
        Integer credits;
        final Set<Long> studentIds = new LinkedHashSet<>();
        long heldClasses;
        long attendedClasses;
        long examCount;
        BigDecimal totalObtained;
        BigDecimal totalMaximum;
    }

    private static final class SemesterAccumulator {
        final String academicYear;
        final Integer semester;
        final Set<Long> studentIds = new LinkedHashSet<>();
        long heldClasses;
        long attendedClasses;
        BigDecimal totalObtained;
        BigDecimal totalMaximum;
        final List<BigDecimal> gpas = new ArrayList<>();

        SemesterAccumulator(String academicYear, Integer semester) {
            this.academicYear = academicYear;
            this.semester = semester;
        }
    }

    private static final class DepartmentAccumulator {
        final Long departmentId;
        final String departmentCode;
        final String departmentName;
        final Set<Long> studentIds = new LinkedHashSet<>();
        long heldClasses;
        long attendedClasses;
        BigDecimal totalObtained;
        BigDecimal totalMaximum;
        final List<BigDecimal> gpas = new ArrayList<>();

        DepartmentAccumulator(Long departmentId, String departmentCode, String departmentName) {
            this.departmentId = departmentId;
            this.departmentCode = departmentCode;
            this.departmentName = departmentName;
        }
    }

    // ------------------------------------------------------------------
    // Filter options
    // ------------------------------------------------------------------

    /** {@code GET /api/analytics/filters} — FACULTY or ADMIN. */
    @Transactional(readOnly = true)
    public AnalyticsFilterOptionsResponse filterOptions(User caller) {
        if (caller == null) {
            throw new AccessDeniedException("Authentication is required for this operation.");
        }
        if (caller.getRole() == Role.STUDENT) {
            throw new AccessDeniedException("Analytics filters are restricted to faculty and admin accounts.");
        }

        if (caller.getRole() == Role.FACULTY) {
            List<TeachingClassResponse> classes = teachingService.myClasses(caller);

            Map<Long, FilterCourseOption> courses = new LinkedHashMap<>();
            Map<Long, FilterSubjectOption> subjects = new LinkedHashMap<>();
            Set<String> years = new TreeSet<>(Comparator.reverseOrder());
            Set<Integer> semesters = new TreeSet<>();
            Set<String> sections = new TreeSet<>();
            for (TeachingClassResponse c : classes) {
                courses.putIfAbsent(c.courseId(), new FilterCourseOption(c.courseId(), c.courseCode(), c.courseName()));
                subjects.putIfAbsent(
                        c.subjectId(),
                        new FilterSubjectOption(c.subjectId(), c.subjectCode(), c.subjectName(), c.courseId(), c.semester()));
                years.add(c.academicYear());
                semesters.add(c.semester());
                sections.add(c.section());
            }
            return new AnalyticsFilterOptionsResponse(
                    courses.values().stream().sorted(Comparator.comparing(FilterCourseOption::code)).toList(),
                    subjects.values().stream().sorted(Comparator.comparing(FilterSubjectOption::code)).toList(),
                    new ArrayList<>(years),
                    new ArrayList<>(semesters),
                    new ArrayList<>(sections));
        }

        // ADMIN
        List<FilterCourseOption> courses =
                courseRepository.findAll().stream()
                        .sorted(Comparator.comparing(Course::getCode))
                        .map(c -> new FilterCourseOption(c.getId(), c.getCode(), c.getName()))
                        .toList();
        List<FilterSubjectOption> subjects =
                subjectRepository.findAll().stream()
                        .sorted(Comparator.comparing(Subject::getCode))
                        .map(s -> new FilterSubjectOption(s.getId(), s.getCode(), s.getName(), s.getCourse().getId(), s.getSemester()))
                        .toList();
        return new AnalyticsFilterOptionsResponse(
                courses,
                subjects,
                analyticsCohortRepository.findDistinctAcademicYears(),
                analyticsCohortRepository.findDistinctSemesters(),
                analyticsCohortRepository.findDistinctSections());
    }

    // ------------------------------------------------------------------
    // Shared arithmetic / mapping helpers
    // ------------------------------------------------------------------

    private int resolveMonths(Integer requested) {
        if (requested != null && requested >= 1 && requested <= maxTrendMonths) {
            return requested;
        }
        return defaultTrendMonths;
    }

    private AttendanceTrendPoint toAttendanceTrendPoint(AttendanceTrendTotals t) {
        String period = String.format("%04d-%02d", t.getPeriodYear(), t.getPeriodMonth());
        LocalDate periodStart = LocalDate.of(t.getPeriodYear(), t.getPeriodMonth(), 1);
        BigDecimal percentage =
                gradeCalculationService.percentage(
                        t.getAttendedClasses() == null ? null : BigDecimal.valueOf(t.getAttendedClasses()),
                        t.getHeldClasses() == null ? null : BigDecimal.valueOf(t.getHeldClasses()));
        return new AttendanceTrendPoint(period, periodStart, t.getHeldClasses(), t.getAttendedClasses(), percentage);
    }

    private MarksTrendPoint toMarksTrendPoint(MarksTrendTotals t) {
        String period = String.format("%04d-%02d", t.getPeriodYear(), t.getPeriodMonth());
        LocalDate periodStart = LocalDate.of(t.getPeriodYear(), t.getPeriodMonth(), 1);
        BigDecimal percentage = gradeCalculationService.percentage(t.getTotalObtained(), t.getTotalMaximum());
        return new MarksTrendPoint(period, periodStart, t.getExamCount(), t.getTotalObtained(), t.getTotalMaximum(), percentage);
    }

    private ExamAveragePoint toExamAveragePoint(ExamMarksTotals t) {
        Long count = t.getMarksEnteredCount();
        BigDecimal averageObtained =
                (count == null || count == 0 || t.getTotalObtained() == null)
                        ? null
                        : t.getTotalObtained().divide(BigDecimal.valueOf(count), GradeCalculationService.SCALE, RoundingMode.HALF_UP);
        BigDecimal averagePercentage = gradeCalculationService.percentage(averageObtained, t.getMaximumMarks());
        return new ExamAveragePoint(
                t.getExamId(),
                t.getTitle(),
                t.getExamType(),
                t.getExamDate(),
                t.getMaximumMarks(),
                count,
                t.getTotalObtained(),
                averageObtained,
                averagePercentage,
                t.getHighestObtained(),
                t.getLowestObtained());
    }

    /** {@code attended/held}, scale 2 HALF_UP, {@code null} when {@code held == 0} (G6) — never a fabricated 0. */
    private BigDecimal pctOf(long attended, long held) {
        return gradeCalculationService.percentage(BigDecimal.valueOf(attended), BigDecimal.valueOf(held));
    }

    /** The arithmetic mean of a list of GPAs, scale 2 HALF_UP, {@code null} when the list is empty. */
    private static BigDecimal meanOf(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            sum = sum.add(v);
        }
        return sum.divide(BigDecimal.valueOf(values.size()), GradeCalculationService.SCALE, RoundingMode.HALF_UP);
    }

    /** {@code a + b} treating a {@code null} operand as "nothing to add" rather than zero; {@code null} only when BOTH are null. */
    private static BigDecimal addNullable(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) {
            return null;
        }
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.add(b);
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
