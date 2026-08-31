package smartcampus.dto;

/**
 * Placement funnel statistics for one job in the analytics breakdown (top 10 jobs
 * by application count), showing the pipeline progression from application to selection.
 */
public record JobFunnelRow(
    Long jobId,
    String jobTitle,
    String companyName,
    long applicationCount,
    long shortlistedCount,
    long selectedCount,
    long rejectedCount) {}
