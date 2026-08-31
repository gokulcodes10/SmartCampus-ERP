package smartcampus.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import smartcampus.entity.AttendanceStatus;

/**
 * One student's mark within a bulk attendance submission (see {@link AttendanceBulkRequest}).
 */
public record AttendanceMarkEntry(
        @NotNull Long studentId,
        @NotNull AttendanceStatus status,
        @Size(max = 255) String remarks) {}
