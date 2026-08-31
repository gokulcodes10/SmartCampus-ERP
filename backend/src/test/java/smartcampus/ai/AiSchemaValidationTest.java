package smartcampus.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import smartcampus.TestcontainersConfiguration;
import smartcampus.entity.AIConversation;
import smartcampus.entity.AIFeature;
import smartcampus.entity.AIMessage;
import smartcampus.entity.AIMessageRole;
import smartcampus.entity.AIRequestLog;
import smartcampus.entity.AIRequestOutcome;
import smartcampus.entity.AIStudyPlan;
import smartcampus.entity.AIStudyPlanItem;
import smartcampus.entity.AIStudyPlanSource;
import smartcampus.entity.AIStudyPlanStatus;
import smartcampus.entity.AIStudyPlanType;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.User;
import smartcampus.repository.AIConversationRepository;
import smartcampus.repository.AIMessageRepository;
import smartcampus.repository.AIRequestLogRepository;
import smartcampus.repository.AIStudyPlanItemRepository;
import smartcampus.repository.AIStudyPlanRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.UserRepository;

/**
 * Wave-1 verification suite for the Phase 6 (AI) JPA domain layer.
 *
 * <p>Runs against a real, freshly-provisioned MySQL container (see {@link
 * TestcontainersConfiguration}) with Flyway applying every migration on the classpath,
 * so a passing {@code @SpringBootTest} context load IS the {@code
 * spring.jpa.hibernate.ddl-auto=validate} proof for all five Phase 6 entities
 * (PROJECT_PLAN.md clarification G8): if a single column name, nullability or JDBC type
 * in {@code AIConversation}, {@code AIMessage}, {@code AIStudyPlan}, {@code
 * AIStudyPlanItem} or {@code AIRequestLog} diverged from {@code V6__ai.sql}, the context
 * would fail to start and every test below would fail with it.
 *
 * <p>On top of that, this suite asserts real behaviour through the repositories against
 * real MySQL rather than merely booting: a MEDIUMTEXT round trip larger than the
 * VARCHAR/TEXT ceiling, {@code findMaxSeqNo}/{@code findMaxPosition} semantics on an
 * empty parent, five database CHECK constraints firing exactly where the contract says
 * they must, the rate-limit window count, and the three different {@code ON DELETE}
 * behaviours fanning out from one conversation delete.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AiSchemaValidationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private AIConversationRepository conversationRepository;
    @Autowired private AIMessageRepository messageRepository;
    @Autowired private AIStudyPlanRepository studyPlanRepository;
    @Autowired private AIStudyPlanItemRepository studyPlanItemRepository;
    @Autowired private AIRequestLogRepository requestLogRepository;

    private static long counter = 0;

    private String unique(String prefix) {
        return prefix + "-" + System.nanoTime() + "-" + (counter++);
    }

    private User persistUser() {
        String tag = unique("user");
        return userRepository.save(User.builder()
                .email(tag + "@example.com")
                .password("irrelevant-hash")
                .fullName("AI Verification User")
                .role(Role.STUDENT)
                .build());
    }

    private Student persistStudent() {
        User user = persistUser();
        return studentRepository.save(Student.builder().user(user).build());
    }

    private AIConversation persistConversation(User user) {
        return conversationRepository.saveAndFlush(AIConversation.builder()
                .user(user)
                .title("Verification conversation " + unique("conv"))
                .feature(AIFeature.CHAT)
                .build());
    }

    // ------------------------------------------------------------------
    // Context load == ddl-auto=validate proof for all five Phase 6 tables
    // ------------------------------------------------------------------

    @Test
    void contextLoads() {
        // Intentionally empty: a failing @JdbcTypeCode, column name, nullability or
        // length mismatch anywhere in the Phase 6 entities fails Spring context
        // startup before this test body ever runs.
        assertThat(conversationRepository).isNotNull();
    }

    // ------------------------------------------------------------------
    // (a) MEDIUMTEXT / @JdbcTypeCode(LONGVARCHAR) round-trip on ai_messages.content
    // ------------------------------------------------------------------

    @Test
    void mediumTextContent_roundTripsValueLargerThan65535Bytes_byteIdentical() {
        User user = persistUser();
        AIConversation conversation = persistConversation(user);

        // 70,000 characters comfortably exceeds the 65,535-byte VARCHAR/TEXT ceiling
        // that a plain String field (mapped to VARCHAR without @JdbcTypeCode) would
        // silently truncate at or fail to write at all.
        String large = "x".repeat(70_000);

        AIMessage saved = messageRepository.saveAndFlush(AIMessage.builder()
                .conversation(conversation)
                .seqNo(0)
                .role(AIMessageRole.USER)
                .content(large)
                .build());

        AIMessage reloaded = messageRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getContent()).hasSize(70_000);
        assertThat(reloaded.getContent()).isEqualTo(large);
    }

    // ------------------------------------------------------------------
    // (b) findMaxSeqNo: -1 on empty conversation, true max afterwards
    // ------------------------------------------------------------------

    @Test
    void findMaxSeqNo_returnsMinusOneEmpty_thenTrueMaxAfterInserts() {
        User user = persistUser();
        AIConversation conversation = persistConversation(user);

        assertThat(messageRepository.findMaxSeqNo(conversation.getId())).isEqualTo(-1);

        messageRepository.saveAndFlush(AIMessage.builder()
                .conversation(conversation)
                .seqNo(0)
                .role(AIMessageRole.SYSTEM)
                .content("System context.")
                .grounded(true)
                .build());
        messageRepository.saveAndFlush(AIMessage.builder()
                .conversation(conversation)
                .seqNo(1)
                .role(AIMessageRole.USER)
                .content("Explain recursion.")
                .build());
        messageRepository.saveAndFlush(AIMessage.builder()
                .conversation(conversation)
                .seqNo(2)
                .role(AIMessageRole.ASSISTANT)
                .content("Recursion is...")
                .model("llama-3.3-70b-versatile")
                .build());

        assertThat(messageRepository.findMaxSeqNo(conversation.getId())).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // (c) chk_ai_messages_assistant_has_model: ASSISTANT with model=null is rejected
    // ------------------------------------------------------------------

    @Test
    void assistantMessage_withNullModel_isRejectedByTheDatabase() {
        User user = persistUser();
        AIConversation conversation = persistConversation(user);

        assertThatThrownBy(() -> messageRepository.saveAndFlush(AIMessage.builder()
                        .conversation(conversation)
                        .seqNo(0)
                        .role(AIMessageRole.ASSISTANT)
                        .content("A fabricated answer with no model attached.")
                        .model(null)
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // (d) chk_ai_study_plan_items_completion_consistent: completed=true, completedAt=null
    // ------------------------------------------------------------------

    @Test
    void studyPlanItem_completedTrueWithNullCompletedAt_isRejectedByTheDatabase() {
        Student student = persistStudent();
        AIStudyPlan plan = studyPlanRepository.saveAndFlush(AIStudyPlan.builder()
                .student(student)
                .planType(AIStudyPlanType.STUDY_PLAN)
                .title("Verification plan")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(7))
                .status(AIStudyPlanStatus.ACTIVE)
                .source(AIStudyPlanSource.AI_GENERATED)
                .model("llama-3.3-70b-versatile")
                .build());

        assertThatThrownBy(() -> studyPlanItemRepository.saveAndFlush(AIStudyPlanItem.builder()
                        .studyPlan(plan)
                        .position(0)
                        .scheduledDate(LocalDate.now())
                        .title("Revise arrays")
                        .completed(true)
                        .completedAt(null)
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // (e) chk_ai_request_logs_error_matches_outcome: SUCCESS with non-null error
    // ------------------------------------------------------------------

    @Test
    void requestLog_successOutcomeWithErrorMessage_isRejectedByTheDatabase() {
        User user = persistUser();

        assertThatThrownBy(() -> requestLogRepository.saveAndFlush(AIRequestLog.builder()
                        .user(user)
                        .feature(AIFeature.CHAT)
                        .outcome(AIRequestOutcome.SUCCESS)
                        .model("llama-3.3-70b-versatile")
                        .errorMessage("this should not be here on a success row")
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // (f) countByUserIdAndCreatedAtGreaterThanEqual: only counts rows inside the window
    // ------------------------------------------------------------------

    @Test
    void countByUserIdAndCreatedAtGreaterThanEqual_countsOnlyRowsInsideWindow() {
        User user = persistUser();

        requestLogRepository.saveAndFlush(AIRequestLog.builder()
                .user(user)
                .feature(AIFeature.CHAT)
                .outcome(AIRequestOutcome.SUCCESS)
                .model("llama-3.3-70b-versatile")
                .build());

        requestLogRepository.saveAndFlush(AIRequestLog.builder()
                .user(user)
                .feature(AIFeature.CHAT)
                .outcome(AIRequestOutcome.NOT_CONFIGURED)
                .errorMessage("no api key configured")
                .build());

        // `ai_request_logs.created_at` is a MySQL DATETIME (whole-second precision), so
        // a window boundary must not be compared against a Java LocalDateTime.now()
        // carrying sub-second precision finer than the column stores - it must be
        // comfortably clear of "now" in each direction to avoid a spurious truncation
        // mismatch. A window starting well in the past must count both rows...
        long countRecentWindow = requestLogRepository.countByUserIdAndCreatedAtGreaterThanEqual(
                user.getId(), LocalDateTime.now().minusMinutes(1));
        assertThat(countRecentWindow).isEqualTo(2);

        // ...a window starting well in the future must count neither, proving the
        // predicate is a real >= filter rather than an unconditional count.
        long countFarFuture = requestLogRepository.countByUserIdAndCreatedAtGreaterThanEqual(
                user.getId(), LocalDateTime.now().plusDays(1));
        assertThat(countFarFuture).isEqualTo(0);

        // ...and a different user's identical window sees none of this user's rows.
        User otherUser = persistUser();
        long countOtherUser = requestLogRepository.countByUserIdAndCreatedAtGreaterThanEqual(
                otherUser.getId(), LocalDateTime.now().minusMinutes(1));
        assertThat(countOtherUser).isEqualTo(0);
    }

    // ------------------------------------------------------------------
    // (g) delete cascade fan-out: messages cascade, plan.conversation -> NULL,
    //     request log rows survive
    // ------------------------------------------------------------------

    @Test
    void deletingConversation_cascadesMessages_nullsPlanConversation_keepsRequestLog() {
        User user = persistUser();
        Student student = studentRepository.save(Student.builder().user(user).build());
        AIConversation conversation = persistConversation(user);

        AIMessage message = messageRepository.saveAndFlush(AIMessage.builder()
                .conversation(conversation)
                .seqNo(0)
                .role(AIMessageRole.USER)
                .content("Hello")
                .build());

        AIStudyPlan plan = studyPlanRepository.saveAndFlush(AIStudyPlan.builder()
                .student(student)
                .conversation(conversation)
                .planType(AIStudyPlanType.STUDY_PLAN)
                .title("Plan tied to a conversation")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(3))
                .status(AIStudyPlanStatus.ACTIVE)
                .source(AIStudyPlanSource.AI_GENERATED)
                .model("llama-3.3-70b-versatile")
                .build());

        AIRequestLog log = requestLogRepository.saveAndFlush(AIRequestLog.builder()
                .user(user)
                .conversation(conversation)
                .feature(AIFeature.CHAT)
                .outcome(AIRequestOutcome.SUCCESS)
                .model("llama-3.3-70b-versatile")
                .build());

        conversationRepository.delete(conversation);
        conversationRepository.flush();

        assertThat(messageRepository.findById(message.getId())).isEmpty();

        AIStudyPlan reloadedPlan = studyPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(reloadedPlan.getConversation()).isNull();

        AIRequestLog reloadedLog = requestLogRepository.findById(log.getId()).orElseThrow();
        assertThat(reloadedLog).isNotNull();
    }

    // ------------------------------------------------------------------
    // findMaxPosition: -1 on empty plan, true max afterwards
    // ------------------------------------------------------------------

    @Test
    void findMaxPosition_returnsMinusOneEmpty_thenTrueMaxAfterInserts() {
        Student student = persistStudent();
        AIStudyPlan plan = studyPlanRepository.saveAndFlush(AIStudyPlan.builder()
                .student(student)
                .planType(AIStudyPlanType.STUDY_PLAN)
                .title("Position verification plan")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(5))
                .status(AIStudyPlanStatus.ACTIVE)
                .source(AIStudyPlanSource.AI_GENERATED)
                .model("llama-3.3-70b-versatile")
                .build());

        assertThat(studyPlanItemRepository.findMaxPosition(plan.getId())).isEqualTo(-1);

        studyPlanItemRepository.saveAndFlush(AIStudyPlanItem.builder()
                .studyPlan(plan)
                .position(0)
                .scheduledDate(LocalDate.now())
                .title("Item 0")
                .build());
        studyPlanItemRepository.saveAndFlush(AIStudyPlanItem.builder()
                .studyPlan(plan)
                .position(1)
                .scheduledDate(LocalDate.now().plusDays(1))
                .title("Item 1")
                .build());

        assertThat(studyPlanItemRepository.findMaxPosition(plan.getId())).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // message_count / last_message_at consistency CHECK: a mismatched pair is rejected
    // ------------------------------------------------------------------

    @Test
    void conversation_withPositiveMessageCountAndNullLastMessageAt_isRejectedByTheDatabase() {
        User user = persistUser();

        assertThatThrownBy(() -> conversationRepository.saveAndFlush(AIConversation.builder()
                        .user(user)
                        .title("Inconsistent counters")
                        .feature(AIFeature.CHAT)
                        .messageCount(3)
                        .lastMessageAt(null)
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // chk_ai_study_plans_model_matches_source: STUDENT_CREATED with non-null model
    // ------------------------------------------------------------------

    @Test
    void studentCreatedPlan_withNonNullModel_isRejectedByTheDatabase() {
        Student student = persistStudent();

        assertThatThrownBy(() -> studyPlanRepository.saveAndFlush(AIStudyPlan.builder()
                        .student(student)
                        .planType(AIStudyPlanType.STUDY_PLAN)
                        .title("Student-authored plan")
                        .startDate(LocalDate.now())
                        .endDate(LocalDate.now().plusDays(2))
                        .status(AIStudyPlanStatus.ACTIVE)
                        .source(AIStudyPlanSource.STUDENT_CREATED)
                        .model("should-not-be-set")
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
