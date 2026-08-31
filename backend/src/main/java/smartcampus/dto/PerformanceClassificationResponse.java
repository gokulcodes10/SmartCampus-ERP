package smartcampus.dto;

import java.math.BigDecimal;
import smartcampus.entity.PerformanceCategory;

/**
 * The result of classifying one student's (or one subject row's) figures against the
 * admin-configured {@code performance_bands} table, produced by
 * {@code smartcampus.service.PerformanceClassifier}.
 *
 * <p>{@code category}, {@code colorHex} and {@code description} are {@code null} together when
 * the figures cannot be classified (missing marks and/or attendance, or no band matches) — never
 * defaulted to {@code AT_RISK}, which would be a fabricated verdict (§69). {@code reason} is
 * never {@code null}: it always explains what happened, including the successful case.
 *
 * <p><b>Consumed by the Phase 5 {@code AnalyticsService}, built by another agent — this record's
 * component names and order are frozen.</b> Do not rename, reorder or add fields.
 */
public record PerformanceClassificationResponse(
        PerformanceCategory category,
        String colorHex,
        String description,
        BigDecimal marksPercentage,
        BigDecimal attendancePercentage,
        BigDecimal gpa,
        String reason) {}
