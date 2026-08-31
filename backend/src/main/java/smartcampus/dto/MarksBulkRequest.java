package smartcampus.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Request body for {@code POST /api/marks/bulk} — UPSERT semantics against the unique
 * key {@code (exam_id, student_id)}, exactly like {@code AttendanceBulkRequest} does for
 * attendance. Re-submitting the same sheet updates rows instead of duplicating them.
 */
public record MarksBulkRequest(
        @NotNull(message = "Exam is required.") Long examId,
        @NotEmpty(message = "At least one mark entry is required.") @Valid List<MarksEntry> entries) {}
