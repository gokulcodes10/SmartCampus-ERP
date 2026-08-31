-- V11__realtime.sql  --  SmartCampus ERP real-time / notification schema (Phase 11, Real-Time)
--
-- Creates the two tables the real-time module owns:
--   announcements  - an admin broadcast (§42) with ALL / STUDENTS / FACULTY / DEPARTMENT
--                     targeting, a priority and an optional expiry. This is the SOURCE
--                     row: it is written once and is the permanent record of what was
--                     announced, by whom, to whom, and until when.
--   notifications  - one row per (recipient user, event). This is the DELIVERY row and
--                     the entire content of the §40 notification centre: unread count,
--                     mark read, mark all read, delete. Every notification in the system
--                     lives here, whatever produced it - an announcement fan-out, a
--                     placement decision, an interview change, a contest or leaderboard
--                     movement, or a low-attendance warning.
--
-- Creation order follows the FK dependency chain: announcements depends on `departments`
-- (V3) and `users` (V2); notifications depends on `users` (V2) and announcements.
--
-- Inherits utf8mb4 / utf8mb4_unicode_ci from the database default set in V1__baseline.sql;
-- each table still declares ENGINE/CHARSET/COLLATE explicitly, matching V1-V10 style.
-- utf8mb4 matters here for the same reason it does in V6 and V10: announcement bodies and
-- notification messages carry student names and free prose.
--
-- ---------------------------------------------------------------------------------------
-- THE ANNOUNCEMENT / NOTIFICATION SPLIT, AND WHY IT IS TWO TABLES
-- ---------------------------------------------------------------------------------------
-- An announcement is authored ONCE and read by MANY people, each of whom has their own
-- read/unread state. Storing read state on the announcement row is impossible (it is
-- per-reader); storing the announcement text only in the notification rows would lose the
-- authoritative original the admin can later edit, expire or delete. So: the admin writes
-- one `announcements` row, and the service fans it out into one `notifications` row per
-- eligible recipient. `notifications.announcement_id` links them, and the FK cascades -
-- deleting an announcement withdraws it from every notification centre it reached, which
-- is the behaviour an admin expects from "delete announcement".
--
-- ---------------------------------------------------------------------------------------
-- WHAT THIS MIGRATION DELIBERATELY DOES NOT CONTAIN
-- ---------------------------------------------------------------------------------------
--
-- 1. NO WEBSOCKET SESSION / CONNECTION TABLE. Live sockets are in-process state on one
--    application instance; a database row describing "who is currently connected" would be
--    stale the instant the process restarted and would have to be reconciled by nothing.
--    Delivery is: persist the notification row (durable), then best-effort push it to any
--    live socket for that user. A user who was offline sees it on their next page load
--    because the row is already here - that is what makes the notification centre real
--    rather than a UI that only works while the tab is open.
--
-- 2. NO `delivered`/`pushed` FLAG. It could only ever record "we attempted a socket
--    write", which is not delivery, and nothing in §40-§42 reads it. A column whose value
--    cannot be trusted is worse than no column.
--
-- 3. NO PER-USER NOTIFICATION PREFERENCES / MUTE TABLE. §40-§42 does not ask for it, and
--    an unread settings screen that filters nothing is exactly the §69 fake functionality
--    this build forbids. Adding it later is an additive migration.
--
-- 4. NO FK FROM `notifications` TO jobs / placement_applications / interviews /
--    coding_contests / subjects. A notification can be produced by any of those and by
--    announcements too; five nullable FK columns of which four are always NULL is a wide,
--    unenforceable mess, and a notification MUST survive the deletion of the row that
--    caused it (the historical fact "you were rejected for that drive" does not stop being
--    true when the drive is deleted). The polymorphic `reference_type`/`reference_id` pair
--    below is an intentionally SOFT pointer: it is used to build the `link` and nothing
--    reads it as a join key. The single exception is `announcement_id`, which IS a real FK
--    because an ANNOUNCEMENT notification is meaningless without its announcement and must
--    disappear with it.

-- ---------------------------------------------------------------------------------------
-- announcements  (§42: an admin broadcast with audience targeting, priority and expiry)
-- ---------------------------------------------------------------------------------------
-- AUDIENCE TARGETING. `audience` is one of four values and `department_id` is locked to it
-- in BOTH directions by chk_announcements_department_matches_audience below:
--     ALL         -> department_id MUST be NULL. Every authenticated user is a recipient.
--     STUDENTS    -> department_id MUST be NULL. Every user with role STUDENT.
--     FACULTY     -> department_id MUST be NULL. Every user with role FACULTY.
--     DEPARTMENT  -> department_id MUST be NOT NULL. Every student AND every faculty
--                    member whose own department is that one. Admins are not scoped to a
--                    department and are therefore NOT recipients of a DEPARTMENT
--                    announcement; they can still read every announcement through the
--                    admin board.
-- There is no state in which a DEPARTMENT announcement has no department (it would reach
-- nobody and be silently invisible), and none in which a non-DEPARTMENT announcement
-- carries one (it would look scoped in the UI while reaching everyone).
--
-- `body` is MEDIUMTEXT, not TEXT or VARCHAR: an announcement is free-form prose of
-- unbounded length and a truncated broadcast is a silent data-loss bug. Any entity field
-- mapped to a MEDIUMTEXT column in this codebase MUST carry BOTH
-- `columnDefinition = "MEDIUMTEXT"` AND `@JdbcTypeCode(SqlTypes.LONGVARCHAR)` or
-- ddl-auto=validate rejects the mapping at boot - see AIMessage.content,
-- CodingProblem.description and Interview.notes for the established pattern (G8).
--
-- EXPIRY. `expires_at` NULL means "never expires". A non-NULL value must be strictly after
-- `published_at`; an announcement that expires before or at the instant it is published
-- would be created already-invisible, which reads to the admin as a broadcast that
-- silently did nothing. Expiry hides an announcement from the ACTIVE board
-- (published_at <= NOW() AND (expires_at IS NULL OR expires_at > NOW())); it does NOT
-- delete it and does NOT retract notifications that were already delivered, because those
-- were true when they were sent.
--
-- `published_at` is the go-live instant AND the ordering column for every board query. It
-- is set by the service to "now" at creation - there is no scheduled/draft state in §42,
-- so a future published_at is not something any screen can produce. It exists as a
-- separate column from `created_at` because it is the stable anchor the expiry CHECK
-- compares against, while `created_at` is a pure audit timestamp.
--
-- `created_by` is ON DELETE SET NULL for the same reason as in V10: deleting the admin who
-- authored an announcement must not delete the announcement, and SET NULL (unlike
-- RESTRICT) can never block a users-row deletion. `department_id` has NO ON DELETE clause,
-- so it defaults to RESTRICT exactly as students.department_id and faculty.department_id
-- do in V3 - a department with announcements targeted at it cannot be silently removed.
CREATE TABLE announcements (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    title          VARCHAR(200) NOT NULL,
    body           MEDIUMTEXT   NOT NULL,
    audience       VARCHAR(20)  NOT NULL,
    department_id  BIGINT       NULL,
    priority       VARCHAR(10)  NOT NULL DEFAULT 'NORMAL',
    published_at   DATETIME     NOT NULL,
    expires_at     DATETIME     NULL,
    created_by     BIGINT       NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_announcements_department
        FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_announcements_created_by
        FOREIGN KEY (created_by) REFERENCES users (id)
        ON DELETE SET NULL,
    CONSTRAINT chk_announcements_audience
        CHECK (audience IN ('ALL', 'STUDENTS', 'FACULTY', 'DEPARTMENT')),
    CONSTRAINT chk_announcements_priority
        CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    -- Audience and department locked to each other in both directions - see the note above.
    CONSTRAINT chk_announcements_department_matches_audience
        CHECK ((audience = 'DEPARTMENT' AND department_id IS NOT NULL)
               OR (audience <> 'DEPARTMENT' AND department_id IS NULL)),
    -- A blank title or body is an announcement that says nothing while still occupying a
    -- slot in every recipient's notification centre and inflating every unread count.
    CONSTRAINT chk_announcements_title_not_blank
        CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT chk_announcements_body_not_blank
        CHECK (CHAR_LENGTH(TRIM(body)) > 0),
    -- Born-expired announcements cannot be written at all.
    CONSTRAINT chk_announcements_expiry_after_publish
        CHECK (expires_at IS NULL OR expires_at > published_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- THE board query, for every role:
--   WHERE audience IN (...)  [OR (audience = 'DEPARTMENT' AND department_id = ?)]
--     AND published_at <= NOW()
--     AND (expires_at IS NULL OR expires_at > NOW())
--   ORDER BY published_at DESC
-- A student sends ('ALL','STUDENTS'), a faculty member ('ALL','FACULTY'), an admin reads
-- everything. Leftmost equality/IN on audience plus the ordering leg, so the DESC scan is
-- not a filesort. The DEPARTMENT leg of the same query is served by the index InnoDB
-- creates for fk_announcements_department.
CREATE INDEX idx_announcements_audience_published
    ON announcements (audience, published_at);

-- The expiry sweep and the admin "expiring soon" view:
--   WHERE expires_at IS NOT NULL AND expires_at <= ?
-- which is a full scan of the table without this index.
CREATE INDEX idx_announcements_expires_at
    ON announcements (expires_at);

-- ---------------------------------------------------------------------------------------
-- notifications  (§40, §41: one row per recipient per event - the notification centre)
-- ---------------------------------------------------------------------------------------
-- OWNERSHIP IS THE WHOLE SECURITY MODEL OF THIS TABLE. Every row belongs to exactly one
-- user, and `user_id` is the ONLY thing that decides who may read, mark or delete it -
-- over REST and over the WebSocket alike. There is no shared/broadcast row and no
-- "audience" column here on purpose: a broadcast is fanned out into one owned row per
-- recipient at write time, so there is no query anywhere in this module that has to
-- decide at read time whether a caller is allowed to see a row. Every list, count, mark
-- and delete statement MUST carry `AND user_id = :callerUserId` in its WHERE clause -
-- never "load by id, then compare in Java", which is the shape that leaks rows when
-- someone later adds a code path that forgets the comparison.
--
-- READ STATE IS ONE COLUMN. `read_at` NULL means unread; a timestamp means read, and it
-- records WHEN. There is deliberately no companion `is_read` boolean: two columns
-- expressing one fact drift apart, and every "unread count" bug in an application like
-- this one comes from a flag and a timestamp disagreeing. Mark-read sets
-- `read_at = NOW()` only `WHERE read_at IS NULL`, so re-marking an already-read
-- notification is a no-op that cannot rewrite history.
--
-- `type` is the §40 notification type, and every value in the CHECK below is produced by
-- a REAL event in this codebase - there is no placeholder/SYSTEM member, because a type
-- nothing can emit is a §69 dead branch:
--     ANNOUNCEMENT        admin announcement fan-out (this migration's announcements table)
--     PLACEMENT_UPDATE    a drive is posted or opened to a student (Phase 8 JobService)
--     APPLICATION_UPDATE  the caller's own application changed status
--                         (Phase 8 PlacementApplicationService)
--     INTERVIEW_UPDATE    an interview was scheduled, rescheduled or its status changed
--                         (Phase 10 InterviewSchedulingService)
--     CONTEST_UPDATE      a contest the student is registered for opened/changed
--                         (Phase 7 CodingContestService)
--     LEADERBOARD_UPDATE  the student's contest standing moved (Phase 7 ContestScoringService)
--     ATTENDANCE_WARNING  attendance fell below smartcampus.attendance.minimum-percentage
--                         (Phase 4 AttendanceService)
--
-- `reference_type` / `reference_id` are a SOFT, deliberately un-FK'd pointer at the row
-- that caused the notification (see note 4 in the header). They are written together or
-- not at all - a reference_id with no type cannot be resolved and a type with no id points
-- nowhere, so chk_notifications_reference_pair forbids both half-states. `link` is the
-- frontend route the notification navigates to when clicked; it is nullable, and a NULL
-- link renders as a non-clickable item rather than a button that goes nowhere (§69).
--
-- `dedupe_key` + uk_notifications_user_dedupe is the IDEMPOTENCE mechanism, and it is why
-- re-running a fan-out, re-marking a roster or recomputing a leaderboard cannot flood a
-- user's centre with duplicates. A NULL dedupe_key opts out (MySQL unique indexes do not
-- compare NULLs, so any number of rows may have one), which is correct for genuinely
-- repeatable events. Producers that must fire at most once per logical event set it, e.g.
--     announcement:<announcementId>
--     application:<applicationId>:<newStatus>
--     interview:<interviewId>:<statusOrRescheduleInstant>
--     attendance-warning:<subjectId>:<academicYear>:<semester>
-- The insert path MUST catch DataIntegrityViolationException on this key and treat it as
-- "already notified" - success, not an error, and never a 500 reaching the client.
--
-- `user_id` cascades: a deleted account's notifications are meaningless and must not
-- outlive it. `announcement_id` cascades for the reason in the header note.
CREATE TABLE notifications (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    type             VARCHAR(30)  NOT NULL,
    title            VARCHAR(200) NOT NULL,
    message          MEDIUMTEXT   NOT NULL,
    priority         VARCHAR(10)  NOT NULL DEFAULT 'NORMAL',
    link             VARCHAR(500) NULL,
    reference_type   VARCHAR(30)  NULL,
    reference_id     BIGINT       NULL,
    announcement_id  BIGINT       NULL,
    dedupe_key       VARCHAR(150) NULL,
    read_at          DATETIME     NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- The idempotence key described above. Scoped to the user, so two different recipients
    -- of the same announcement do not collide with each other.
    UNIQUE KEY uk_notifications_user_dedupe (user_id, dedupe_key),
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_notifications_announcement
        FOREIGN KEY (announcement_id) REFERENCES announcements (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_notifications_type
        CHECK (type IN ('ANNOUNCEMENT', 'PLACEMENT_UPDATE', 'APPLICATION_UPDATE',
                        'INTERVIEW_UPDATE', 'CONTEST_UPDATE', 'LEADERBOARD_UPDATE',
                        'ATTENDANCE_WARNING')),
    CONSTRAINT chk_notifications_priority
        CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT chk_notifications_title_not_blank
        CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT chk_notifications_message_not_blank
        CHECK (CHAR_LENGTH(TRIM(message)) > 0),
    CONSTRAINT chk_notifications_reference_type
        CHECK (reference_type IS NULL
               OR reference_type IN ('ANNOUNCEMENT', 'JOB', 'PLACEMENT_APPLICATION',
                                     'INTERVIEW', 'CONTEST', 'SUBJECT')),
    -- Half a pointer is not a pointer.
    CONSTRAINT chk_notifications_reference_pair
        CHECK ((reference_type IS NULL AND reference_id IS NULL)
               OR (reference_type IS NOT NULL AND reference_id IS NOT NULL)),
    -- Type and announcement locked to each other in both directions: an ANNOUNCEMENT
    -- notification always resolves to a real announcements row (and disappears with it via
    -- the cascading FK above), and no other type may claim to be one.
    CONSTRAINT chk_notifications_announcement_link
        CHECK ((type = 'ANNOUNCEMENT' AND announcement_id IS NOT NULL)
               OR (type <> 'ANNOUNCEMENT' AND announcement_id IS NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- THE notification-centre list query - "my notifications, newest first", paged:
--   WHERE user_id = ? ORDER BY created_at DESC, id DESC
-- Leftmost equality on the owning user plus the ordering leg. MySQL scans an ascending
-- index backwards for a DESC sort, so no descending index is needed.
CREATE INDEX idx_notifications_user_created
    ON notifications (user_id, created_at);

-- THE unread-count query, which the bell polls/refreshes on every push:
--   SELECT COUNT(*) FROM notifications WHERE user_id = ? AND read_at IS NULL
-- and the "unread only" tab, which adds ORDER BY created_at DESC. Both legs are covered,
-- so the count is answered from the index without touching a row. This also serves
-- mark-all-read, which is UPDATE ... WHERE user_id = ? AND read_at IS NULL.
CREATE INDEX idx_notifications_user_unread
    ON notifications (user_id, read_at, created_at);

-- The centre's type filter ("show me only placement updates"):
--   WHERE user_id = ? AND type = ? ORDER BY created_at DESC
-- which neither index above can serve once type is pinned.
CREATE INDEX idx_notifications_user_type_created
    ON notifications (user_id, type, created_at);
