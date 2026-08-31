package smartcampus.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import smartcampus.entity.PerformanceBand;
import smartcampus.entity.PerformanceCategory;

/** Response representation of a {@code PerformanceBand}, safe to return to any authenticated role. */
public record PerformanceBandResponse(
        Long id,
        PerformanceCategory category,
        Integer displayOrder,
        BigDecimal minMarksPercentage,
        BigDecimal minAttendancePercentage,
        BigDecimal minGpa,
        String colorHex,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static PerformanceBandResponse from(PerformanceBand band) {
        return new PerformanceBandResponse(
                band.getId(),
                band.getCategory(),
                band.getDisplayOrder(),
                band.getMinMarksPercentage(),
                band.getMinAttendancePercentage(),
                band.getMinGpa(),
                band.getColorHex(),
                band.getDescription(),
                band.getCreatedAt(),
                band.getUpdatedAt());
    }
}
