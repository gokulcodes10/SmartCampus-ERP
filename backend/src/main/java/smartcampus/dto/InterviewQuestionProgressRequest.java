package smartcampus.dto;

/**
 * STUDENT-only progress upsert payload. Both fields are deliberately nullable: {@code
 * null} means "leave this flag unchanged" — sending {@code {"bookmarked": true}} must
 * not clear {@code completed}, and vice versa.
 */
public record InterviewQuestionProgressRequest(Boolean completed, Boolean bookmarked) {}
