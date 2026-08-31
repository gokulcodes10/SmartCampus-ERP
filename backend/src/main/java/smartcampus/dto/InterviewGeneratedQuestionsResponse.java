package smartcampus.dto;

import java.util.List;

/**
 * Response body of {@code POST /api/interview-questions/generate}. {@code model} is the
 * real provider model id that produced every question in {@code questions} — never
 * {@code null}, mirroring the anti-fabrication guarantee {@code chk_interview_questions_ai_has_model}
 * enforces on the persisted rows. {@code count} is {@code questions.size()}, which may be
 * less than the requested count when the provider returned fewer usable items.
 */
public record InterviewGeneratedQuestionsResponse(String model, int count, List<InterviewQuestionResponse> questions) {}
