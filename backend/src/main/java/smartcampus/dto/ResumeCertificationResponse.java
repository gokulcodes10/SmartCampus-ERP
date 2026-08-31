package smartcampus.dto;

import java.time.LocalDate;

public record ResumeCertificationResponse(
    Long id,
    String name,
    String issuer,
    LocalDate issueDate,
    LocalDate expiryDate,
    String credentialId,
    String credentialUrl,
    int displayOrder) {}
