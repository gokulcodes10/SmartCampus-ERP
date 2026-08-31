package smartcampus.dto;

import java.time.LocalDate;

public record ResumeProjectResponse(
    Long id,
    String name,
    String description,
    String techStack,
    String projectUrl,
    String repositoryUrl,
    LocalDate startDate,
    LocalDate endDate,
    int displayOrder) {}
