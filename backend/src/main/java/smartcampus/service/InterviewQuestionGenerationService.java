package smartcampus.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import smartcampus.dto.InterviewGeneratedQuestionsResponse;
import smartcampus.dto.InterviewQuestionGenerateRequest;
import smartcampus.dto.InterviewQuestionResponse;
import smartcampus.entity.AIFeature;
import smartcampus.entity.AIMessageRole;
import smartcampus.entity.AIRequestOutcome;
import smartcampus.entity.InterviewDifficulty;
import smartcampus.entity.InterviewQuestion;
import smartcampus.entity.InterviewQuestionCategory;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.User;
import smartcampus.exception.AIUnavailableException;
import smartcampus.exception.BadRequestException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Orchestrates {@code POST /api/interview-questions/generate}: generates and privately
 * persists AI-written interview-practice questions for the calling student, reusing the
 * whole Phase 6 AI stack rather than building a second provider client, rate limiter or
 * request ledger.
 *
 * <p><b>Deliberately NOT {@code @Transactional}</b> — the exact shape {@code
 * AIAssistantService}'s feature methods and {@code AIStudyPlanService#generate} use:
 * authorize → validate → rate-limit check → build a fresh academic context → CALL THE
 * PROVIDER (no open transaction) → parse → persist through {@link
 * InterviewQuestionRecorder} (a separate {@code @Service} bean so its writes really
 * commit through the real Spring proxy) → log the ledger row through {@link
 * AIRequestLogRecorder} ({@code REQUIRES_NEW}, so it survives even when this method goes
 * on to throw). If this whole sequence lived in one {@code @Transactional} method, a
 * thrown exception after the provider call would roll back the ledger row that proves
 * the attempt happened — the row the rate limiter depends on.
 *
 * <p>Reuses {@link AIFeature#PRACTICE_QUESTIONS} — Phase 10 introduces no new {@code
 * AIFeature} constant, since {@code V6__ai.sql} CHECK-constrains {@code
 * ai_request_logs.feature} to exactly six values and this phase does not alter that
 * table. No {@link smartcampus.entity.AIConversation} is created for interview
 * generation; {@code null} is passed as the conversation to every ledger write —
 * {@code ai_request_logs.conversation_id} is nullable.
 */
@Service
public class InterviewQuestionGenerationService {

    private static final String NOT_CONFIGURED_MESSAGE =
            "AI is not configured: set AI_API_KEY in the environment.";
    private static final String INVALID_RESPONSE_MESSAGE =
            "The AI response could not be parsed into questions.";
    private static final int DEFAULT_COUNT = 5;
    private static final int TAGS_MAX_LENGTH = 255;

    private final InterviewQuestionRecorder interviewQuestionRecorder;
    private final InterviewPromptBuilder interviewPromptBuilder;
    private final AIContextService aiContextService;
    private final AIPromptBuilder aiPromptBuilder;
    private final AIService aiService;
    private final AIRateLimiter aiRateLimiter;
    private final AIRequestLogRecorder aiRequestLogRecorder;
    private final ScopedWriteAuthorizer scopedWriteAuthorizer;
    private final ObjectMapper objectMapper;

    public InterviewQuestionGenerationService(
            InterviewQuestionRecorder interviewQuestionRecorder,
            InterviewPromptBuilder interviewPromptBuilder,
            AIContextService aiContextService,
            AIPromptBuilder aiPromptBuilder,
            AIService aiService,
            AIRateLimiter aiRateLimiter,
            AIRequestLogRecorder aiRequestLogRecorder,
            ScopedWriteAuthorizer scopedWriteAuthorizer,
            ObjectMapper objectMapper) {
        this.interviewQuestionRecorder = interviewQuestionRecorder;
        this.interviewPromptBuilder = interviewPromptBuilder;
        this.aiContextService = aiContextService;
        this.aiPromptBuilder = aiPromptBuilder;
        this.aiService = aiService;
        this.aiRateLimiter = aiRateLimiter;
        this.aiRequestLogRecorder = aiRequestLogRecorder;
        this.scopedWriteAuthorizer = scopedWriteAuthorizer;
        this.objectMapper = objectMapper;
    }

    public InterviewGeneratedQuestionsResponse generate(InterviewQuestionGenerateRequest req, User caller) {
        Student student = requireStudentCaller(caller);

        if (req.category() == InterviewQuestionCategory.COMPANY_SPECIFIC
                && (req.companyName() == null || req.companyName().isBlank())) {
            throw new BadRequestException("A company-specific question must name a company.");
        }

        aiRateLimiter.checkAllowed(caller);

        StudentAcademicContext ctx = aiContextService.buildFor(caller);
        String systemPrompt = aiPromptBuilder.systemPrompt(ctx);
        String userTurn = interviewPromptBuilder.generateInstruction(req);

        List<AIChatMessage> providerMessages =
                List.of(
                        new AIChatMessage(AIMessageRole.SYSTEM, systemPrompt),
                        new AIChatMessage(AIMessageRole.USER, userTurn));

        AICompletion completion = callProvider(caller, providerMessages);

        InterviewDifficulty difficulty = req.difficulty() != null ? req.difficulty() : InterviewDifficulty.MEDIUM;
        int requestedCount = req.count() != null ? req.count() : DEFAULT_COUNT;
        String companyName =
                req.companyName() != null && !req.companyName().isBlank() ? req.companyName().trim() : null;

        List<InterviewQuestionRecorder.ParsedQuestion> parsed;
        try {
            parsed = parseQuestions(completion.content(), requestedCount);
        } catch (InvalidResponseException ex) {
            aiRequestLogRecorder.record(
                    caller, null, AIFeature.PRACTICE_QUESTIONS, AIRequestOutcome.INVALID_RESPONSE,
                    completion.model(), completion, ex.getMessage());
            throw new AIUnavailableException(ex.getMessage());
        }

        List<InterviewQuestion> saved =
                interviewQuestionRecorder.saveAll(
                        student, caller, req.category(), difficulty, companyName, completion.model(), parsed);

        aiRequestLogRecorder.record(
                caller, null, AIFeature.PRACTICE_QUESTIONS, AIRequestOutcome.SUCCESS, completion.model(),
                completion, null);

        return new InterviewGeneratedQuestionsResponse(
                completion.model(),
                saved.size(),
                saved.stream().map(q -> InterviewQuestionResponse.from(q, null)).toList());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Student requireStudentCaller(User caller) {
        if (caller == null || caller.getRole() != Role.STUDENT) {
            throw new AccessDeniedException("Interview question generation is available to student accounts only.");
        }
        return scopedWriteAuthorizer.requireOwnStudent(caller);
    }

    /**
     * Calls the provider and, on any failure, logs the honest ledger row
     * (NOT_CONFIGURED when {@link AIService#isConfigured()} is false, PROVIDER_ERROR for
     * every other failure) through {@link AIRequestLogRecorder} — which commits in its
     * own {@code REQUIRES_NEW} transaction — before rethrowing. Never persists a
     * question on this path.
     */
    private AICompletion callProvider(User caller, List<AIChatMessage> messages) {
        if (!aiService.isConfigured()) {
            aiRequestLogRecorder.record(
                    caller, null, AIFeature.PRACTICE_QUESTIONS, AIRequestOutcome.NOT_CONFIGURED, null, null,
                    NOT_CONFIGURED_MESSAGE);
            throw new AIUnavailableException(NOT_CONFIGURED_MESSAGE);
        }
        try {
            return aiService.complete(AICompletionRequest.json(messages));
        } catch (AIUnavailableException ex) {
            aiRequestLogRecorder.record(
                    caller, null, AIFeature.PRACTICE_QUESTIONS, AIRequestOutcome.PROVIDER_ERROR, null, null,
                    ex.getMessage());
            throw ex;
        }
    }

    // ------------------------------------------------------------------
    // Parsing — defensive, never fabricates a question (§69).
    // ------------------------------------------------------------------

    private static final class InvalidResponseException extends RuntimeException {
        InvalidResponseException(String message) {
            super(message);
        }
    }

    /**
     * Parses {@code {"questions":[{"question":...,"answer":...,"explanation":...,"tags":...}]}}
     * from {@code content}, stripping a ```json fence if present. Skips any entry whose
     * {@code question} is null or blank (never fabricates a substitute), truncates
     * {@code tags} to 255 characters, and caps the number of items returned at {@code
     * requestedCount}. Throws {@link InvalidResponseException} when nothing usable could
     * be parsed at all.
     */
    private List<InterviewQuestionRecorder.ParsedQuestion> parseQuestions(String content, int requestedCount) {
        String cleaned = stripCodeFence(content);
        JsonNode root;
        try {
            root = objectMapper.readTree(cleaned);
        } catch (RuntimeException ex) {
            throw new InvalidResponseException(INVALID_RESPONSE_MESSAGE);
        }
        if (root == null || !root.isObject()) {
            throw new InvalidResponseException(INVALID_RESPONSE_MESSAGE);
        }

        JsonNode questionsNode = root.get("questions");
        if (questionsNode == null || !questionsNode.isArray() || questionsNode.size() == 0) {
            throw new InvalidResponseException(INVALID_RESPONSE_MESSAGE);
        }

        List<InterviewQuestionRecorder.ParsedQuestion> parsed = new ArrayList<>();
        for (JsonNode questionNode : questionsNode) {
            if (parsed.size() >= requestedCount) {
                break;
            }
            InterviewQuestionRecorder.ParsedQuestion pq = parseOne(questionNode);
            if (pq != null) {
                parsed.add(pq);
            }
        }
        if (parsed.isEmpty()) {
            throw new InvalidResponseException(INVALID_RESPONSE_MESSAGE);
        }
        return parsed;
    }

    private InterviewQuestionRecorder.ParsedQuestion parseOne(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String question = textOrNull(node.get("question"));
        if (question == null || question.isBlank()) {
            // A blank question would violate chk_interview_questions_question_not_blank
            // and would be a fabricated placeholder besides — skip it, never substitute.
            return null;
        }
        String answer = textOrNull(node.get("answer"));
        String explanation = textOrNull(node.get("explanation"));
        String tags = textOrNull(node.get("tags"));
        if (tags != null && tags.length() > TAGS_MAX_LENGTH) {
            tags = tags.substring(0, TAGS_MAX_LENGTH);
        }
        return new InterviewQuestionRecorder.ParsedQuestion(question.trim(), answer, explanation, tags);
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isString()) {
            return null;
        }
        return node.asString();
    }

    /** Strips a ```json ... ``` (or bare ``` ... ```) fence some providers wrap JSON in. */
    private String stripCodeFence(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline == -1) {
            return trimmed;
        }
        String withoutOpeningFence = trimmed.substring(firstNewline + 1);
        int closingFence = withoutOpeningFence.lastIndexOf("```");
        if (closingFence == -1) {
            return withoutOpeningFence.trim();
        }
        return withoutOpeningFence.substring(0, closingFence).trim();
    }
}
