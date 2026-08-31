package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** One achievement entry submitted as part of {@link ResumeSaveRequest}. */
public record ResumeAchievementRequest(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 20000) String description,
    @Size(max = 200) String issuer,
    LocalDate achievedOn) {}
