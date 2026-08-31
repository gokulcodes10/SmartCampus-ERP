package smartcampus.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request to apply for a placement drive (§35).
 *
 * <p>{@code resumeId} is optional (Phase 9) — {@code null} means "apply with no resume
 * attached". A non-null value must resolve to a resume owned by the caller or the
 * service throws a 404, never a 403 (an id must not be probeable).
 */
public record PlacementApplicationCreateRequest(
    @NotNull Long jobId,
    Long resumeId,
    @Size(max = 2000) String coverNote) {}
