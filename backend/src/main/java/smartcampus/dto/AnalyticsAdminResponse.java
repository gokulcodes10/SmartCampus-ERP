package smartcampus.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The institution-wide analytics dashboard — {@code GET /api/analytics/overview},
 * ADMIN only. The {@code total*}/{@code active*}/{@code pending*} counters are
 * unscoped institution totals from {@code AnalyticsCohortRepository}, independent of
 * {@code departmentId}/{@code courseId}/etc. The {@code studentCount} family and every
 * list below it are the SCOPED cohort selected by the request filters — every list is
 * empty and every scoped count/percentage is zero/null when the filters match nothing.
 */
public record AnalyticsAdminResponse(
        Long departmentId,
        String departmentName,
        Long courseId,
        String courseName,
        String academicYear,
        Integer semester,
        String section,
        int trendMonths,
        long totalStudents,
        long activeStudents,
        long pendingStudents,
        long totalFaculty,
        long activeFaculty,
        long totalDepartments,
        long totalCourses,
        long totalSubjects,
        long totalExams,
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
        List<DepartmentPerformanceRow> departments,
        List<SemesterPerformancePoint> semesters,
        List<SubjectAveragePoint> subjectAverages,
        List<AttendanceTrendPoint> attendanceTrend,
        List<MarksTrendPoint> marksTrend,
        List<ClassificationSlice> classificationDistribution,
        List<GradeDistributionSlice> gradeDistribution,
        List<CohortStudentRow> atRiskStudents) {}
