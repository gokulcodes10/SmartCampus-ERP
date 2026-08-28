-- V2__auth.sql  --  SmartCampus ERP authentication schema (Phase 2, Authentication)
--
-- Creates the two tables the authentication module owns:
--   users                  - one row per account (student/faculty/admin). Created at
--                             registration (self-service for STUDENT only, per
--                             PROJECT_PLAN.md clarification G1 - faculty/admin are
--                             admin-provisioned) and read by JwtAuthenticationFilter
--                             on every authenticated request.
--   password_reset_tokens  - single-use, expiring OTP tokens for the forgot-password
--                             flow (Phase 2 scope, built by the OTP-flow owner on top
--                             of this table). Only a hash of the OTP is ever stored,
--                             never the plaintext code, and each row tracks how many
--                             verification attempts have been made against it so the
--                             flow can cap retries and stay non-enumerating.
--
-- Inherits utf8mb4 / utf8mb4_unicode_ci from the database default set in V1. Under
-- utf8mb4_unicode_ci, string comparison (and therefore the unique index below) is
-- already case-insensitive, so email lookups do not need a separate lower-cased column.

CREATE TABLE users (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    email       VARCHAR(255) NOT NULL,
    password    VARCHAR(100) NOT NULL,
    full_name   VARCHAR(150) NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    enabled     TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    CONSTRAINT chk_users_role CHECK (role IN ('STUDENT', 'FACULTY', 'ADMIN'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- idx_users_role backs admin screens that list/filter accounts by role (Phase 3) and
-- any "pending student activation" queries (G1), both of which filter on role rather
-- than looking up a single row the way the email unique index already serves.
CREATE INDEX idx_users_role ON users (role);

CREATE TABLE password_reset_tokens (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    token_hash     VARCHAR(255) NOT NULL,
    expires_at     DATETIME     NOT NULL,
    used           TINYINT(1)   NOT NULL DEFAULT 0,
    attempt_count  INT          NOT NULL DEFAULT 0,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_password_reset_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- The verify-OTP query looks up "the live token for this user matching this hash":
-- WHERE user_id = ? AND token_hash = ? AND used = 0 AND expires_at > NOW(). The
-- composite index below serves that lookup directly.
CREATE INDEX idx_password_reset_tokens_user_token ON password_reset_tokens (user_id, token_hash);

-- Supports a periodic cleanup job deleting expired rows without a full table scan.
CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens (expires_at);
