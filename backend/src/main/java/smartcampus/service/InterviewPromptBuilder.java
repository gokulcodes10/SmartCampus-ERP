package smartcampus.service;

import org.springframework.stereotype.Component;
import smartcampus.dto.InterviewQuestionGenerateRequest;

/**
 * Renders the user-turn instruction for interview-question generation, in the style of
 * {@link AIPromptBuilder#practiceQuestionsInstruction}. PURE: a deterministic function of
 * its argument only — no repository, no clock, no randomness.
 *
 * <p>Instructs the model to reply with ONLY a JSON object of the exact shape parsed by
 * {@code InterviewQuestionGenerationService}:
 * {@code {"questions":[{"question":"...","answer":"...","explanation":"...","tags":"comma,separated"}]}}.
 * The grounding SYSTEM prompt (the student's real academic record) is built separately by
 * {@link AIContextService#buildFor} / {@link AIPromptBuilder#systemPrompt} — this class
 * only renders the USER turn.
 */
@Component
public class InterviewPromptBuilder {

    public String generateInstruction(InterviewQuestionGenerateRequest req) {
        int count = req.count() != null ? req.count() : 5;
        String difficulty = req.difficulty() != null ? req.difficulty().name() : "MEDIUM";

        StringBuilder sb = new StringBuilder();
        sb.append("Generate ")
                .append(count)
                .append(" interview preparation questions in the \"")
                .append(req.category())
                .append("\" category at \"")
                .append(difficulty)
                .append("\" difficulty, for this student to practice for job interviews.\n");

        if (req.topic() != null && !req.topic().isBlank()) {
            sb.append("Focus specifically on the topic: ").append(req.topic().trim()).append(".\n");
        }
        if (req.companyName() != null && !req.companyName().isBlank()) {
            sb.append("Tailor the questions toward interviews at: ").append(req.companyName().trim()).append(".\n");
        }

        sb.append(
                "For each question, include a model answer, a short explanation of why that answer is "
                        + "correct or what it should cover, and a short comma-separated list of relevant tags. "
                        + "Base every question on real, well-established interview practice for this category — "
                        + "never invent a fact about a real company that you are not confident is true.\n");
        sb.append("Respond with ONLY a single JSON object of this exact shape, no prose outside it:\n");
        sb.append(
                "{\"questions\":[{\"question\":\"...\",\"answer\":\"...\",\"explanation\":\"...\","
                        + "\"tags\":\"comma,separated\"}]}\n");

        return sb.toString();
    }
}
