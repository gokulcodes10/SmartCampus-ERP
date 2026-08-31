package smartcampus.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.AIStudyPlanGenerateRequest;
import smartcampus.dto.AIStudyPlanItemRequest;
import smartcampus.dto.AIStudyPlanResponse;
import smartcampus.dto.AIStudyPlanSummaryResponse;
import smartcampus.dto.AIStudyPlanUpdateRequest;
import smartcampus.dto.PageResponse;
import smartcampus.entity.AIConversation;
import smartcampus.entity.AIFeature;
import smartcampus.entity.AIMessageRole;
import smartcampus.entity.AIRequestOutcome;
import smartcampus.entity.AIStudyPlan;
import smartcampus.entity.AIStudyPlanItem;
import smartcampus.entity.AIStudyPlanSource;
import smartcampus.entity.AIStudyPlanStatus;
import smartcampus.entity.AIStudyPlanType;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.Subject;
import smartcampus.entity.User;
import smartcampus.exception.AIUnavailableException;
import smartcampus.exception.BadRequestException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.AIStudyPlanItemRepository;
import smartcampus.repository.AIStudyPlanRepository;
import smartcampus.repository.SubjectRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Orchestrates study-plan / revision-schedule generation and the student-editable
 * CRUD around a generated plan and its items.
 *
 * <p><b>{@link #generate} is deliberately NOT {@code @Transactional}</b> — it calls the
 * external AI provider, and only after a real completion is in hand does it persist
 * anything, exactly the same shape as {@code AIAssistantService}'s AI-call methods (see
 * that class's javadoc, and {@code CodingSubmissionService} for the pattern this
 * project first established). No other method here calls the provider, so each of
 * {@link #list}, {@link #get}, {@link #update}, {@link #delete}, {@link #addItem},
 * {@link #updateItem} and {@link #deleteItem} is safely {@code @Transactional} on this
 * bean directly — there is no external call inside any of them to roll back around.
 *
 * <p>Authorization: every method is STUDENT-only via {@link #requireStudentCaller}. A
 * plan is loaded ONLY through {@code aiStudyPlanRepository.findByIdAndStudentId} — a
 * miss is a 404, never a 403.
 */
@Service
public class AIStudyPlanService {

    private static final String NOT_CONFIGURED_MESSAGE = "AI is not configured: set AI_API_KEY in the environment.";
    private static final String INVALID_PLAN_MESSAGE =
            "The AI provider returned a study plan that could not be read.";
    private static final int ITEM_TITLE_MAX_LENGTH = 200;
    private static final int MIN_DURATION_MINUTES = 1;
    private static final int MAX_DURATION_MINUTES = 1440;
    private static final int TITLE_MAX_LENGTH = 150;

    private final AIStudyPlanRepository aiStudyPlanRepository;
    private final AIStudyPlanItemRepository aiStudyPlanItemRepository;
    private final AIConversationRecorder aiConversationRecorder;
    private final AIContextService aiContextService;
    private final AIPromptBuilder aiPromptBuilder;
    private final AIService aiService;
    private final AIRateLimiter aiRateLimiter;
    private final AIRequestLogRecorder aiRequestLogRecorder;
    private final ScopedWriteAuthorizer scopedWriteAuthorizer;
    private final SubjectRepository subjectRepository;
    private final ObjectMapper objectMapper;

    public AIStudyPlanService(
            AIStudyPlanRepository aiStudyPlanRepository,
            AIStudyPlanItemRepository aiStudyPlanItemRepository,
            AIConversationRecorder aiConversationRecorder,
            AIContextService aiContextService,
            AIPromptBuilder aiPromptBuilder,
            AIService aiService,
            AIRateLimiter aiRateLimiter,
            AIRequestLogRecorder aiRequestLogRecorder,
            ScopedWriteAuthorizer scopedWriteAuthorizer,
            SubjectRepository subjectRepository,
            ObjectMapper objectMapper) {
        this.aiStudyPlanRepository = aiStudyPlanRepository;
        this.aiStudyPlanItemRepository = aiStudyPlanItemRepository;
        this.aiConversationRecorder = aiConversationRecorder;
        this.aiContextService = aiContextService;
        this.aiPromptBuilder = aiPromptBuilder;
        this.aiService = aiService;
        this.aiRateLimiter = aiRateLimiter;
        this.aiRequestLogRecorder = aiRequestLogRecorder;
        this.scopedWriteAuthorizer = scopedWriteAuthorizer;
        this.subjectRepository = subjectRepository;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // Generation
    // ------------------------------------------------------------------

    public AIStudyPlanResponse generate(AIStudyPlanGenerateRequest req, AIStudyPlanType type, User caller) {
        Student student = requireStudentCaller(caller);
        if (req.endDate().isBefore(req.startDate())) {
            throw new BadRequestException("endDate must not be before startDate.");
        }
        aiRateLimiter.checkAllowed(caller);

        AIFeature feature =
                type == AIStudyPlanType.REVISION_SCHEDULE ? AIFeature.REVISION_SCHEDULE : AIFeature.STUDY_PLAN;
        StudentAcademicContext ctx = aiContextService.buildFor(caller);
        String freshSystemPrompt = aiPromptBuilder.systemPrompt(ctx);
        String instruction = aiPromptBuilder.studyPlanInstruction(ctx, req, type);

        List<AIChatMessage> providerMessages =
                List.of(
                        new AIChatMessage(AIMessageRole.SYSTEM, freshSystemPrompt),
                        new AIChatMessage(AIMessageRole.USER, instruction));

        AICompletion completion = callProvider(caller, feature, providerMessages);

        ParsedPlan parsed;
        try {
            parsed = parsePlan(completion.content(), req, type);
        } catch (InvalidPlanException ex) {
            aiRequestLogRecorder.record(
                    caller, null, feature, AIRequestOutcome.INVALID_RESPONSE, completion.model(), completion,
                    ex.getMessage());
            throw new AIUnavailableException(ex.getMessage());
        }

        String conversationTitle = conversationTitleFor(type, req);
        AIConversation conversation =
                aiConversationRecorder.createConversation(caller, conversationTitle, feature, completion.model());
        aiConversationRecorder.append(
                conversation.getId(), AIMessageRole.SYSTEM, freshSystemPrompt, true, null, null);
        aiConversationRecorder.append(conversation.getId(), AIMessageRole.USER, instruction, false, null, null);
        aiConversationRecorder.append(
                conversation.getId(),
                AIMessageRole.ASSISTANT,
                completion.content(),
                false,
                completion.model(),
                completion);

        AIStudyPlan plan =
                AIStudyPlan.builder()
                        .student(student)
                        .conversation(conversation)
                        .planType(type)
                        .title(parsed.title())
                        .goal(parsed.goal())
                        .startDate(req.startDate())
                        .endDate(req.endDate())
                        .status(AIStudyPlanStatus.ACTIVE)
                        .source(AIStudyPlanSource.AI_GENERATED)
                        .model(completion.model())
                        .edited(false)
                        .build();
        plan = aiStudyPlanRepository.save(plan);

        List<AIStudyPlanItem> items = new ArrayList<>(parsed.items().size());
        for (int i = 0; i < parsed.items().size(); i++) {
            ParsedItem parsedItem = parsed.items().get(i);
            items.add(
                    AIStudyPlanItem.builder()
                            .studyPlan(plan)
                            .subject(parsedItem.subject())
                            .subjectLabel(parsedItem.subjectLabel())
                            .position(i)
                            .scheduledDate(parsedItem.scheduledDate())
                            .title(parsedItem.title())
                            .description(parsedItem.description())
                            .durationMinutes(parsedItem.durationMinutes())
                            .completed(false)
                            .build());
        }
        items = aiStudyPlanItemRepository.saveAll(items);

        aiRequestLogRecorder.record(
                caller, conversation, feature, AIRequestOutcome.SUCCESS, completion.model(), completion, null);

        return AIStudyPlanResponse.from(plan, items);
    }

    // ------------------------------------------------------------------
    // Reads / edits — no external call, safely @Transactional directly.
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<AIStudyPlanSummaryResponse> list(
            User caller, AIStudyPlanType type, AIStudyPlanStatus status, Pageable pageable) {
        Student student = requireStudentCaller(caller);
        Page<AIStudyPlan> page;
        if (type != null && status != null) {
            page = aiStudyPlanRepository.findByStudentIdAndPlanTypeAndStatus(student.getId(), type, status, pageable);
        } else if (type != null) {
            page = aiStudyPlanRepository.findByStudentIdAndPlanType(student.getId(), type, pageable);
        } else if (status != null) {
            page = aiStudyPlanRepository.findByStudentIdAndStatus(student.getId(), status, pageable);
        } else {
            page = aiStudyPlanRepository.findByStudentId(student.getId(), pageable);
        }
        return PageResponse.of(page, this::toSummary);
    }

    @Transactional(readOnly = true)
    public AIStudyPlanResponse get(Long id, User caller) {
        Student student = requireStudentCaller(caller);
        AIStudyPlan plan =
                aiStudyPlanRepository
                        .findByIdAndStudentId(id, student.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Study plan not found."));
        List<AIStudyPlanItem> items = aiStudyPlanItemRepository.findByStudyPlanIdOrderByPositionAsc(id);
        return AIStudyPlanResponse.from(plan, items);
    }

    /** A successful PUT on an AI_GENERATED plan sets {@code edited = true}. */
    @Transactional
    public AIStudyPlanResponse update(Long id, AIStudyPlanUpdateRequest req, User caller) {
        Student student = requireStudentCaller(caller);
        AIStudyPlan plan =
                aiStudyPlanRepository
                        .findByIdAndStudentId(id, student.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Study plan not found."));
        if (req.endDate().isBefore(req.startDate())) {
            throw new BadRequestException("endDate must not be before startDate.");
        }

        plan.setTitle(req.title().trim());
        plan.setGoal(req.goal());
        plan.setStartDate(req.startDate());
        plan.setEndDate(req.endDate());
        plan.setStatus(req.status());
        if (plan.getSource() == AIStudyPlanSource.AI_GENERATED) {
            plan.setEdited(true);
        }
        plan = aiStudyPlanRepository.save(plan);

        List<AIStudyPlanItem> items = aiStudyPlanItemRepository.findByStudyPlanIdOrderByPositionAsc(id);
        return AIStudyPlanResponse.from(plan, items);
    }

    @Transactional
    public void delete(Long id, User caller) {
        Student student = requireStudentCaller(caller);
        AIStudyPlan plan =
                aiStudyPlanRepository
                        .findByIdAndStudentId(id, student.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Study plan not found."));
        aiStudyPlanRepository.delete(plan);
    }

    /**
     * Adding a new item does not itself count as editing the AI's generated content
     * (§10: only a successful PUT on the plan or an item sets {@code edited = true}), so
     * {@code source}/{@code edited} are left untouched here.
     */
    @Transactional
    public AIStudyPlanResponse addItem(Long planId, AIStudyPlanItemRequest req, User caller) {
        Student student = requireStudentCaller(caller);
        AIStudyPlan plan =
                aiStudyPlanRepository
                        .findByIdAndStudentId(planId, student.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Study plan not found."));

        Subject subject = resolveSubject(req.subjectId());
        int position = req.position() != null ? req.position() : aiStudyPlanItemRepository.findMaxPosition(planId) + 1;
        boolean completed = req.completed() != null && req.completed();

        AIStudyPlanItem item =
                AIStudyPlanItem.builder()
                        .studyPlan(plan)
                        .subject(subject)
                        .subjectLabel(req.subjectLabel())
                        .position(position)
                        .scheduledDate(req.scheduledDate())
                        .title(req.title().trim())
                        .description(req.description())
                        .durationMinutes(req.durationMinutes())
                        .completed(completed)
                        .completedAt(completed ? LocalDateTime.now() : null)
                        .build();
        aiStudyPlanItemRepository.save(item);

        List<AIStudyPlanItem> items = aiStudyPlanItemRepository.findByStudyPlanIdOrderByPositionAsc(planId);
        return AIStudyPlanResponse.from(plan, items);
    }

    /** A successful PUT on an item of an AI_GENERATED plan sets the plan's {@code edited = true}. */
    @Transactional
    public AIStudyPlanResponse updateItem(Long planId, Long itemId, AIStudyPlanItemRequest req, User caller) {
        Student student = requireStudentCaller(caller);
        AIStudyPlan plan =
                aiStudyPlanRepository
                        .findByIdAndStudentId(planId, student.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Study plan not found."));
        AIStudyPlanItem item =
                aiStudyPlanItemRepository
                        .findByIdAndStudyPlanId(itemId, planId)
                        .orElseThrow(() -> new ResourceNotFoundException("Study plan item not found."));

        item.setSubject(resolveSubject(req.subjectId()));
        item.setSubjectLabel(req.subjectLabel());
        item.setScheduledDate(req.scheduledDate());
        item.setTitle(req.title().trim());
        item.setDescription(req.description());
        item.setDurationMinutes(req.durationMinutes());
        if (req.position() != null) {
            item.setPosition(req.position());
        }
        if (req.completed() != null) {
            boolean completed = req.completed();
            item.setCompleted(completed);
            item.setCompletedAt(completed ? LocalDateTime.now() : null);
        }
        aiStudyPlanItemRepository.save(item);

        if (plan.getSource() == AIStudyPlanSource.AI_GENERATED) {
            plan.setEdited(true);
            aiStudyPlanRepository.save(plan);
        }

        List<AIStudyPlanItem> items = aiStudyPlanItemRepository.findByStudyPlanIdOrderByPositionAsc(planId);
        return AIStudyPlanResponse.from(plan, items);
    }

    @Transactional
    public AIStudyPlanResponse deleteItem(Long planId, Long itemId, User caller) {
        Student student = requireStudentCaller(caller);
        AIStudyPlan plan =
                aiStudyPlanRepository
                        .findByIdAndStudentId(planId, student.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Study plan not found."));
        AIStudyPlanItem item =
                aiStudyPlanItemRepository
                        .findByIdAndStudyPlanId(itemId, planId)
                        .orElseThrow(() -> new ResourceNotFoundException("Study plan item not found."));
        aiStudyPlanItemRepository.delete(item);

        List<AIStudyPlanItem> items = aiStudyPlanItemRepository.findByStudyPlanIdOrderByPositionAsc(planId);
        return AIStudyPlanResponse.from(plan, items);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Student requireStudentCaller(User caller) {
        if (caller == null || caller.getRole() != Role.STUDENT) {
            throw new AccessDeniedException("The AI assistant is available to student accounts only.");
        }
        return scopedWriteAuthorizer.requireOwnStudent(caller);
    }

    private Subject resolveSubject(Long subjectId) {
        if (subjectId == null) {
            return null;
        }
        return subjectRepository.findById(subjectId).orElse(null);
    }

    private String conversationTitleFor(AIStudyPlanType type, AIStudyPlanGenerateRequest req) {
        String prefix = type == AIStudyPlanType.REVISION_SCHEDULE ? "Revision schedule: " : "Study plan: ";
        String combined = prefix + req.startDate() + "–" + req.endDate();
        return combined.length() <= TITLE_MAX_LENGTH ? combined : combined.substring(0, TITLE_MAX_LENGTH);
    }

    private AICompletion callProvider(User caller, AIFeature feature, List<AIChatMessage> messages) {
        if (!aiService.isConfigured()) {
            aiRequestLogRecorder.record(
                    caller, null, feature, AIRequestOutcome.NOT_CONFIGURED, null, null, NOT_CONFIGURED_MESSAGE);
            throw new AIUnavailableException(NOT_CONFIGURED_MESSAGE);
        }
        try {
            return aiService.complete(AICompletionRequest.json(messages));
        } catch (AIUnavailableException ex) {
            aiRequestLogRecorder.record(
                    caller, null, feature, AIRequestOutcome.PROVIDER_ERROR, null, null, ex.getMessage());
            throw ex;
        }
    }

    // ------------------------------------------------------------------
    // §10 JSON parsing — no fabrication, no clamping of model content.
    // ------------------------------------------------------------------

    private record ParsedItem(
            LocalDate scheduledDate,
            Subject subject,
            String subjectLabel,
            String title,
            String description,
            Integer durationMinutes) {}

    private record ParsedPlan(String title, String goal, List<ParsedItem> items) {}

    private static final class InvalidPlanException extends RuntimeException {
        InvalidPlanException(String message) {
            super(message);
        }
    }

    private ParsedPlan parsePlan(String content, AIStudyPlanGenerateRequest req, AIStudyPlanType type) {
        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (RuntimeException ex) {
            throw new InvalidPlanException(INVALID_PLAN_MESSAGE);
        }
        if (root == null || !root.isObject()) {
            throw new InvalidPlanException(INVALID_PLAN_MESSAGE);
        }

        JsonNode itemsNode = root.get("items");
        if (itemsNode == null || !itemsNode.isArray() || itemsNode.size() == 0) {
            throw new InvalidPlanException(INVALID_PLAN_MESSAGE);
        }

        List<ParsedItem> parsedItems = new ArrayList<>();
        for (JsonNode itemNode : itemsNode) {
            ParsedItem parsedItem = parseItem(itemNode, req);
            if (parsedItem != null) {
                parsedItems.add(parsedItem);
            }
        }
        if (parsedItems.isEmpty()) {
            throw new InvalidPlanException(INVALID_PLAN_MESSAGE);
        }
        // Stable sort: ties keep the model's original relative order.
        parsedItems.sort(Comparator.comparing(ParsedItem::scheduledDate));

        String title = textOrNull(root.get("title"));
        if (title != null) {
            title = title.trim();
        }
        if (title == null || title.isEmpty()) {
            title = conversationTitleFor(type, req);
        } else if (title.length() > TITLE_MAX_LENGTH) {
            title = title.substring(0, TITLE_MAX_LENGTH);
        }

        String goal = textOrNull(root.get("goal"));
        if (goal != null) {
            goal = goal.trim();
            if (goal.isEmpty()) {
                goal = null;
            } else if (goal.length() > 500) {
                goal = goal.substring(0, 500);
            }
        }

        return new ParsedPlan(title, goal, parsedItems);
    }

    /** Returns {@code null} — meaning "drop this item" — for any item that fails a §10 parsing rule. */
    private ParsedItem parseItem(JsonNode itemNode, AIStudyPlanGenerateRequest req) {
        if (itemNode == null || !itemNode.isObject()) {
            return null;
        }

        String dateText = textOrNull(itemNode.get("scheduledDate"));
        LocalDate scheduledDate;
        try {
            scheduledDate = dateText != null ? LocalDate.parse(dateText.trim()) : null;
        } catch (DateTimeParseException ex) {
            scheduledDate = null;
        }
        if (scheduledDate == null || scheduledDate.isBefore(req.startDate()) || scheduledDate.isAfter(req.endDate())) {
            // Unparseable or out-of-range: dropped, never shifted to fit.
            return null;
        }

        String itemTitle = textOrNull(itemNode.get("title"));
        if (itemTitle == null) {
            return null;
        }
        itemTitle = itemTitle.trim();
        if (itemTitle.isEmpty()) {
            return null;
        }
        if (itemTitle.length() > ITEM_TITLE_MAX_LENGTH) {
            itemTitle = itemTitle.substring(0, ITEM_TITLE_MAX_LENGTH);
        }

        String subjectCode = textOrNull(itemNode.get("subjectCode"));
        Subject subject = null;
        if (subjectCode != null && !subjectCode.isBlank()) {
            subject = subjectRepository.findByCode(subjectCode.trim()).orElse(null);
        }
        String subjectLabel = textOrNull(itemNode.get("subjectLabel"));

        String description = textOrNull(itemNode.get("description"));

        Integer durationMinutes = null;
        JsonNode durationNode = itemNode.get("durationMinutes");
        if (durationNode != null && durationNode.isNumber()) {
            int candidate = durationNode.asInt();
            if (candidate >= MIN_DURATION_MINUTES && candidate <= MAX_DURATION_MINUTES) {
                durationMinutes = candidate;
            }
            // Out of range: stored as NULL, never rewritten to a plausible number.
        }

        return new ParsedItem(scheduledDate, subject, subjectLabel, itemTitle, description, durationMinutes);
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isString()) {
            return null;
        }
        return node.asString();
    }

    private AIStudyPlanSummaryResponse toSummary(AIStudyPlan plan) {
        long itemCount = aiStudyPlanItemRepository.countByStudyPlanId(plan.getId());
        long completedItemCount = aiStudyPlanItemRepository.countByStudyPlanIdAndCompletedTrue(plan.getId());
        return new AIStudyPlanSummaryResponse(
                plan.getId(),
                plan.getConversation() != null ? plan.getConversation().getId() : null,
                plan.getPlanType(),
                plan.getTitle(),
                plan.getGoal(),
                plan.getStartDate(),
                plan.getEndDate(),
                plan.getStatus(),
                plan.getSource(),
                plan.getModel(),
                plan.isEdited(),
                itemCount,
                completedItemCount,
                plan.getCreatedAt(),
                plan.getUpdatedAt());
    }
}
