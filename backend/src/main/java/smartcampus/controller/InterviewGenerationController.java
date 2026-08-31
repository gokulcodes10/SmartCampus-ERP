package smartcampus.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.InterviewGeneratedQuestionsResponse;
import smartcampus.dto.InterviewQuestionGenerateRequest;
import smartcampus.entity.User;
import smartcampus.service.InterviewQuestionGenerationService;

/**
 * AI generation of private interview-practice questions. A SEPARATE {@code
 * @RestController} from {@code InterviewQuestionController}, sharing its class-level
 * base path — a different agent owns the rest of {@code /api/interview-questions}; this
 * class carries exactly the one {@code POST .../generate} route so the two never map the
 * same method + path.
 */
@RestController
@RequestMapping("/api/interview-questions")
public class InterviewGenerationController {

    private final InterviewQuestionGenerationService interviewQuestionGenerationService;

    public InterviewGenerationController(InterviewQuestionGenerationService interviewQuestionGenerationService) {
        this.interviewQuestionGenerationService = interviewQuestionGenerationService;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewGeneratedQuestionsResponse generate(
            @Valid @RequestBody InterviewQuestionGenerateRequest request, @AuthenticationPrincipal User caller) {
        return interviewQuestionGenerationService.generate(request, caller);
    }
}
