-- V1__baseline.sql  --  SmartCampus ERP schema baseline (Phase 1, Foundation)
--
-- This migration deliberately creates NO domain tables. Phase 1 is the foundation
-- only; the domain model arrives later and each phase brings its own migration:
--   Phase 2 -> users, password reset tokens
--   Phase 3 -> departments, courses, subjects, students, faculty, enrollments,
--              faculty_subject_assignments
--   Phase 4 -> attendance, exams, marks, grade bands
--   Phases 5-11 -> analytics config, AI conversations, coding, placement, resume,
--              interview and notification tables
-- Creating any of those tables here would collide with the phase that actually owns
-- and defines them, so this baseline does not speculate about their columns.
--
-- What it does do is real and verifiable: it fixes the database-wide character set
-- and collation that every later table inherits, and records a single marker row
-- proving the migration ran. `SELECT * FROM schema_info;` after startup is the proof
-- that Flyway is wired correctly end to end.

-- Every table created from here on inherits utf8mb4 / utf8mb4_unicode_ci unless it
-- overrides them. utf8mb4 is required for full Unicode (student names, AI responses,
-- source code submitted to the coding module).
ALTER DATABASE CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE schema_info (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    schema_name     VARCHAR(64)  NOT NULL,
    application     VARCHAR(64)  NOT NULL,
    baseline_phase  VARCHAR(32)  NOT NULL,
    description     VARCHAR(255) NOT NULL,
    initialized_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_schema_info_schema_name (schema_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

INSERT INTO schema_info (schema_name, application, baseline_phase, description)
VALUES ('smartcampus',
        'SmartCampus ERP',
        'Phase 1 - Foundation',
        'Flyway baseline. Schema created with utf8mb4/utf8mb4_unicode_ci; no domain tables yet.');
