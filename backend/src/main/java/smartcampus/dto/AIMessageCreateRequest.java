package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/ai/conversations/{id}/messages}: one new user turn. */
public record AIMessageCreateRequest(@NotBlank @Size(max = 4000) String message) {}
