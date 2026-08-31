package smartcampus.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import smartcampus.entity.GradeScale;

/**
 * One education entry submitted as part of {@link ResumeSaveRequest}. Cross-field rules
 * that Bean Validation cannot express (year order, grade value/scale pairing, per-scale
 * grade range) are enforced in {@code ResumeService} before any save, mirroring the
 * {@code resume_educations} CHECK constraints in V9__resume.sql.
 */
public record ResumeEducationRequest(
    @NotBlank @Size(max = 200) String institution,
    @Size(max = 150) String degree,
    @Size(max = 150) String fieldOfStudy,
    @Min(1950) @Max(2100) Integer startYear,
    @Min(1950) @Max(2100) Integer endYear,
    @DecimalMin("0.00") @Digits(integer = 3, fraction = 2) BigDecimal gradeValue,
    GradeScale gradeScale) {}
