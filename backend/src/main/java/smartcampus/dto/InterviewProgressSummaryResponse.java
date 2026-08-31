package smartcampus.dto;

import java.util.List;

/**
 * A student's whole-bank progress summary. {@code notStarted} is always {@code
 * totalQuestions - completed} — derived, never stored, so it cannot drift from the real
 * counts. {@code byCategory} carries exactly one entry per {@link
 * smartcampus.entity.InterviewQuestionCategory} value, including categories with zero
 * questions in the bank — an absent category is not the same as a category with no data.
 */
public record InterviewProgressSummaryResponse(
        long totalQuestions,
        long completed,
        long bookmarked,
        long notStarted,
        List<InterviewCategoryProgressResponse> byCategory) {}
