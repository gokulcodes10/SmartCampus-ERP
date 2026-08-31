package smartcampus.service;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.InterviewCategoryProgressResponse;
import smartcampus.dto.InterviewProgressSummaryResponse;
import smartcampus.dto.InterviewQuestionCreateRequest;
import smartcampus.dto.InterviewQuestionProgressRequest;
import smartcampus.dto.InterviewQuestionResponse;
import smartcampus.dto.InterviewQuestionUpdateRequest;
import smartcampus.dto.PageResponse;
import smartcampus.entity.InterviewDifficulty;
import smartcampus.entity.InterviewQuestion;
import smartcampus.entity.InterviewQuestionCategory;
import smartcampus.entity.InterviewQuestionProgress;
import smartcampus.entity.InterviewQuestionSource;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.User;
import smartcampus.exception.BadRequestException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.InterviewQuestionProgressRepository;
import smartcampus.repository.InterviewQuestionRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.projection.InterviewCategoryCount;

/**
 * Business logic behind {@code /api/interview-questions} — the interview question bank
 * (§38): visibility between the global (curated) bank and a student's own private
 * AI-generated rows, admin authoring, and per-student completion/bookmark progress.
 *
 * <p>Authorization is enforced here, not by route rules — method security ({@code
 * @PreAuthorize}) is not enabled on this build. {@link
 * smartcampus.service.ScopedWriteAuthorizer} is not used here: its {@code
 * requireScopedWrite} is the Phase 4 academic-write gate (subject/section tuples), which
 * has no bearing on this module, and this service needs plain role checks plus "load the
 * caller's own {@link Student} row", done directly against {@link StudentRepository}.
 *
 * <p>VISIBILITY, enforced in the {@link Specification} on every list/read, never
 * optional: a STUDENT sees a question iff it is global ({@code ownerStudent IS NULL}) or
 * privately owned by them; FACULTY and ADMIN see only global questions. A non-visible id
 * is {@link ResourceNotFoundException} (404), never {@link AccessDeniedException} (403)
 * — an id must not be probeable to distinguish "not yours" from "does not exist" (the
 * project-wide R8 convention, see {@code CodingProblemService}/{@code
 * AIAssistantService}).
 */
@Service
@RequiredArgsConstructor
public class InterviewQuestionService {

    private final InterviewQuestionRepository interviewQuestionRepository;
    private final InterviewQuestionProgressRepository interviewQuestionProgressRepository;
    private final StudentRepository studentRepository;

    // ---------------------------------------------------------------------------
    // Read
    // ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<InterviewQuestionResponse> list(
            User caller,
            InterviewQuestionCategory category,
            InterviewDifficulty difficulty,
            String companyName,
            InterviewQuestionSource source,
            String q,
            Boolean bookmarked,
            Boolean completed,
            Boolean mine,
            Pageable pageable) {
        boolean isStudent = caller.getRole() == Role.STUDENT;
        Long studentId = isStudent ? studentIdOrNull(caller) : null;

        Specification<InterviewQuestion> spec =
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();

                    if (isStudent && studentId != null) {
                        if (Boolean.TRUE.equals(mine)) {
                            predicates.add(cb.equal(root.get("ownerStudent").get("id"), studentId));
                        } else {
                            predicates.add(
                                    cb.or(
                                            cb.isNull(root.get("ownerStudent")),
                                            cb.equal(root.get("ownerStudent").get("id"), studentId)));
                        }
                    } else {
                        // FACULTY / ADMIN, and a STUDENT caller with no student profile yet:
                        // the global bank only.
                        predicates.add(cb.isNull(root.get("ownerStudent")));
                    }

                    if (category != null) {
                        predicates.add(cb.equal(root.get("category"), category));
                    }
                    if (difficulty != null) {
                        predicates.add(cb.equal(root.get("difficulty"), difficulty));
                    }
                    if (companyName != null && !companyName.isBlank()) {
                        predicates.add(
                                cb.like(cb.lower(root.get("companyName")), "%" + companyName.toLowerCase() + "%"));
                    }
                    if (source != null) {
                        predicates.add(cb.equal(root.get("source"), source));
                    }
                    if (q != null && !q.isBlank()) {
                        predicates.add(cb.like(cb.lower(root.get("question")), "%" + q.toLowerCase() + "%"));
                    }

                    // bookmarked/completed are STUDENT-only filters (ignored for anyone else,
                    // per contract) and are expressed as an EXISTS-style subquery over the
                    // caller's own progress rows.
                    if (isStudent && studentId != null) {
                        if (bookmarked != null) {
                            predicates.add(root.get("id").in(progressQuestionIdsSubquery(
                                    query, cb, studentId, "bookmarked", bookmarked)));
                        }
                        if (completed != null) {
                            predicates.add(root.get("id").in(progressQuestionIdsSubquery(
                                    query, cb, studentId, "completed", completed)));
                        }
                    }

                    return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
                };

        Page<InterviewQuestion> page = interviewQuestionRepository.findAll(spec, pageable);

        Map<Long, InterviewQuestionProgress> progressByQuestionId = new HashMap<>();
        if (isStudent && studentId != null && !page.getContent().isEmpty()) {
            List<Long> ids = page.getContent().stream().map(InterviewQuestion::getId).toList();
            for (InterviewQuestionProgress p :
                    interviewQuestionProgressRepository.findByStudentIdAndQuestionIdIn(studentId, ids)) {
                progressByQuestionId.put(p.getQuestion().getId(), p);
            }
        }

        return PageResponse.of(
                page, question -> InterviewQuestionResponse.from(question, progressByQuestionId.get(question.getId())));
    }

    private Subquery<Long> progressQuestionIdsSubquery(
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            Long studentId,
            String flagField,
            boolean flagValue) {
        Subquery<Long> subquery = query.subquery(Long.class);
        var progressRoot = subquery.from(InterviewQuestionProgress.class);
        subquery.select(progressRoot.get("question").get("id"));
        subquery.where(
                cb.and(
                        cb.equal(progressRoot.get("student").get("id"), studentId),
                        cb.equal(progressRoot.get(flagField), flagValue)));
        return subquery;
    }

    @Transactional(readOnly = true)
    public InterviewProgressSummaryResponse progressSummary(User caller) {
        Student student = requireStudent(caller);
        Long studentId = student.getId();

        long totalQuestions = interviewQuestionRepository.countVisible(studentId);
        long completed = interviewQuestionProgressRepository.countByStudentIdAndCompletedTrue(studentId);
        long bookmarked = interviewQuestionProgressRepository.countByStudentIdAndBookmarkedTrue(studentId);
        long notStarted = totalQuestions - completed;

        Map<InterviewQuestionCategory, Long> totalByCategory = new HashMap<>();
        for (InterviewCategoryCount row : interviewQuestionRepository.countVisibleByCategory(studentId)) {
            totalByCategory.put(row.getCategory(), row.getTotal());
        }
        Map<InterviewQuestionCategory, Long> completedByCategory = new HashMap<>();
        for (InterviewCategoryCount row :
                interviewQuestionProgressRepository.countCompletedByCategory(studentId)) {
            completedByCategory.put(row.getCategory(), row.getTotal());
        }

        List<InterviewCategoryProgressResponse> byCategory =
                Arrays.stream(InterviewQuestionCategory.values())
                        .map(
                                c ->
                                        new InterviewCategoryProgressResponse(
                                                c,
                                                totalByCategory.getOrDefault(c, 0L),
                                                completedByCategory.getOrDefault(c, 0L)))
                        .toList();

        return new InterviewProgressSummaryResponse(totalQuestions, completed, bookmarked, notStarted, byCategory);
    }

    @Transactional(readOnly = true)
    public InterviewQuestionResponse getById(Long id, User caller) {
        InterviewQuestion question = loadVisibleOrThrow(id, caller);
        InterviewQuestionProgress progress = findOwnProgressOrNull(question, caller);
        return InterviewQuestionResponse.from(question, progress);
    }

    // ---------------------------------------------------------------------------
    // Write (ADMIN)
    // ---------------------------------------------------------------------------

    @Transactional
    public InterviewQuestionResponse create(InterviewQuestionCreateRequest request, User caller) {
        requireAdmin(caller);
        validateCompanySpecific(request.category(), request.companyName());

        InterviewQuestion question =
                InterviewQuestion.builder()
                        .category(request.category())
                        .difficulty(request.difficulty() != null ? request.difficulty() : InterviewDifficulty.MEDIUM)
                        .question(request.question())
                        .answer(request.answer())
                        .explanation(request.explanation())
                        .companyName(request.companyName())
                        .tags(request.tags())
                        .source(InterviewQuestionSource.CURATED)
                        .model(null)
                        .ownerStudent(null)
                        .createdBy(caller)
                        .build();
        question = interviewQuestionRepository.save(question);
        return InterviewQuestionResponse.from(question, null);
    }

    @Transactional
    public InterviewQuestionResponse update(Long id, InterviewQuestionUpdateRequest request, User caller) {
        requireAdmin(caller);
        validateCompanySpecific(request.category(), request.companyName());

        InterviewQuestion question =
                interviewQuestionRepository
                        .findByIdAndOwnerStudentIsNull(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Interview question not found."));

        question.setCategory(request.category());
        question.setDifficulty(request.difficulty() != null ? request.difficulty() : InterviewDifficulty.MEDIUM);
        question.setQuestion(request.question());
        question.setAnswer(request.answer());
        question.setExplanation(request.explanation());
        question.setCompanyName(request.companyName());
        question.setTags(request.tags());

        question = interviewQuestionRepository.save(question);
        return InterviewQuestionResponse.from(question, null);
    }

    @Transactional
    public void delete(Long id, User caller) {
        switch (caller.getRole()) {
            case ADMIN -> {
                InterviewQuestion question =
                        interviewQuestionRepository
                                .findByIdAndOwnerStudentIsNull(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Interview question not found."));
                interviewQuestionRepository.delete(question);
            }
            case STUDENT -> {
                Student student = requireStudent(caller);
                InterviewQuestion question =
                        interviewQuestionRepository
                                .findByIdAndOwnerStudentId(id, student.getId())
                                .orElseThrow(() -> new ResourceNotFoundException("Interview question not found."));
                interviewQuestionRepository.delete(question);
            }
            default ->
                    throw new AccessDeniedException(
                            "Faculty accounts cannot delete interview questions.");
        }
    }

    // ---------------------------------------------------------------------------
    // Progress (STUDENT)
    // ---------------------------------------------------------------------------

    @Transactional
    public InterviewQuestionResponse upsertProgress(
            Long questionId, InterviewQuestionProgressRequest request, User caller) {
        Student student = requireStudent(caller);
        InterviewQuestion question = loadVisibleOrThrow(questionId, caller);

        InterviewQuestionProgress progress =
                interviewQuestionProgressRepository
                        .findByStudentIdAndQuestionId(student.getId(), question.getId())
                        .orElseGet(
                                () ->
                                        InterviewQuestionProgress.builder()
                                                .student(student)
                                                .question(question)
                                                .completed(false)
                                                .bookmarked(false)
                                                .completedAt(null)
                                                .build());

        if (request.completed() != null) {
            progress.setCompleted(request.completed());
            progress.setCompletedAt(request.completed() ? LocalDateTime.now() : null);
        }
        if (request.bookmarked() != null) {
            progress.setBookmarked(request.bookmarked());
        }

        progress = interviewQuestionProgressRepository.save(progress);
        return InterviewQuestionResponse.from(question, progress);
    }

    // ---------------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------------

    private InterviewQuestion loadVisibleOrThrow(Long id, User caller) {
        InterviewQuestion question =
                interviewQuestionRepository
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Interview question not found."));
        if (!isVisible(question, caller)) {
            throw new ResourceNotFoundException("Interview question not found.");
        }
        return question;
    }

    private boolean isVisible(InterviewQuestion question, User caller) {
        if (question.getOwnerStudent() == null) {
            return true;
        }
        if (caller.getRole() != Role.STUDENT) {
            return false;
        }
        Long studentId = studentIdOrNull(caller);
        return studentId != null && studentId.equals(question.getOwnerStudent().getId());
    }

    private InterviewQuestionProgress findOwnProgressOrNull(InterviewQuestion question, User caller) {
        if (caller.getRole() != Role.STUDENT) {
            return null;
        }
        Long studentId = studentIdOrNull(caller);
        if (studentId == null) {
            return null;
        }
        return interviewQuestionProgressRepository
                .findByStudentIdAndQuestionId(studentId, question.getId())
                .orElse(null);
    }

    private Long studentIdOrNull(User caller) {
        return studentRepository.findByUserId(caller.getId()).map(Student::getId).orElse(null);
    }

    private Student requireStudent(User caller) {
        if (caller == null || caller.getRole() != Role.STUDENT) {
            throw new AccessDeniedException("This operation is restricted to student accounts.");
        }
        return studentRepository
                .findByUserId(caller.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No student profile exists for this account."));
    }

    private void requireAdmin(User caller) {
        if (caller == null || caller.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("This operation requires an ADMIN account.");
        }
    }

    /**
     * A {@code COMPANY_SPECIFIC} question with a blank company name is {@link
     * BadRequestException} (400) here, BEFORE it can ever reach {@code
     * chk_interview_questions_company_specific_has_company} and surface as a
     * 500-shaped {@code DataIntegrityViolationException}.
     */
    private void validateCompanySpecific(InterviewQuestionCategory category, String companyName) {
        if (category == InterviewQuestionCategory.COMPANY_SPECIFIC
                && (companyName == null || companyName.isBlank())) {
            throw new BadRequestException("A company-specific question must name a company.");
        }
    }
}
