package smartcampus.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import smartcampus.entity.AttendanceStatus;

/** A correction to a single, already-recorded attendance row (PUT /api/attendance/{id}). */
public record AttendanceUpdateRequest(
        @NotNull AttendanceStatus status,
        @Size(max = 255) String remarks) {}
