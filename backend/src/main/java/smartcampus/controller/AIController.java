package smartcampus.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.AIChatTurnResponse;
import smartcampus.dto.AIConversationCreateRequest;
import smartcampus.dto.AIConversationDetailResponse;
import smartcampus.dto.AIConversationRenameRequest;
import smartcampus.dto.AIConversationResponse;
import smartcampus.dto.AIExplainRequest;
import smartcampus.dto.AIMcqRequest;
import smartcampus.dto.AIMessageCreateRequest;
import smartcampus.dto.AIModelResponse;
import smartcampus.dto.AIPracticeQuestionsRequest;
import smartcampus.dto.AIStatusResponse;
import smartcampus.dto.AIStudentContextResponse;
import smartcampus.dto.PageResponse;
import smartcampus.entity.AIFeature;
import smartcampus.entity.User;
import smartcampus.service.AIAssistantService;
import smartcampus.service.AIContextService;

/**
 * {@code /api/ai} — the AI assistant: conversations, the three single-shot generation
 * endpoints, the live status snapshot and the student's grounding context.
 *
 * <p>Method security is not enabled on this build; role/ownership enforcement lives in
 * {@link AIAssistantService} / {@link AIContextService} (see their javadoc): {@code
 * /status} is open to any authenticated caller, {@code /models} is ADMIN-only via
 * {@code ScopedWriteAuthorizer.requireAdmin}, and every other route here is
 * STUDENT-only, with a conversation loaded only through the caller's own id — a miss
 * is a 404, never a 403.
 */
@RestController
@RequestMapping("/api/ai")
public class AIController {

    private final AIAssistantService aiAssistantService;
    private final AIContextService aiContextService;

    public AIController(AIAssistantService aiAssistantService, AIContextService aiContextService) {
        this.aiAssistantService = aiAssistantService;
        this.aiContextService = aiContextService;
    }

    /** Any authenticated role. Never triggers a provider call. */
    @GetMapping("/status")
    public AIStatusResponse status(@AuthenticationPrincipal User caller) {
        return aiAssistantService.status(caller);
    }

    /** ADMIN only — the live provider model list. */
    @GetMapping("/models")
    public List<AIModelResponse> models(@AuthenticationPrincipal User caller) {
        return aiAssistantService.models(caller);
    }

    /** STUDENT only — the same real academic record the assistant grounds itself in. */
    @GetMapping("/context")
    public AIStudentContextResponse context(@AuthenticationPrincipal User caller) {
        return aiContextService.snapshotFor(caller);
    }

    @GetMapping("/conversations")
    public PageResponse<AIConversationResponse> listConversations(
            @AuthenticationPrincipal User caller,
            @RequestParam(required = false) AIFeature feature,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "lastMessageAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return aiAssistantService.list(caller, feature, q, pageable);
    }

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public AIConversationDetailResponse createConversation(
            @Valid @RequestBody AIConversationCreateRequest request, @AuthenticationPrincipal User caller) {
        return aiAssistantService.createConversation(request, caller);
    }

    @GetMapping("/conversations/{id}")
    public AIConversationDetailResponse history(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        return aiAssistantService.history(id, caller);
    }

    @PutMapping("/conversations/{id}")
    public AIConversationResponse rename(
            @PathVariable Long id,
            @Valid @RequestBody AIConversationRenameRequest request,
            @AuthenticationPrincipal User caller) {
        return aiAssistantService.rename(id, request, caller);
    }

    @DeleteMapping("/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConversation(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        aiAssistantService.delete(id, caller);
    }

    @PostMapping("/conversations/{id}/messages")
    public AIChatTurnResponse continueConversation(
            @PathVariable Long id,
            @Valid @RequestBody AIMessageCreateRequest request,
            @AuthenticationPrincipal User caller) {
        return aiAssistantService.continueConversation(id, request, caller);
    }

    @PostMapping("/explain")
    public AIChatTurnResponse explain(
            @Valid @RequestBody AIExplainRequest request, @AuthenticationPrincipal User caller) {
        return aiAssistantService.explain(request, caller);
    }

    @PostMapping("/practice-questions")
    public AIChatTurnResponse practiceQuestions(
            @Valid @RequestBody AIPracticeQuestionsRequest request, @AuthenticationPrincipal User caller) {
        return aiAssistantService.practiceQuestions(request, caller);
    }

    @PostMapping("/mcqs")
    public AIChatTurnResponse mcqs(@Valid @RequestBody AIMcqRequest request, @AuthenticationPrincipal User caller) {
        return aiAssistantService.mcqs(request, caller);
    }
}
