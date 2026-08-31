package smartcampus.dto;

import java.math.BigDecimal;

/**
 * Placement statistics for one department in the analytics breakdown, including
 * active student count and placement rate.
 */
public record DepartmentPlacementRow(
    Long departmentId,
    String departmentName,
    long activeStudents,
    long applicants,
    long selected,
    BigDecimal placementRate) {}
