package smartcampus.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.AIChatTurnResponse;
import smartcampus.dto.AIConversationCreateRequest;
import smartcampus.dto.AIConversationDetailResponse;
import smartcampus.dto.AIConversationRenameRequest;
import smartcampus.dto.AIConversationResponse;
import smartcampus.dto.AIExplainRequest;
import smartcampus.dto.AIMcqRequest;
import smartcampus.dto.AIMessageCreateRequest;
import smartcampus.dto.AIMessageResponse;
import smartcampus.dto.AIModelResponse;
import smartcampus.dto.AIPracticeQuestionsRequest;
import smartcampus.dto.AIStatusResponse;
import smartcampus.dto.PageResponse;
import smartcampus.entity.AIConversation;
import smartcampus.entity.AIFeature;
import smartcampus.entity.AIMessage;
import smartcampus.entity.AIMessageRole;
import smartcampus.entity.AIRequestOutcome;
import smartcampus.entity.Role;
import smartcampus.entity.User;
import smartcampus.exception.AIUnavailableException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.AIConversationRepository;
import smartcampus.repository.AIMessageRepository;

/**
 * Orchestrates every AI endpoint EXCEPT study plans (see {@link AIStudyPlanService}):
 * conversation create/continue/rename/delete/list/history, the three single-shot
 * feature endpoints (explain, practice questions, MCQs), the status snapshot and the
 * ADMIN-only model list.
 *
 * <p><b>The orchestration methods that call the AI provider —
 * {@link #createConversation}, {@link #continueConversation}, {@link #explain},
 * {@link #practiceQuestions} and {@link #mcqs} — are deliberately NOT
 * {@code @Transactional}.</b> Each runs: authorize → rate-limit check → build a fresh
 * academic context → CALL THE PROVIDER (no open transaction) → persist through
 * {@link AIConversationRecorder} (a separate {@code @Service} bean so its writes really
 * commit through the real Spring proxy) → log the ledger row through
 * {@link AIRequestLogRecorder} ({@code REQUIRES_NEW}, so it survives even when this
 * method goes on to throw). See {@code CodingSubmissionService}/{@code
 * CodingSubmissionRecorder} for the identical shape solving the identical problem.
 *
 * <p>Authorization: every method here except {@link #status} (any authenticated role)
 * and {@link #models} (ADMIN only) is restricted to STUDENT callers via
 * {@link #requireStudentCaller}. A conversation is loaded ONLY through
 * {@code aiConversationRepository.findByIdAndUserId} — a miss is a 404, never a 403, so
 * an id can never be probed to distinguish "not yours" from "does not exist".
 */
@Service
public class AIAssistantService {

    private static final String NOT_CONFIGURED_MESSAGE = "AI is not configured: set AI_API_KEY in the environment.";
    private static final int TITLE_MAX_LENGTH = 150;
    private static final int DEFAULT_TITLE_SOURCE_LENGTH = 60;

    private final AIConversationRepository aiConversationRepository;
    private final AIMessageRepository aiMessageRepository;
    private final AIConversationRecorder aiConversationRecorder;
    private final AIContextService aiContextService;
    private final AIPromptBuilder aiPromptBuilder;
    private final AIService aiService;
    private final GroqAIService groqAIService;
    private final AIRateLimiter aiRateLimiter;
    private final AIRequestLogRecorder aiRequestLogRecorder;
    private final ScopedWriteAuthorizer scopedWriteAuthorizer;

    @Value("${smartcampus.ai.context.history-message-limit:20}")
    private int historyMessageLimit;

    public AIAssistantService(
            AIConversationRepository aiConversationRepository,
            AIMessageRepository aiMessageRepository,
            AIConversationRecorder aiConversationRecorder,
            AIContextService aiContextService,
            AIPromptBuilder aiPromptBuilder,
            AIService aiService,
            GroqAIService groqAIService,
            AIRateLimiter aiRateLimiter,
            AIRequestLogRecorder aiRequestLogRecorder,
            ScopedWriteAuthorizer scopedWriteAuthorizer) {
        this.aiConversationRepository = aiConversationRepository;
        this.aiMessageRepository = aiMessageRepository;
        this.aiConversationRecorder = aiConversationRecorder;
        this.aiContextService = aiContextService;
        this.aiPromptBuilder = aiPromptBuilder;
        this.aiService = aiService;
        this.groqAIService = groqAIService;
        this.aiRateLimiter = aiRateLimiter;
        this.aiRequestLogRecorder = aiRequestLogRecorder;
        this.scopedWriteAuthorizer = scopedWriteAuthorizer;
    }

    // ------------------------------------------------------------------
    // Conversations
    // ------------------------------------------------------------------

    public AIConversationDetailResponse createConversation(AIConversationCreateRequest req, User caller) {
        requireStudentCaller(caller);
        aiRateLimiter.checkAllowed(caller);

        AIFeature feature = req.feature() != null ? req.feature() : AIFeature.CHAT;
        StudentAcademicContext ctx = aiContextService.buildFor(caller);
        String freshSystemPrompt = aiPromptBuilder.systemPrompt(ctx);
        String title = resolveDefaultTitle(req.title(), req.message());

        List<AIChatMessage> providerMessages =
                List.of(
                        new AIChatMessage(AIMessageRole.SYSTEM, freshSystemPrompt),
                        new AIChatMessage(AIMessageRole.USER, req.message()));

        AICompletion completion = callProvider(caller, null, feature, providerMessages);

        AIConversation conversation =
                aiConversationRecorder.createConversation(caller, title, feature, completion.model());
        AIMessage systemMessage =
                aiConversationRecorder.append(
                        conversation.getId(), AIMessageRole.SYSTEM, freshSystemPrompt, true, null, null);
        AIMessage userMessage =
                aiConversationRecorder.append(
                        conversation.getId(), AIMessageRole.USER, req.message(), false, null, null);
        AIMessage assistantMessage =
                aiConversationRecorder.append(
                        conversation.getId(),
                        AIMessageRole.ASSISTANT,
                        completion.content(),
                        false,
                        completion.model(),
                        completion);

        aiRequestLogRecorder.record(
                caller, conversation, feature, AIRequestOutcome.SUCCESS, completion.model(),
                completion, null);

        AIConversation refreshed =
                aiConversationRepository
                        .findById(conversation.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Conversation not found."));

        return new AIConversationDetailResponse(
                AIConversationResponse.from(refreshed),
                List.of(
                        AIMessageResponse.from(systemMessage),
                        AIMessageResponse.from(userMessage),
                        AIMessageResponse.from(assistantMessage)));
    }

    public AIChatTurnResponse continueConversation(Long conversationId, AIMessageCreateRequest req, User caller) {
        requireStudentCaller(caller);
        AIConversation conversation =
                aiConversationRepository
                        .findByIdAndUserId(conversationId, caller.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Conversation not found."));
        aiRateLimiter.checkAllowed(caller);

        AIFeature feature = conversation.getFeature();
        StudentAcademicContext ctx = aiContextService.buildFor(caller);
        String freshSystemPrompt = aiPromptBuilder.systemPrompt(ctx);
        String lastSystemPrompt = aiConversationRecorder.lastSystemPrompt(conversationId);
        boolean needsNewSystemMessage = lastSystemPrompt == null || !lastSystemPrompt.equals(freshSystemPrompt);

        List<AIMessage> history = aiMessageRepository.findByConversationIdOrderBySeqNoAsc(conversationId);
        List<AIMessage> nonSystemHistory =
                history.stream().filter(m -> m.getRole() != AIMessageRole.SYSTEM).toList();
        int from = Math.max(0, nonSystemHistory.size() - historyMessageLimit);
        List<AIMessage> limitedHistory = nonSystemHistory.subList(from, nonSystemHistory.size());

        List<AIChatMessage> providerMessages = new ArrayList<>();
        providerMessages.add(new AIChatMessage(AIMessageRole.SYSTEM, freshSystemPrompt));
        for (AIMessage m : limitedHistory) {
            providerMessages.add(new AIChatMessage(m.getRole(), m.getContent()));
        }
        providerMessages.add(new AIChatMessage(AIMessageRole.USER, req.message()));

        AICompletion completion = callProvider(caller, conversation, feature, providerMessages);

        if (needsNewSystemMessage) {
            aiConversationRecorder.append(
                    conversationId, AIMessageRole.SYSTEM, freshSystemPrompt, true, null, null);
        }
        AIMessage userMessage =
                aiConversationRecorder.append(conversationId, AIMessageRole.USER, req.message(), false, null, null);
        AIMessage assistantMessage =
                aiConversationRecorder.append(
                        conversationId,
                        AIMessageRole.ASSISTANT,
                        completion.content(),
                        false,
                        completion.model(),
                        completion);

        aiRequestLogRecorder.record(
                caller, conversation, feature, AIRequestOutcome.SUCCESS, completion.model(),
                completion, null);

        return new AIChatTurnResponse(
                conversationId, AIMessageResponse.from(userMessage), AIMessageResponse.from(assistantMessage));
    }

    public AIConversationResponse rename(Long id, AIConversationRenameRequest req, User caller) {
        requireStudentCaller(caller);
        aiConversationRepository
                .findByIdAndUserId(id, caller.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found."));
        aiConversationRecorder.rename(id, req.title().trim());
        AIConversation refreshed =
                aiConversationRepository
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Conversation not found."));
        return AIConversationResponse.from(refreshed);
    }

    public void delete(Long id, User caller) {
        requireStudentCaller(caller);
        aiConversationRepository
                .findByIdAndUserId(id, caller.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found."));
        aiConversationRecorder.delete(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<AIConversationResponse> list(User caller, AIFeature feature, String q, Pageable pageable) {
        requireStudentCaller(caller);
        Long userId = caller.getId();
        boolean hasQuery = q != null && !q.isBlank();
        Page<AIConversation> page;
        if (feature != null && hasQuery) {
            page =
                    aiConversationRepository.findByUserIdAndFeatureAndTitleContainingIgnoreCase(
                            userId, feature, q.trim(), pageable);
        } else if (feature != null) {
            page = aiConversationRepository.findByUserIdAndFeature(userId, feature, pageable);
        } else if (hasQuery) {
            page = aiConversationRepository.findByUserIdAndTitleContainingIgnoreCase(userId, q.trim(), pageable);
        } else {
            page = aiConversationRepository.findByUserId(userId, pageable);
        }
        return PageResponse.of(page, AIConversationResponse::from);
    }

    @Transactional(readOnly = true)
    public AIConversationDetailResponse history(Long id, User caller) {
        requireStudentCaller(caller);
        AIConversation conversation =
                aiConversationRepository
                        .findByIdAndUserId(id, caller.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Conversation not found."));
        List<AIMessage> messages = aiMessageRepository.findByConversationIdOrderBySeqNoAsc(id);
        return new AIConversationDetailResponse(
                AIConversationResponse.from(conversation), messages.stream().map(AIMessageResponse::from).toList());
    }

    // ------------------------------------------------------------------
    // Single-shot feature endpoints — each opens its own conversation.
    // ------------------------------------------------------------------

    public AIChatTurnResponse explain(AIExplainRequest req, User caller) {
        String title = truncateTitle("Explain: " + req.topic());
        return runFeatureTurn(caller, AIFeature.TOPIC_EXPLANATION, title, aiPromptBuilder.explainInstruction(req));
    }

    public AIChatTurnResponse practiceQuestions(AIPracticeQuestionsRequest req, User caller) {
        String title = truncateTitle("Practice: " + req.topic());
        return runFeatureTurn(
                caller, AIFeature.PRACTICE_QUESTIONS, title, aiPromptBuilder.practiceQuestionsInstruction(req));
    }

    public AIChatTurnResponse mcqs(AIMcqRequest req, User caller) {
        String title = truncateTitle("MCQs: " + req.topic());
        return runFeatureTurn(caller, AIFeature.MCQ, title, aiPromptBuilder.mcqInstruction(req));
    }

    private AIChatTurnResponse runFeatureTurn(User caller, AIFeature feature, String title, String userMessageText) {
        requireStudentCaller(caller);
        aiRateLimiter.checkAllowed(caller);

        StudentAcademicContext ctx = aiContextService.buildFor(caller);
        String freshSystemPrompt = aiPromptBuilder.systemPrompt(ctx);

        List<AIChatMessage> providerMessages =
                List.of(
                        new AIChatMessage(AIMessageRole.SYSTEM, freshSystemPrompt),
                        new AIChatMessage(AIMessageRole.USER, userMessageText));

        AICompletion completion = callProvider(caller, null, feature, providerMessages);

        AIConversation conversation =
                aiConversationRecorder.createConversation(caller, title, feature, completion.model());
        aiConversationRecorder.append(
                conversation.getId(), AIMessageRole.SYSTEM, freshSystemPrompt, true, null, null);
        AIMessage userMessage =
                aiConversationRecorder.append(
                        conversation.getId(), AIMessageRole.USER, userMessageText, false, null, null);
        AIMessage assistantMessage =
                aiConversationRecorder.append(
                        conversation.getId(),
                        AIMessageRole.ASSISTANT,
                        completion.content(),
                        false,
                        completion.model(),
                        completion);

        aiRequestLogRecorder.record(
                caller, conversation, feature, AIRequestOutcome.SUCCESS, completion.model(),
                completion, null);

        return new AIChatTurnResponse(
                conversation.getId(), AIMessageResponse.from(userMessage), AIMessageResponse.from(assistantMessage));
    }

    // ------------------------------------------------------------------
    // Status / models
    // ------------------------------------------------------------------

    /** Any authenticated caller. Never triggers a provider call — reads only cached/configured state. */
    public AIStatusResponse status(User caller) {
        if (caller == null) {
            throw new AccessDeniedException("Authentication is required for this operation.");
        }
        AIRateLimitSnapshot snapshot = aiRateLimiter.snapshot(caller);
        return new AIStatusResponse(
                aiService.isConfigured(),
                groqAIService.getProvider(),
                groqAIService.getBaseUrl(),
                resolveDisplayModel(),
                snapshot.perMinute(),
                snapshot.perDay(),
                snapshot.usedLastMinute(),
                snapshot.usedToday(),
                snapshot.remainingMinute(),
                snapshot.remainingDay());
    }

    private String resolveDisplayModel() {
        String configuredModel = groqAIService.getConfiguredModel();
        if (configuredModel != null && !configuredModel.isBlank()) {
            return configuredModel;
        }
        return groqAIService.getResolvedModelOrNull();
    }

    /** ADMIN only. */
    public List<AIModelResponse> models(User caller) {
        scopedWriteAuthorizer.requireAdmin(caller);
        return aiService.listModels().stream()
                .map(m -> new AIModelResponse(m.id(), m.ownedBy(), m.contextWindow()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void requireStudentCaller(User caller) {
        if (caller == null || caller.getRole() != Role.STUDENT) {
            throw new AccessDeniedException("The AI assistant is available to student accounts only.");
        }
        scopedWriteAuthorizer.requireOwnStudent(caller);
    }

    /**
     * Calls the provider and, on any failure, logs the honest ledger row
     * (NOT_CONFIGURED when {@link AIService#isConfigured()} is false, PROVIDER_ERROR for
     * every other failure) through {@link AIRequestLogRecorder} — which commits in its
     * own {@code REQUIRES_NEW} transaction — before rethrowing. Never persists a
     * conversation or message on this path.
     */
    private AICompletion callProvider(
            User caller, AIConversation conversationOrNull, AIFeature feature, List<AIChatMessage> messages) {
        if (!aiService.isConfigured()) {
            aiRequestLogRecorder.record(
                    caller, conversationOrNull, feature, AIRequestOutcome.NOT_CONFIGURED, null,
                    null, NOT_CONFIGURED_MESSAGE);
            throw new AIUnavailableException(NOT_CONFIGURED_MESSAGE);
        }
        try {
            return aiService.complete(AICompletionRequest.text(messages));
        } catch (AIUnavailableException ex) {
            aiRequestLogRecorder.record(
                    caller, conversationOrNull, feature, AIRequestOutcome.PROVIDER_ERROR, null,
                    null, ex.getMessage());
            throw ex;
        }
    }

    /** First 60 characters of {@code message}, trimmed, with a trailing "…" only if truncated. */
    private String resolveDefaultTitle(String requestedTitle, String message) {
        if (requestedTitle != null && !requestedTitle.isBlank()) {
            return requestedTitle.trim();
        }
        String trimmed = message.trim();
        if (trimmed.length() <= DEFAULT_TITLE_SOURCE_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, DEFAULT_TITLE_SOURCE_LENGTH) + "…";
    }

    private String truncateTitle(String title) {
        return title.length() <= TITLE_MAX_LENGTH ? title : title.substring(0, TITLE_MAX_LENGTH);
    }
}
