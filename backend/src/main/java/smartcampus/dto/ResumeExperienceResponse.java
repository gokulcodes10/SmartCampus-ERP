package smartcampus.dto;

import java.time.LocalDate;
import smartcampus.entity.EmploymentType;

public record ResumeExperienceResponse(
    Long id,
    String companyName,
    String roleTitle,
    String location,
    EmploymentType employmentType,
    LocalDate startDate,
    LocalDate endDate,
    boolean currentPosition,
    String description,
    int displayOrder) {}
