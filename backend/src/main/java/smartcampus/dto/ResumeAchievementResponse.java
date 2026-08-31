package smartcampus.dto;

import java.time.LocalDate;

public record ResumeAchievementResponse(
    Long id, String title, String description, String issuer, LocalDate achievedOn, int displayOrder) {}
