package smartcampus.dto;

import java.util.List;

/**
 * Suggested starting values for a new resume, read live from the account and academic
 * record (§69 - nothing here is fabricated). {@code phone} and {@code location} are
 * ALWAYS {@code null}: {@code users} has no phone column and no address is stored
 * anywhere in this application. {@code educations} has zero or one element; when present,
 * its {@code institution} is {@code ""} - this application stores no college name, so the
 * student must type it themselves.
 */
public record ResumePrefillResponse(
    String suggestedTitle,
    String fullName,
    String email,
    String phone,
    String location,
    List<ResumeEducationRequest> educations) {}
