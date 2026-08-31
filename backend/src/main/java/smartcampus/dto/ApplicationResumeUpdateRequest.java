package smartcampus.dto;

import jakarta.validation.constraints.NotNull;

/**
 * {@code PATCH /api/applications/{id}/resume} — attaches (or replaces) the resume on an
 * already-submitted application. Allowed only while the application's status is
 * non-terminal; see {@code PlacementApplicationService#updateResume}.
 */
public record ApplicationResumeUpdateRequest(@NotNull Long resumeId) {}
