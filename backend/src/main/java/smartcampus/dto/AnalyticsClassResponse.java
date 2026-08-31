package smartcampus.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A class (or a faculty's whole scoped teaching cohort) analytics dashboard — {@code
 * GET /api/analytics/class}. FACULTY only ever sees this filtered to their own {@code
 * AcademicAccessGuard}-authorized assignments; ADMIN may see any scope. {@code
 * courseId}/{@code subjectId}/{@code academicYear}/{@code semester}/{@code section}
 * echo the request filters exactly, {@code null} when omitted. Every list is empty and
 * every count/percentage is zero/null when the caller's scope matched nothing — this
 * response never falls back to unfiltered data.
 */
public record AnalyticsClassResponse(
        Long courseId,
        String courseCode,
        String courseName,
        Long subjectId,
        String subjectCode,
        String subjectName,
        String academicYear,
        Integer semester,
        String section,
        int trendMonths,
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
        List<ExamAveragePoint> examAverages,
        List<AttendanceTrendPoint> attendanceTrend,
        List<MarksTrendPoint> marksTrend,
        List<ClassificationSlice> classificationDistribution,
        List<GradeDistributionSlice> gradeDistribution) {}
