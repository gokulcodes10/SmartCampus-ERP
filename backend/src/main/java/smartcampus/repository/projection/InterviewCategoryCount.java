package smartcampus.repository.projection;

import smartcampus.entity.InterviewQuestionCategory;

/**
 * Projection for interview question category counts.
 *
 * <p>Used by aggregation queries to count interview questions and progress by category.
 */
public interface InterviewCategoryCount {
    InterviewQuestionCategory getCategory();

    long getTotal();
}
