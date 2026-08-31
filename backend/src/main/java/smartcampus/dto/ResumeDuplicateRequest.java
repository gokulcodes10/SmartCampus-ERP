package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/resumes/{id}/duplicate} - the title for the new, unlocked copy. */
public record ResumeDuplicateRequest(@NotBlank @Size(max = 150) String title) {}
