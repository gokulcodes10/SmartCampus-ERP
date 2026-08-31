package smartcampus.controller;

import jakarta.validation.Valid;
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
import smartcampus.dto.AIStudyPlanGenerateRequest;
import smartcampus.dto.AIStudyPlanItemRequest;
import smartcampus.dto.AIStudyPlanResponse;
import smartcampus.dto.AIStudyPlanSummaryResponse;
import smartcampus.dto.AIStudyPlanUpdateRequest;
import smartcampus.dto.PageResponse;
import smartcampus.entity.AIStudyPlanStatus;
import smartcampus.entity.AIStudyPlanType;
import smartcampus.entity.User;
import smartcampus.service.AIStudyPlanService;

/**
 * {@code /api/ai/study-plans} — AI-generated study plans and revision schedules, and
 * the student-editable items within them.
 *
 * <p>Method security is not enabled on this build; every route here is STUDENT-only,
 * enforced in {@link AIStudyPlanService} (see its javadoc). A plan is loaded only
 * through the caller's own {@code Student} row — a miss is a 404, never a 403.
 */
@RestController
@RequestMapping("/api/ai/study-plans")
public class AIStudyPlanController {

    private final AIStudyPlanService aiStudyPlanService;

    public AIStudyPlanController(AIStudyPlanService aiStudyPlanService) {
        this.aiStudyPlanService = aiStudyPlanService;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public AIStudyPlanResponse generate(
            @Valid @RequestBody AIStudyPlanGenerateRequest request, @AuthenticationPrincipal User caller) {
        return aiStudyPlanService.generate(request, AIStudyPlanType.STUDY_PLAN, caller);
    }

    @PostMapping("/revision-schedule")
    @ResponseStatus(HttpStatus.CREATED)
    public AIStudyPlanResponse revisionSchedule(
            @Valid @RequestBody AIStudyPlanGenerateRequest request, @AuthenticationPrincipal User caller) {
        return aiStudyPlanService.generate(request, AIStudyPlanType.REVISION_SCHEDULE, caller);
    }

    @GetMapping
    public PageResponse<AIStudyPlanSummaryResponse> list(
            @AuthenticationPrincipal User caller,
            @RequestParam(required = false) AIStudyPlanType planType,
            @RequestParam(required = false) AIStudyPlanStatus status,
            @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return aiStudyPlanService.list(caller, planType, status, pageable);
    }

    @GetMapping("/{id}")
    public AIStudyPlanResponse get(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        return aiStudyPlanService.get(id, caller);
    }

    @PutMapping("/{id}")
    public AIStudyPlanResponse update(
            @PathVariable Long id,
            @Valid @RequestBody AIStudyPlanUpdateRequest request,
            @AuthenticationPrincipal User caller) {
        return aiStudyPlanService.update(id, request, caller);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal User caller) {
        aiStudyPlanService.delete(id, caller);
    }

    @PostMapping("/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public AIStudyPlanResponse addItem(
            @PathVariable Long id,
            @Valid @RequestBody AIStudyPlanItemRequest request,
            @AuthenticationPrincipal User caller) {
        return aiStudyPlanService.addItem(id, request, caller);
    }

    @PutMapping("/{id}/items/{itemId}")
    public AIStudyPlanResponse updateItem(
            @PathVariable Long id,
            @PathVariable Long itemId,
            @Valid @RequestBody AIStudyPlanItemRequest request,
            @AuthenticationPrincipal User caller) {
        return aiStudyPlanService.updateItem(id, itemId, request, caller);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public AIStudyPlanResponse deleteItem(
            @PathVariable Long id, @PathVariable Long itemId, @AuthenticationPrincipal User caller) {
        return aiStudyPlanService.deleteItem(id, itemId, caller);
    }
}
