package smartcampus.dto;

import smartcampus.entity.ApplicationStatus;

/**
 * Breakdown of applications by status for analytics.
 */
public record ApplicationStatusSlice(
    ApplicationStatus status,
    long count) {}
