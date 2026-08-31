package smartcampus.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * A faculty (or admin) submission of one teaching session's attendance for a whole
 * class: one (subject, academicYear, semester, section) tuple, one date, one period,
 * many students. {@code AttendanceService} upserts each entry against the
 * {@code (student_id, subject_id, attendance_date, period)} unique key, so resubmitting
 * the same roster corrects it instead of doubling the attendance-percentage
 * denominator.
 */
public record AttendanceBulkRequest(
        @NotNull Long subjectId,
        @NotBlank
                @Pattern(
                        regexp = "\\d{4}-\\d{4}",
                        message = "Academic year must be in the form \"2025-2026\".")
                String academicYear,
        @NotNull @Positive Integer semester,
        @NotBlank @Size(max = 10) String section,
        @NotNull @PastOrPresent LocalDate date,
        @NotNull @Min(1) @Max(12) Integer period,
        @NotEmpty @Valid List<AttendanceMarkEntry> entries) {}
