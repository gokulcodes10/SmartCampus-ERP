package smartcampus.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import smartcampus.entity.CompanyStatus;

/**
 * Request to update a recruiting company (§33).
 */
public record CompanyUpdateRequest(
    @NotBlank @Size(max = 150) String name,
    @Size(max = 100) String industry,
    @Size(max = 255) String website,
    String description,
    @Size(max = 150) String location,
    @Size(max = 120) String contactPerson,
    @Email @Size(max = 255) String contactEmail,
    @Size(max = 20) String contactPhone,
    @NotNull CompanyStatus status) {}
