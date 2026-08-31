package smartcampus.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import smartcampus.entity.GradeBand;

/** Response representation of a {@code GradeBand}, safe to return to any authenticated role. */
public record GradeBandResponse(
        Long id,
        String grade,
        BigDecimal minPercentage,
        BigDecimal maxPercentage,
        BigDecimal gradePoint,
        boolean passGrade,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static GradeBandResponse from(GradeBand band) {
        return new GradeBandResponse(
                band.getId(),
                band.getGrade(),
                band.getMinPercentage(),
                band.getMaxPercentage(),
                band.getGradePoint(),
                band.isPassGrade(),
                band.getDescription(),
                band.getCreatedAt(),
                band.getUpdatedAt());
    }
}
