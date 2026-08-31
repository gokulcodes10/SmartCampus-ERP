package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code PUT /api/ai/conversations/{id}}: renames the thread. */
public record AIConversationRenameRequest(@NotBlank @Size(max = 150) String title) {}
