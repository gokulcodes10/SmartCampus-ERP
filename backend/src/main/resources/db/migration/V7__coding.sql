-- V7__coding.sql  --  SmartCampus ERP coding module schema (Phase 7, Coding)
--
-- Creates the seven tables the coding module owns:
--   coding_problems          - an authored problem statement (§30) with its execution
--                               limits. Authoring is ADMIN-only (README "For
--                               Administrators": coding contest creation and problem
--                               authoring), so created_by references users(id).
--   problem_test_cases       - clarification G3. `is_sample = 1` cases are shown to the
--                               student and are what the playground "Run" button
--                               executes; `is_sample = 0` (hidden) cases are what drive
--                               the ACCEPTED / WRONG_ANSWER verdict and are never
--                               serialized to a non-admin caller.
--   coding_contests          - a timed contest (§31, §32) with its per-wrong-attempt
--                               penalty.
--   contest_problems         - which problems a contest contains, in which order, worth
--                               how many points.
--   coding_submissions       - one student's attempt at one problem, optionally inside a
--                               contest, with the full §29 status set and the aggregate
--                               verdict. This IS the submission history table.
--   submission_test_results  - the per-test-case outcome behind that verdict, so a
--                               verdict is auditable rather than asserted. `actual_output`
--                               is only ever returned to a student for a SAMPLE case.
--   contest_participants     - registration plus the denormalized leaderboard row
--                               (score / solved / penalty), recomputed from
--                               coding_submissions after every judged contest submission
--                               and by an admin recompute endpoint. It is a cache of a
--                               query, never an independent source of truth.
--
-- Creation order follows the FK dependency chain:
--   coding_problems -> problem_test_cases
--   coding_contests -> contest_problems
--   (both)          -> coding_submissions -> submission_test_results
--                   -> contest_participants
--
-- Inherits utf8mb4 / utf8mb4_unicode_ci from the database default set in
-- V1__baseline.sql; each table still declares ENGINE/CHARSET/COLLATE explicitly,
-- matching V1, V2 and V3 style. utf8mb4 matters here more than anywhere else in the
-- schema: source code, compiler diagnostics and program output are all stored as text
-- and routinely contain non-BMP characters.
--
-- ---------------------------------------------------------------------------------------
-- A note on the honesty constraints below
-- ---------------------------------------------------------------------------------------
-- §69 forbids fake functionality, and the specific fake this module could produce is a
-- fabricated ACCEPTED verdict when the execution backend (Judge0) is unreachable -
-- which, per clarification G10, is the normal state on the current development machine.
-- chk_coding_submissions_accepted_is_earned below makes that impossible at the database
-- level, not merely discouraged in Java: a row cannot be stored with status 'ACCEPTED'
-- unless it actually ran against at least one test case and passed every one of them.
-- Anything that cannot be executed must be stored with its real failure status
-- (INTERNAL_ERROR) and a real error_message.

-- ---------------------------------------------------------------------------------------
-- coding_problems
-- ---------------------------------------------------------------------------------------
-- time_limit_ms / memory_limit_kb are per-test-case execution limits handed to Judge0 as
-- `cpu_time_limit` (seconds, so ms/1000.0) and `memory_limit` (kilobytes). The upper
-- bounds in the CHECK constraints are the ceilings a default Judge0 CE deployment will
-- accept without configuration changes; storing a value above them would produce a
-- submission that is rejected by the judge at run time rather than by validation here.
--
-- Both Java and C++ are accepted for every problem: the project supports exactly those
-- two languages (README "Coding playground for Java and C++"; additional languages are
-- explicitly out of scope), so a per-problem language allowlist would be a column with
-- one possible value. There is deliberately no such column.
CREATE TABLE coding_problems (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    slug             VARCHAR(120) NOT NULL,
    title            VARCHAR(200) NOT NULL,
    description      MEDIUMTEXT   NOT NULL,
    input_format     MEDIUMTEXT   NULL,
    output_format    MEDIUMTEXT   NULL,
    constraints_text MEDIUMTEXT   NULL,
    sample_input     MEDIUMTEXT   NULL,
    sample_output    MEDIUMTEXT   NULL,
    difficulty       VARCHAR(10)  NOT NULL,
    time_limit_ms    INT          NOT NULL DEFAULT 2000,
    memory_limit_kb  INT          NOT NULL DEFAULT 262144,
    tags             VARCHAR(255) NULL,
    published        TINYINT(1)   NOT NULL DEFAULT 0,
    created_by       BIGINT       NOT NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_coding_problems_slug (slug),
    CONSTRAINT fk_coding_problems_created_by
        FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT chk_coding_problems_difficulty
        CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    CONSTRAINT chk_coding_problems_time_limit
        CHECK (time_limit_ms >= 100 AND time_limit_ms <= 15000),
    CONSTRAINT chk_coding_problems_memory_limit
        CHECK (memory_limit_kb >= 16384 AND memory_limit_kb <= 512000)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- The student-facing problem browser runs
--   WHERE published = 1 [AND difficulty = ?] ORDER BY id
-- on every page load; `published` is not a FK column so InnoDB does not index it for
-- free. Trailing `id` lets the ordering be served from the index rather than a filesort.
CREATE INDEX idx_coding_problems_published_difficulty
    ON coding_problems (published, difficulty, id);

-- ---------------------------------------------------------------------------------------
-- problem_test_cases  (clarification G3)
-- ---------------------------------------------------------------------------------------
-- `input` and `expected_output` are NOT NULL but may legitimately be the empty string:
-- plenty of problems read nothing from stdin, and a program whose correct answer is no
-- output at all is valid. NULL, however, is not a meaningful test case, so it is refused.
--
-- `ordinal` is the 1-based execution and display order within a problem. It is what a
-- verdict refers to when it says "failed on test case 4" - the ONLY thing a student is
-- ever told about a hidden case. Its input and expected output are never serialized to a
-- non-admin caller.
--
-- `weight` is the partial-credit weight of the case (G3). A submission's score is the sum
-- of the weights of the cases it passed, out of the sum of all weights. Weight does not
-- affect the ACCEPTED verdict, which requires every case to pass regardless of weight.
CREATE TABLE problem_test_cases (
    id              BIGINT     NOT NULL AUTO_INCREMENT,
    problem_id      BIGINT     NOT NULL,
    ordinal         INT        NOT NULL,
    input           MEDIUMTEXT NOT NULL,
    expected_output MEDIUMTEXT NOT NULL,
    is_sample       TINYINT(1) NOT NULL DEFAULT 0,
    weight          INT        NOT NULL DEFAULT 1,
    created_at      DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- Two cases cannot occupy the same position in a problem. Its leftmost column also
    -- makes it the index for "every test case of this problem, in execution order",
    -- which is the query every submission runs.
    UNIQUE KEY uk_problem_test_cases_problem_ordinal (problem_id, ordinal),
    CONSTRAINT fk_problem_test_cases_problem
        FOREIGN KEY (problem_id) REFERENCES coding_problems (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_problem_test_cases_ordinal_positive CHECK (ordinal > 0),
    CONSTRAINT chk_problem_test_cases_weight_positive CHECK (weight > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- The playground "Run" button and the student-facing problem page both fetch only the
-- sample cases: WHERE problem_id = ? AND is_sample = 1 ORDER BY ordinal. The unique key
-- above cannot serve the is_sample predicate, so this composite index adds it.
CREATE INDEX idx_problem_test_cases_problem_sample
    ON problem_test_cases (problem_id, is_sample, ordinal);

-- ---------------------------------------------------------------------------------------
-- coding_contests  (§31, §32)
-- ---------------------------------------------------------------------------------------
-- `status` is the AUTHORING lifecycle only - DRAFT (not visible to students), PUBLISHED
-- (visible and joinable), CANCELLED. It is deliberately NOT "upcoming / running / ended":
-- a stored running-state would be wrong the moment the clock passed it and nothing wrote
-- the row, which is exactly the kind of stale-because-nobody-updated-it value §69 calls
-- fake. Whether a contest is upcoming, running or ended is derived from start_time /
-- end_time against the current time, every time it is asked.
--
-- `penalty_minutes_per_wrong_attempt` is the ICPC-style time penalty added per rejected
-- attempt on a problem the participant eventually solves. It is per contest and
-- configurable rather than hard-coded (§60).
CREATE TABLE coding_contests (
    id                                BIGINT       NOT NULL AUTO_INCREMENT,
    slug                              VARCHAR(120) NOT NULL,
    title                             VARCHAR(200) NOT NULL,
    description                       MEDIUMTEXT   NULL,
    start_time                        DATETIME     NOT NULL,
    end_time                          DATETIME     NOT NULL,
    status                            VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    penalty_minutes_per_wrong_attempt INT          NOT NULL DEFAULT 10,
    created_by                        BIGINT       NOT NULL,
    created_at                        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_coding_contests_slug (slug),
    CONSTRAINT fk_coding_contests_created_by
        FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT chk_coding_contests_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    -- A zero-length or backwards contest window would make every derived phase, every
    -- "is this submission inside the window" check and every time penalty meaningless.
    CONSTRAINT chk_coding_contests_window CHECK (end_time > start_time),
    CONSTRAINT chk_coding_contests_penalty_non_negative
        CHECK (penalty_minutes_per_wrong_attempt >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- The contest list is always "the published ones, soonest first"
--   WHERE status = 'PUBLISHED' [AND end_time > NOW()] ORDER BY start_time
-- and the phase filter narrows on end_time as well.
CREATE INDEX idx_coding_contests_status_start ON coding_contests (status, start_time);
CREATE INDEX idx_coding_contests_end_time ON coding_contests (end_time);

-- ---------------------------------------------------------------------------------------
-- contest_problems
-- ---------------------------------------------------------------------------------------
-- `ordinal` is the 1-based position of the problem in the contest; the UI renders it as
-- the usual A / B / C label. `points` is what solving it is worth on that contest's
-- leaderboard, independent of the problem's own difficulty.
--
-- The problem FK is RESTRICT: a problem that is part of a contest cannot be deleted out
-- from under it. Removing it from the contest first is a deliberate act.
CREATE TABLE contest_problems (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    contest_id BIGINT   NOT NULL,
    problem_id BIGINT   NOT NULL,
    ordinal    INT      NOT NULL,
    points     INT      NOT NULL DEFAULT 100,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- The same problem cannot appear twice in one contest. This key is also the target
    -- of the composite foreign key on coding_submissions below, which is what stops a
    -- submission claiming to be "for contest C, problem P" when P is not in C.
    UNIQUE KEY uk_contest_problems_contest_problem (contest_id, problem_id),
    -- Two problems cannot occupy the same slot in a contest.
    UNIQUE KEY uk_contest_problems_contest_ordinal (contest_id, ordinal),
    CONSTRAINT fk_contest_problems_contest
        FOREIGN KEY (contest_id) REFERENCES coding_contests (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_contest_problems_problem
        FOREIGN KEY (problem_id) REFERENCES coding_problems (id),
    CONSTRAINT chk_contest_problems_ordinal_positive CHECK (ordinal > 0),
    CONSTRAINT chk_contest_problems_points_positive CHECK (points > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------------------
-- coding_submissions  (§29 status set + submission history)
-- ---------------------------------------------------------------------------------------
-- One row per attempt. `contest_id` NULL means a practice submission from the playground;
-- non-NULL means it counts toward that contest's leaderboard.
--
-- `created_at` IS the submission time - there is no separate submitted_at column, and the
-- API exposes this value as `submittedAt`. It is what contest time penalties are measured
-- from, so it is never updated after insert.
--
-- The status vocabulary is the project's SubmissionStatus enum in full. PENDING and
-- RUNNING are transient states while the judge works; every other value is terminal.
-- INTERNAL_ERROR is the honest outcome when the execution backend cannot be reached or
-- does not return a verdict (clarification G10) - `error_message` carries why.
CREATE TABLE coding_submissions (
    id                       BIGINT      NOT NULL AUTO_INCREMENT,
    problem_id               BIGINT      NOT NULL,
    student_id               BIGINT      NOT NULL,
    contest_id               BIGINT      NULL,
    language                 VARCHAR(10) NOT NULL,
    source_code              MEDIUMTEXT  NOT NULL,
    status                   VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    passed_test_cases        INT         NOT NULL DEFAULT 0,
    total_test_cases         INT         NOT NULL DEFAULT 0,
    score                    INT         NOT NULL DEFAULT 0,
    max_score                INT         NOT NULL DEFAULT 0,
    execution_time_ms        INT         NULL,
    memory_kb                INT         NULL,
    failed_test_case_ordinal INT         NULL,
    compile_output           MEDIUMTEXT  NULL,
    error_message            MEDIUMTEXT  NULL,
    judged_at                DATETIME    NULL,
    created_at               DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- Explicitly named index backing the composite foreign key below. InnoDB would
    -- create one implicitly; declaring it keeps the name predictable in EXPLAIN output
    -- and in any later migration that needs to drop it.
    KEY fk_idx_coding_submissions_contest_problem (contest_id, problem_id),
    CONSTRAINT fk_coding_submissions_problem
        FOREIGN KEY (problem_id) REFERENCES coding_problems (id),
    -- Deleting a student (which happens by cascade from users, per V3) takes their
    -- coding history with them; the history has no meaning without the student.
    CONSTRAINT fk_coding_submissions_student
        FOREIGN KEY (student_id) REFERENCES students (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_coding_submissions_contest
        FOREIGN KEY (contest_id) REFERENCES coding_contests (id),
    -- The invariant that a contest submission must be for a problem that is actually in
    -- that contest. InnoDB uses MATCH SIMPLE semantics, so when contest_id IS NULL the
    -- check is skipped entirely and practice submissions are unaffected. Without this,
    -- a caller could post problemId=X with contestId=Y and have it silently scored.
    CONSTRAINT fk_coding_submissions_contest_problem
        FOREIGN KEY (contest_id, problem_id)
        REFERENCES contest_problems (contest_id, problem_id),
    CONSTRAINT chk_coding_submissions_language
        CHECK (language IN ('JAVA', 'CPP')),
    CONSTRAINT chk_coding_submissions_status
        CHECK (status IN (
            'PENDING', 'RUNNING', 'ACCEPTED', 'WRONG_ANSWER', 'TIME_LIMIT_EXCEEDED',
            'MEMORY_LIMIT_EXCEEDED', 'COMPILATION_ERROR', 'RUNTIME_ERROR',
            'INTERNAL_ERROR')),
    CONSTRAINT chk_coding_submissions_counts
        CHECK (passed_test_cases >= 0
               AND total_test_cases >= 0
               AND passed_test_cases <= total_test_cases),
    CONSTRAINT chk_coding_submissions_score
        CHECK (score >= 0 AND max_score >= 0 AND score <= max_score),
    -- THE anti-fake-verdict constraint (§69). ACCEPTED is only storable if the
    -- submission actually ran against at least one test case and passed every one of
    -- them. A judge outage, a timeout waiting for a verdict, or any other path that
    -- produced no test results cannot be recorded as a pass - it has to be stored with
    -- its real status instead.
    CONSTRAINT chk_coding_submissions_accepted_is_earned
        CHECK (status <> 'ACCEPTED'
               OR (total_test_cases > 0 AND passed_test_cases = total_test_cases)),
    -- Mirror image: a submission that passed every one of a non-zero number of test
    -- cases cannot be recorded as WRONG_ANSWER either.
    CONSTRAINT chk_coding_submissions_wrong_answer_is_earned
        CHECK (status <> 'WRONG_ANSWER'
               OR total_test_cases = 0
               OR passed_test_cases < total_test_cases)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- "My submissions, newest first" (the student's submission-history screen):
-- WHERE student_id = ? ORDER BY id DESC.
CREATE INDEX idx_coding_submissions_student_recent
    ON coding_submissions (student_id, id);

-- "My submissions on this problem, newest first", and the "have I already solved this?"
-- badge on the problem list: WHERE student_id = ? AND problem_id = ? [AND status = ...].
CREATE INDEX idx_coding_submissions_student_problem
    ON coding_submissions (student_id, problem_id, id);

-- Per-problem statistics (attempts, acceptance rate) on the admin problem screen:
-- WHERE problem_id = ? GROUP BY status.
CREATE INDEX idx_coding_submissions_problem_status
    ON coding_submissions (problem_id, status);

-- The global leaderboard aggregation: every ACCEPTED submission, grouped by student and
-- distinct problem. status leads because it is the only equality predicate.
CREATE INDEX idx_coding_submissions_accepted
    ON coding_submissions (status, student_id, problem_id, created_at);

-- Contest rescoring. Two shapes, both served by this one index: recompute one
-- participant (contest_id + student_id, then walk their problems in time order) and
-- recompute a whole contest (contest_id alone).
CREATE INDEX idx_coding_submissions_contest_scoring
    ON coding_submissions (contest_id, student_id, problem_id, created_at);

-- ---------------------------------------------------------------------------------------
-- submission_test_results
-- ---------------------------------------------------------------------------------------
-- The evidence behind a verdict: one row per test case the submission was actually run
-- against. Only terminal statuses are storable here - a per-test result is written after
-- the judge has finished with that case, so PENDING and RUNNING are not valid values.
--
-- `is_sample` is a deliberate denormalized snapshot of problem_test_cases.is_sample as it
-- was at judging time. The API reveals `actual_output` / `stderr_output` only for sample
-- cases, and that decision must not change retroactively because an admin later flipped a
-- case from sample to hidden - which would otherwise leak a hidden case's behaviour
-- through an old submission.
--
-- `judge0_token` and `judge0_status_id` record exactly what the execution backend
-- returned, so a verdict can be traced back to the run that produced it rather than
-- taken on trust.
CREATE TABLE submission_test_results (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    submission_id     BIGINT      NOT NULL,
    test_case_id      BIGINT      NOT NULL,
    ordinal           INT         NOT NULL,
    is_sample         TINYINT(1)  NOT NULL DEFAULT 0,
    status            VARCHAR(30) NOT NULL,
    execution_time_ms INT         NULL,
    memory_kb         INT         NULL,
    judge0_token      VARCHAR(64) NULL,
    judge0_status_id  INT         NULL,
    actual_output     MEDIUMTEXT  NULL,
    stderr_output     MEDIUMTEXT  NULL,
    created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- One result per test case per submission. Also the index for "the results of this
    -- submission", which is every read this table serves.
    UNIQUE KEY uk_submission_test_results_submission_case (submission_id, test_case_id),
    CONSTRAINT fk_submission_test_results_submission
        FOREIGN KEY (submission_id) REFERENCES coding_submissions (id)
        ON DELETE CASCADE,
    -- Cascade, not restrict: editing a problem's test cases is an authoring action that
    -- invalidates the historical per-case detail. The submission row and its aggregate
    -- verdict survive; only the now-meaningless per-case rows go.
    CONSTRAINT fk_submission_test_results_test_case
        FOREIGN KEY (test_case_id) REFERENCES problem_test_cases (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_submission_test_results_ordinal_positive CHECK (ordinal > 0),
    CONSTRAINT chk_submission_test_results_status
        CHECK (status IN (
            'ACCEPTED', 'WRONG_ANSWER', 'TIME_LIMIT_EXCEEDED', 'MEMORY_LIMIT_EXCEEDED',
            'COMPILATION_ERROR', 'RUNTIME_ERROR', 'INTERNAL_ERROR'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------------------
-- contest_participants
-- ---------------------------------------------------------------------------------------
-- Registration plus the leaderboard row. Every scoring column here is DERIVED from
-- coding_submissions and is rewritten wholesale by the recompute routine after each
-- judged contest submission - it is never incremented in place, so it cannot drift away
-- from the submissions that justify it, and an admin recompute can rebuild the whole
-- contest from the submission table alone.
--
-- `penalty_seconds` is the ICPC-style total: for each SOLVED problem, the seconds from
-- contest start to that problem's first accepted submission, plus
-- penalty_minutes_per_wrong_attempt * 60 for each rejected attempt on that problem before
-- the accept. Unsolved problems contribute nothing.
CREATE TABLE contest_participants (
    id               BIGINT   NOT NULL AUTO_INCREMENT,
    contest_id       BIGINT   NOT NULL,
    student_id       BIGINT   NOT NULL,
    registered_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_score      INT      NOT NULL DEFAULT 0,
    problems_solved  INT      NOT NULL DEFAULT 0,
    penalty_seconds  INT      NOT NULL DEFAULT 0,
    last_accepted_at DATETIME NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- A student registers for a contest exactly once.
    UNIQUE KEY uk_contest_participants_contest_student (contest_id, student_id),
    CONSTRAINT fk_contest_participants_contest
        FOREIGN KEY (contest_id) REFERENCES coding_contests (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_contest_participants_student
        FOREIGN KEY (student_id) REFERENCES students (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_contest_participants_totals
        CHECK (total_score >= 0 AND problems_solved >= 0 AND penalty_seconds >= 0),
    -- A participant who has solved nothing cannot have accrued penalty time or have an
    -- accepted timestamp; both only exist as a consequence of solving something.
    CONSTRAINT chk_contest_participants_unsolved_is_clean
        CHECK (problems_solved > 0
               OR (penalty_seconds = 0 AND total_score = 0 AND last_accepted_at IS NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- The per-contest leaderboard, which is the single hottest query in this module:
--   WHERE contest_id = ?
--   ORDER BY total_score DESC, penalty_seconds ASC, last_accepted_at ASC, student_id ASC
-- MySQL 8 supports descending index columns, so the whole ordering is served from the
-- index with no filesort. The trailing student_id makes the ranking fully deterministic
-- rather than leaving tied rows in arbitrary order (which would make the displayed rank
-- of two tied students change between page loads).
CREATE INDEX idx_contest_participants_leaderboard
    ON contest_participants (contest_id, total_score DESC, penalty_seconds ASC,
                             last_accepted_at ASC, student_id ASC);
