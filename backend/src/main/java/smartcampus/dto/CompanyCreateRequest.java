package smartcampus.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to create a new recruiting company (§33).
 */
public record CompanyCreateRequest(
    @NotBlank @Size(max = 150) String name,
    @Size(max = 100) String industry,
    @Size(max = 255) String website,
    String description,
    @Size(max = 150) String location,
    @Size(max = 120) String contactPerson,
    @Email @Size(max = 255) String contactEmail,
    @Size(max = 20) String contactPhone) {}
