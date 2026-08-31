package smartcampus.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Comprehensive placement analytics and metrics overview (§36), including system-wide
 * statistics, status breakdown, top companies and departments, and per-job funnel metrics.
 */
public record PlacementAnalyticsResponse(
    long totalCompanies,
    long activeCompanies,
    long totalJobs,
    long draftJobs,
    long openJobs,
    long closedJobs,
    long cancelledJobs,
    long totalApplications,
    long uniqueApplicants,
    long selectedStudents,
    long activeStudents,
    BigDecimal placementRate,
    List<ApplicationStatusSlice> statusBreakdown,
    List<CompanyPlacementRow> topCompanies,
    List<DepartmentPlacementRow> departmentBreakdown,
    List<JobFunnelRow> jobFunnel) {}
