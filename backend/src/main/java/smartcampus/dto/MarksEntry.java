package smartcampus.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * One student's score within a {@link MarksBulkRequest}. The upper bound (marks cannot
 * exceed the exam's maximum) is a cross-table condition the database cannot express as
 * a CHECK constraint, so {@code MarksService} validates it against the exam the bulk
 * request targets before saving anything.
 */
public record MarksEntry(
        @NotNull(message = "Student is required.") Long studentId,
        @NotNull(message = "Marks obtained is required.")
                @DecimalMin(value = "0.00", message = "Marks obtained cannot be negative.")
                @Digits(integer = 4, fraction = 2, message = "Marks obtained may have at most 4 integer and 2 fraction digits.")
                BigDecimal marksObtained,
        @Size(max = 255, message = "Remarks must be at most 255 characters.") String remarks) {}
