package smartcampus.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.entity.InterviewDifficulty;
import smartcampus.entity.InterviewQuestion;
import smartcampus.entity.InterviewQuestionCategory;
import smartcampus.entity.InterviewQuestionSource;
import smartcampus.entity.Student;
import smartcampus.entity.User;
import smartcampus.repository.InterviewQuestionRepository;

/**
 * All persistence for one AI-generated batch of interview questions, in its own bean so
 * the writes truly commit through the real Spring proxy — the same shape {@link
 * AIRequestLogRecorder} and {@code CodingSubmissionRecorder} exist for. {@link
 * InterviewQuestionGenerationService#generate} is deliberately NOT {@code @Transactional}
 * because it calls the external AI provider first; only after a real completion is
 * parsed does it hand the result here to persist.
 *
 * <p>{@code owner} and {@code createdBy} are passed in as already-loaded entities (the
 * caller's {@link Student} resolved via {@code StudentRepository.findByUserId}, and the
 * {@link User} principal itself) — never re-fetched by a lazy reference — so the caller
 * can safely read {@code getOwnerStudent()} off the returned rows after this method
 * returns, even with {@code spring.jpa.open-in-view=false}.
 */
@Service
public class InterviewQuestionRecorder {

    private final InterviewQuestionRepository interviewQuestionRepository;

    public InterviewQuestionRecorder(InterviewQuestionRepository interviewQuestionRepository) {
        this.interviewQuestionRepository = interviewQuestionRepository;
    }

    /** One parsed, already-validated question ready to persist. */
    public record ParsedQuestion(String question, String answer, String explanation, String tags) {}

    /**
     * Inserts one {@link InterviewQuestion} row per {@code parsedQuestions} entry, all
     * {@code source = AI_GENERATED}, owned privately by {@code owner}, and commits.
     * {@code model} must be non-null — {@code chk_interview_questions_ai_has_model}
     * refuses the row otherwise.
     */
    @Transactional
    public List<InterviewQuestion> saveAll(
            Student owner,
            User createdBy,
            InterviewQuestionCategory category,
            InterviewDifficulty difficulty,
            String companyName,
            String model,
            List<ParsedQuestion> parsedQuestions) {
        List<InterviewQuestion> toSave = new ArrayList<>(parsedQuestions.size());
        for (ParsedQuestion pq : parsedQuestions) {
            toSave.add(
                    InterviewQuestion.builder()
                            .category(category)
                            .difficulty(difficulty)
                            .question(pq.question())
                            .answer(pq.answer())
                            .explanation(pq.explanation())
                            .companyName(companyName)
                            .tags(pq.tags())
                            .source(InterviewQuestionSource.AI_GENERATED)
                            .model(model)
                            .ownerStudent(owner)
                            .createdBy(createdBy)
                            .build());
        }
        return interviewQuestionRepository.saveAll(toSave);
    }
}
