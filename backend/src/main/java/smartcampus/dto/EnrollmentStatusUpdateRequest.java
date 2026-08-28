package smartcampus.dto;

import jakarta.validation.constraints.NotNull;
import smartcampus.entity.EnrollmentStatus;

/**
 * Request body for {@code PATCH /api/enrollments/{id}/status} — an admin transitioning
 * an enrollment between {@code ACTIVE}, {@code COMPLETED} and {@code DROPPED}.
 */
public record EnrollmentStatusUpdateRequest(
        @NotNull(message = "Status is required.")
        EnrollmentStatus status) {}
