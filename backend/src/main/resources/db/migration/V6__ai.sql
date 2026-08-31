-- V6__ai.sql  --  SmartCampus ERP AI assistant schema (Phase 6, AI)
--
-- Creates the five tables the AI module owns:
--   ai_conversations     - one chat thread belonging to exactly one user. Created,
--                           continued, renamed, deleted and replayed as history by the
--                           §25-§27 study assistant.
--   ai_messages          - the ordered turns inside one conversation: the SYSTEM turn
--                           carrying the student's REAL academic context (marks,
--                           attendance, weak subjects, upcoming exams), the USER turn,
--                           and the ASSISTANT turn that a real provider call produced.
--   ai_study_plans       - a generated study plan or revision schedule. Advisory and
--                           STUDENT-EDITABLE: once generated it is the student's own
--                           document, so it lives in its own table rather than only as
--                           prose inside an assistant message.
--   ai_study_plan_items  - the individual scheduled study items of one plan, each
--                           independently editable and completable by the student.
--   ai_request_logs      - one row per AI request ATTEMPT, successful or not. This is
--                           both the §61 rate-limit ledger (count rows for a user in a
--                           window) and the honest usage record: a provider failure is
--                           recorded as a failure, never as a fabricated answer (§69).
--
-- Creation order follows the FK dependency chain: ai_conversations depends on `users`
-- (V2), ai_messages on ai_conversations, ai_study_plans on `students` (V3) and
-- optionally ai_conversations, ai_study_plan_items on ai_study_plans and optionally
-- `subjects` (V3), ai_request_logs on `users` and optionally ai_conversations.
--
-- Inherits utf8mb4 / utf8mb4_unicode_ci from the database default set in
-- V1__baseline.sql; each table still declares ENGINE/CHARSET/COLLATE explicitly,
-- matching V1-V4 and V7 style. utf8mb4 matters more here than anywhere else in the
-- schema: model output routinely contains non-BMP characters (emoji, mathematical
-- symbols), and a 3-byte utf8 column would reject them mid-conversation.
--
-- Secrets note (§25, §61): nothing in this schema stores, or can store, the provider
-- API key. The only provider-identifying value persisted is the resolved model id,
-- which is not a credential and is safe to show a user.

-- ---------------------------------------------------------------------------------------
-- ai_conversations
-- ---------------------------------------------------------------------------------------
-- Owned by a `users` row rather than a `students` row on purpose: ownership ("is this
-- thread mine?") is an identity question, and the JWT principal is a User. The academic
-- GROUNDING is a separate concern and resolves through students.user_id at prompt-build
-- time. ON DELETE CASCADE because a conversation has no meaning without its owner.
--
-- `feature` records which entry point opened the thread, so history can be filtered
-- ("my study plans" vs "my chats") without re-parsing message text.
--
-- message_count and last_message_at are maintained by the service on every appended
-- turn. They exist so the conversation LIST screen never has to count or scan
-- ai_messages; the CHECK below keeps them from drifting into an impossible state.
CREATE TABLE ai_conversations (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    title            VARCHAR(150) NOT NULL,
    feature          VARCHAR(30)  NOT NULL DEFAULT 'CHAT',
    model            VARCHAR(120) NULL,
    message_count    INT          NOT NULL DEFAULT 0,
    last_message_at  DATETIME     NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_conversations_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_ai_conversations_feature
        CHECK (feature IN ('CHAT', 'STUDY_PLAN', 'TOPIC_EXPLANATION',
                           'PRACTICE_QUESTIONS', 'MCQ', 'REVISION_SCHEDULE')),
    -- A rename to "" or "   " would produce an unclickable blank row in the history
    -- list. Enforced here as well as by @NotBlank so it cannot be written by any path.
    CONSTRAINT chk_ai_conversations_title_not_blank
        CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT chk_ai_conversations_message_count_non_negative
        CHECK (message_count >= 0),
    -- "Zero messages" and "there was a last message" cannot both be true. Any conversation
    -- with turns must carry the timestamp of its most recent one.
    CONSTRAINT chk_ai_conversations_last_message_consistent
        CHECK ((message_count = 0 AND last_message_at IS NULL)
               OR (message_count > 0 AND last_message_at IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- THE conversation-list query: "my threads, newest activity first"
--   WHERE user_id = ? ORDER BY last_message_at DESC
-- and its filtered variant WHERE user_id = ? AND feature = ?. InnoDB's implicit index on
-- user_id (created for the FK above) carries neither column, so both are declared here.
CREATE INDEX idx_ai_conversations_user_activity
    ON ai_conversations (user_id, last_message_at);

CREATE INDEX idx_ai_conversations_user_feature
    ON ai_conversations (user_id, feature);

-- ---------------------------------------------------------------------------------------
-- ai_messages
-- ---------------------------------------------------------------------------------------
-- One turn of a conversation. `seq_no` is the ordering key WITHIN a conversation
-- (0-based, contiguous, assigned by the service); it is not a global sequence and it is
-- not the id. The unique key on (conversation_id, seq_no) is what makes replaying a
-- history deterministic: ORDER BY seq_no can never tie, whereas ORDER BY created_at can
-- (all three turns of one exchange are written in the same second).
--
-- Column name is `seq_no`, not `sequence`: SEQUENCE is a keyword in several SQL dialects
-- and a needless quoting hazard.
--
-- `content` is MEDIUMTEXT, not TEXT: the SYSTEM turn embeds the student's entire academic
-- snapshot and a long assistant answer easily clears the 65,535-byte TEXT ceiling. The
-- entity MUST map it with columnDefinition = "MEDIUMTEXT" AND
-- @JdbcTypeCode(SqlTypes.LONGVARCHAR), exactly as CodingProblem.description does, or
-- ddl-auto=validate rejects the mapping at boot (clarification G8).
--
-- `grounded` marks a SYSTEM turn whose content was built from real rows in `marks`,
-- `attendance` and `exams` for this student. It is the checkpoint's audit trail: the
-- proof that a given answer was grounded is a stored, readable system prompt, not a
-- claim in a log file.
--
-- Token counts and latency come from the provider response and are NULL when the
-- provider did not report them. NULL means "not reported", never zero.
CREATE TABLE ai_messages (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    conversation_id    BIGINT       NOT NULL,
    seq_no             INT          NOT NULL,
    role               VARCHAR(20)  NOT NULL,
    content            MEDIUMTEXT   NOT NULL,
    model              VARCHAR(120) NULL,
    grounded           TINYINT(1)   NOT NULL DEFAULT 0,
    prompt_tokens      INT          NULL,
    completion_tokens  INT          NULL,
    total_tokens       INT          NULL,
    latency_ms         INT          NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_messages_conversation_seq (conversation_id, seq_no),
    CONSTRAINT fk_ai_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_ai_messages_role
        CHECK (role IN ('SYSTEM', 'USER', 'ASSISTANT')),
    CONSTRAINT chk_ai_messages_seq_no_non_negative
        CHECK (seq_no >= 0),
    CONSTRAINT chk_ai_messages_content_not_blank
        CHECK (CHAR_LENGTH(TRIM(content)) > 0),
    -- THE anti-fabrication constraint (§69), the AI-module counterpart of
    -- chk_coding_submissions_accepted_is_earned in V7. An ASSISTANT turn is only
    -- storable if it carries the id of the model that produced it. A hand-written,
    -- cached, templated or otherwise fabricated "AI answer" has no model to name, so it
    -- cannot be written to this table at all.
    CONSTRAINT chk_ai_messages_assistant_has_model
        CHECK (role <> 'ASSISTANT' OR model IS NOT NULL),
    -- Only a SYSTEM turn carries injected academic context; marking a user or assistant
    -- turn "grounded" would make the audit trail meaningless.
    CONSTRAINT chk_ai_messages_grounded_is_system
        CHECK (grounded = 0 OR role = 'SYSTEM'),
    CONSTRAINT chk_ai_messages_token_counts_non_negative
        CHECK ((prompt_tokens     IS NULL OR prompt_tokens     >= 0)
           AND (completion_tokens IS NULL OR completion_tokens >= 0)
           AND (total_tokens      IS NULL OR total_tokens      >= 0)
           AND (latency_ms        IS NULL OR latency_ms        >= 0))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- The history query is "every turn of this conversation in order", which
-- uk_ai_messages_conversation_seq already serves as a covering prefix. No further index
-- is added here: a second index on (conversation_id, created_at) would be written on
-- every turn and read by nothing.

-- ---------------------------------------------------------------------------------------
-- ai_study_plans
-- ---------------------------------------------------------------------------------------
-- A study plan (or revision schedule - same structure, different plan_type) is ADVISORY:
-- the model proposes it, the student owns and edits it afterwards. That is why it is a
-- first-class table with its own editable rows rather than a blob of assistant prose.
--
-- Owned by a `students` row, not a `users` row: unlike a conversation, a plan is
-- inherently academic and only a student can have one. ON DELETE CASCADE from students
-- (which itself cascades from users, per V3).
--
-- conversation_id is the thread the plan was generated in, kept so a student can reopen
-- the discussion behind the plan. ON DELETE SET NULL rather than CASCADE: deleting the
-- chat must never silently destroy the plan the student has since been editing.
--
-- `source` distinguishes a plan the model produced from one the student created by hand,
-- and `edited` records that a student has since changed an AI-generated plan. Both exist
-- so the UI can label a plan honestly instead of implying the model endorsed edits it
-- never saw.
CREATE TABLE ai_study_plans (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    student_id       BIGINT       NOT NULL,
    conversation_id  BIGINT       NULL,
    plan_type        VARCHAR(20)  NOT NULL DEFAULT 'STUDY_PLAN',
    title            VARCHAR(150) NOT NULL,
    goal             VARCHAR(500) NULL,
    start_date       DATE         NOT NULL,
    end_date         DATE         NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    source           VARCHAR(20)  NOT NULL DEFAULT 'AI_GENERATED',
    model            VARCHAR(120) NULL,
    edited           TINYINT(1)   NOT NULL DEFAULT 0,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_study_plans_student
        FOREIGN KEY (student_id) REFERENCES students (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_ai_study_plans_conversation
        FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id)
        ON DELETE SET NULL,
    CONSTRAINT chk_ai_study_plans_type
        CHECK (plan_type IN ('STUDY_PLAN', 'REVISION_SCHEDULE')),
    CONSTRAINT chk_ai_study_plans_status
        CHECK (status IN ('ACTIVE', 'COMPLETED', 'ARCHIVED')),
    CONSTRAINT chk_ai_study_plans_source
        CHECK (source IN ('AI_GENERATED', 'STUDENT_CREATED')),
    CONSTRAINT chk_ai_study_plans_title_not_blank
        CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    -- A plan that ends before it starts is not a plan. Cheap to enforce here, and the
    -- date pair arrives from a model response that nothing else validates.
    CONSTRAINT chk_ai_study_plans_date_order
        CHECK (end_date >= start_date),
    -- An AI-generated plan must name the model that generated it - the same
    -- anti-fabrication rule as chk_ai_messages_assistant_has_model. A STUDENT_CREATED
    -- plan has no model and must not pretend to have one.
    CONSTRAINT chk_ai_study_plans_model_matches_source
        CHECK ((source = 'AI_GENERATED'    AND model IS NOT NULL)
            OR (source = 'STUDENT_CREATED' AND model IS NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- "my plans, active ones first, most recent window first" - the study-plan list screen:
--   WHERE student_id = ? [AND status = ?] [AND plan_type = ?] ORDER BY start_date DESC
CREATE INDEX idx_ai_study_plans_student_status_start
    ON ai_study_plans (student_id, status, start_date);

CREATE INDEX idx_ai_study_plans_student_type
    ON ai_study_plans (student_id, plan_type);

-- ---------------------------------------------------------------------------------------
-- ai_study_plan_items
-- ---------------------------------------------------------------------------------------
-- One scheduled study item. `position` is the student-visible ordering within the plan
-- (0-based, contiguous, assigned by the service), unique per plan for the same reason
-- ai_messages.seq_no is: two items on the same date must still have a stable order.
--
-- subject_id is OPTIONAL and ON DELETE SET NULL: a plan item usually maps to a real
-- `subjects` row (which is how a plan gets grounded in the student's actual weak
-- subjects), but a model may legitimately propose "revise pointers" with no catalog
-- subject behind it, and retiring a subject must not delete a student's plan item.
-- `subject_label` keeps the human-readable name the plan was written with even when
-- subject_id is NULL or is later cleared.
--
-- The completion pair is constrained rather than trusted: "done" and "done at" are one
-- fact, and half of it is a bug that surfaces months later as a blank timestamp.
CREATE TABLE ai_study_plan_items (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    study_plan_id     BIGINT       NOT NULL,
    subject_id        BIGINT       NULL,
    subject_label     VARCHAR(150) NULL,
    position          INT          NOT NULL,
    scheduled_date    DATE         NOT NULL,
    title             VARCHAR(200) NOT NULL,
    description       MEDIUMTEXT   NULL,
    duration_minutes  INT          NULL,
    completed         TINYINT(1)   NOT NULL DEFAULT 0,
    completed_at      DATETIME     NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_study_plan_items_plan_position (study_plan_id, position),
    CONSTRAINT fk_ai_study_plan_items_plan
        FOREIGN KEY (study_plan_id) REFERENCES ai_study_plans (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_ai_study_plan_items_subject
        FOREIGN KEY (subject_id) REFERENCES subjects (id)
        ON DELETE SET NULL,
    CONSTRAINT chk_ai_study_plan_items_position_non_negative
        CHECK (position >= 0),
    CONSTRAINT chk_ai_study_plan_items_title_not_blank
        CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    -- A study block of 0 minutes is not a study block; more than a full day is a parse
    -- error in the model's response, not a plan.
    CONSTRAINT chk_ai_study_plan_items_duration_range
        CHECK (duration_minutes IS NULL OR (duration_minutes > 0 AND duration_minutes <= 1440)),
    CONSTRAINT chk_ai_study_plan_items_completion_consistent
        CHECK ((completed = 0 AND completed_at IS NULL)
               OR (completed = 1 AND completed_at IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- The plan-detail query is "every item of this plan, in order", served by
-- uk_ai_study_plan_items_plan_position. This second index serves the calendar view -
-- "what am I supposed to study, by date" - which orders by scheduled_date instead:
--   WHERE study_plan_id = ? ORDER BY scheduled_date, position
CREATE INDEX idx_ai_study_plan_items_plan_date
    ON ai_study_plan_items (study_plan_id, scheduled_date);

-- ---------------------------------------------------------------------------------------
-- ai_request_logs   (the §61 rate-limit ledger and the honest usage record)
-- ---------------------------------------------------------------------------------------
-- One row per AI request ATTEMPT by one user, written whether the provider answered or
-- not. Two jobs:
--
--   1. Rate limiting (§61). The limiter counts rows for a user inside a rolling window
--      (per-minute and per-day), so the limit survives a restart and cannot be reset by
--      reconnecting - which an in-memory counter cannot promise. A FAILED attempt still
--      counts: otherwise a caller could hammer a failing provider without limit, which is
--      exactly the abuse the limit exists to stop.
--   2. Honest accounting (§69). `outcome` records what actually happened. NOT_CONFIGURED
--      means no API key was present and no call was made; PROVIDER_ERROR means the call
--      was made and failed; INVALID_RESPONSE means the provider answered but the answer
--      could not be parsed into the structure the feature required. In none of those
--      cases does the application invent an answer - the row is the record that it did
--      not.
--
-- IMPORTANT for the implementer: this row MUST be committed even when the surrounding
-- request then throws (a provider failure raises AIUnavailableException). Spring's
-- default rollback-on-unchecked rule would otherwise discard the very row that proves
-- the attempt happened - the exact trap that broke the Phase 2 brute-force cap. Write it
-- from a @Transactional(propagation = REQUIRES_NEW) recorder.
--
-- conversation_id is nullable and ON DELETE SET NULL: deleting a conversation must not
-- erase the usage/rate-limit history it generated.
--
-- error_message is VARCHAR(500), deliberately short: it holds a truncated, human-readable
-- reason. It must never contain the API key, a request body, or an Authorization header
-- (§25, §61) - the provider-facing implementation is responsible for not putting them
-- there.
CREATE TABLE ai_request_logs (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    user_id            BIGINT       NOT NULL,
    conversation_id    BIGINT       NULL,
    feature            VARCHAR(30)  NOT NULL,
    outcome            VARCHAR(20)  NOT NULL,
    model              VARCHAR(120) NULL,
    prompt_tokens      INT          NULL,
    completion_tokens  INT          NULL,
    total_tokens       INT          NULL,
    latency_ms         INT          NULL,
    error_message      VARCHAR(500) NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_request_logs_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_ai_request_logs_conversation
        FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id)
        ON DELETE SET NULL,
    CONSTRAINT chk_ai_request_logs_feature
        CHECK (feature IN ('CHAT', 'STUDY_PLAN', 'TOPIC_EXPLANATION',
                           'PRACTICE_QUESTIONS', 'MCQ', 'REVISION_SCHEDULE')),
    CONSTRAINT chk_ai_request_logs_outcome
        CHECK (outcome IN ('SUCCESS', 'PROVIDER_ERROR', 'NOT_CONFIGURED', 'INVALID_RESPONSE')),
    -- A success has no error to report, and a failure that reports nothing is useless
    -- for diagnosis. Both halves are enforced so neither can be skipped.
    CONSTRAINT chk_ai_request_logs_error_matches_outcome
        CHECK ((outcome =  'SUCCESS' AND error_message IS NULL)
            OR (outcome <> 'SUCCESS' AND error_message IS NOT NULL)),
    -- A successful call always names the model that answered - same anti-fabrication
    -- rule as chk_ai_messages_assistant_has_model, applied to the ledger.
    CONSTRAINT chk_ai_request_logs_success_has_model
        CHECK (outcome <> 'SUCCESS' OR model IS NOT NULL),
    CONSTRAINT chk_ai_request_logs_counts_non_negative
        CHECK ((prompt_tokens     IS NULL OR prompt_tokens     >= 0)
           AND (completion_tokens IS NULL OR completion_tokens >= 0)
           AND (total_tokens      IS NULL OR total_tokens      >= 0)
           AND (latency_ms        IS NULL OR latency_ms        >= 0))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- THE rate-limit query, run before every AI call:
--   SELECT COUNT(*) FROM ai_request_logs WHERE user_id = ? AND created_at >= ?
-- (once for the per-minute window, once for the per-day window). This composite index
-- is what keeps that count from degrading into a scan of the user's whole history as the
-- ledger grows; InnoDB's implicit FK index on user_id alone would not carry created_at.
CREATE INDEX idx_ai_request_logs_user_created
    ON ai_request_logs (user_id, created_at);

-- Supports a periodic retention/cleanup job deleting old ledger rows without a full
-- table scan, mirroring idx_password_reset_tokens_expires_at from V2.
CREATE INDEX idx_ai_request_logs_created_at
    ON ai_request_logs (created_at);
