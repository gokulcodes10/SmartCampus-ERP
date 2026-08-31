package smartcampus.dto;

/**
 * Placement statistics for one company in the analytics breakdown (top 5 companies
 * by selected applications, then by application count).
 */
public record CompanyPlacementRow(
    Long companyId,
    String companyName,
    long jobCount,
    long applicationCount,
    long selectedCount) {}
