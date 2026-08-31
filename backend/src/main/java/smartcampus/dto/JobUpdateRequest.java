package smartcampus.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import smartcampus.entity.JobType;

/**
 * Request to update a job/placement drive (§33-§35), excluding company ID and status.
 */
public record JobUpdateRequest(
    @NotBlank @Size(max = 150) String title,
    String description,
    @Size(max = 150) String location,
    @NotNull JobType jobType,
    @Positive Integer openings,
    @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal salaryMin,
    @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal salaryMax,
    @Pattern(regexp = "^[A-Z]{3}$") String salaryCurrency,
    @DecimalMin("0") @DecimalMax("10") @Digits(integer = 2, fraction = 2) BigDecimal minCgpa,
    @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal minMarksPercentage,
    @Min(1950) @Max(2100) Integer graduationYear,
    List<Long> eligibleDepartmentIds,
    @NotNull LocalDateTime applicationDeadline,
    LocalDate driveDate) {}
