package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/ai/explain}: ask for a topic explanation. */
public record AIExplainRequest(
        @NotBlank @Size(max = 200) String topic, @Size(max = 500) String focus, Long subjectId) {}
