package smartcampus.dto;

import smartcampus.entity.InterviewQuestionCategory;

/**
 * One category's slice of a student's progress summary. {@code total} is every visible
 * question in this category; {@code completed} is how many of those the student has
 * marked done.
 */
public record InterviewCategoryProgressResponse(
        InterviewQuestionCategory category, long total, long completed) {}
