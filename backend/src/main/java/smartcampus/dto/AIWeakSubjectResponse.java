package smartcampus.dto;

import java.math.BigDecimal;

/**
 * One subject flagged as weak in a student's academic context — low marks and/or low
 * attendance. Assembled by the context-building service. {@code reason} is a short
 * human-readable explanation ("marks below threshold", "attendance below minimum", or
 * both). Percentage fields are null when they cannot be computed from real rows (§69
 * null policy) — never 0.00.
 */
public record AIWeakSubjectResponse(
        Long subjectId,
        String subjectCode,
        String subjectName,
        BigDecimal marksPercentage,
        BigDecimal attendancePercentage,
        String reason) {}
