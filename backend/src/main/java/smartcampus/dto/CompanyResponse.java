package smartcampus.dto;

import java.time.LocalDateTime;
import smartcampus.entity.CompanyStatus;

/**
 * Response representing a recruiting company (§33), including aggregated job counts.
 */
public record CompanyResponse(
    Long id,
    String name,
    String industry,
    String website,
    String description,
    String location,
    String contactPerson,
    String contactEmail,
    String contactPhone,
    CompanyStatus status,
    long jobCount,
    long openJobCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
