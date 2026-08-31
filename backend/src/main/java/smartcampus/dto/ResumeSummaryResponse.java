package smartcampus.dto;

import java.time.LocalDateTime;
import smartcampus.entity.ResumeTemplate;

/** One row of {@code GET /api/resumes/me} - the resume list screen, no section detail. */
public record ResumeSummaryResponse(
    Long id,
    String title,
    ResumeTemplate template,
    boolean locked,
    LocalDateTime lockedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
