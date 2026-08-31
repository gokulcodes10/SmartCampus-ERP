package smartcampus.dto;

import java.math.BigDecimal;
import java.util.List;
import smartcampus.entity.PerformanceCategory;

/**
 * One student's full analytics dashboard — {@code GET /api/analytics/me} (own record)
 * or {@code GET /api/analytics/students/{id}} (ADMIN, any student). {@code attendance}
 * and {@code academics} are the exact same reused Phase 4 {@link
 * AttendanceSummaryResponse}/{@link AcademicResultResponse} objects the attendance and
 * marks pages already show, so {@code cgpa} here can never disagree with the marks
 * page's own figure. {@code academicYear}/{@code semester} echo the request filters
 * (both {@code null} when omitted — "every year/semester").
 */
public record AnalyticsStudentResponse(
        Long studentId,
        String registerNumber,
        String studentName,
        Long departmentId,
        String departmentName,
        Long courseId,
        String courseName,
        Integer currentSemester,
        String section,
        String academicYear,
        Integer semester,
        int trendMonths,
        AttendanceSummaryResponse attendance,
        AcademicResultResponse academics,
        BigDecimal marksPercentage,
        BigDecimal attendancePercentage,
        BigDecimal gpa,
        BigDecimal cgpa,
        PerformanceClassificationResponse classification,
        List<AttendanceTrendPoint> attendanceTrend,
        List<MarksTrendPoint> marksTrend,
        List<SemesterGpaPoint> gpaTrend,
        List<GradeDistributionSlice> gradeDistribution,
        List<SubjectPerformanceRow> subjects) {}
