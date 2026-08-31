package smartcampus.dto;

import java.math.BigDecimal;
import smartcampus.entity.GradeScale;

public record ResumeEducationResponse(
    Long id,
    String institution,
    String degree,
    String fieldOfStudy,
    Integer startYear,
    Integer endYear,
    BigDecimal gradeValue,
    GradeScale gradeScale,
    int displayOrder) {}
