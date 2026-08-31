-- V9__resume.sql  --  SmartCampus ERP resume builder schema (Phase 9, Resume)
--
-- Creates the seven tables the resume module owns, and adds the ONE column
-- V8__placement.sql deliberately left for this migration:
--
--   resumes                 - one SAVED VERSION of a student's resume: the header/contact
--                             block, the free-text summary, the chosen §37 template, and
--                             the lock that freezes a version once it has been attached to
--                             a placement application. "Multiple saved versions" means
--                             multiple rows here, one per version, each independently
--                             renderable to PDF.
--   resume_educations       -\
--   resume_experiences       |
--   resume_projects          |- the six §37 SECTION tables. Each row belongs to exactly
--   resume_certifications    |  one resume version and carries its own display_order, so
--   resume_skills            |  the student controls the order entries appear in the PDF.
--   resume_achievements     -/
--
--   placement_applications.resume_id  - the §35 attachment. V8__placement.sql explicitly
--                             deferred this column to V9 ("NO `resume_id` ON
--                             `placement_applications` ... Phase 9 owns adding
--                             `resume_id BIGINT NULL` plus its FK to this table in V9"),
--                             because a FK to a table that does not exist yet fails the
--                             migration and a bare BIGINT with no FK is an unpoliced
--                             pointer.
--
-- Creation order follows the FK dependency chain: resumes (-> students, V3) first, then
-- the six section tables (-> resumes), then the ALTER on placement_applications (V8).
--
-- Inherits utf8mb4 / utf8mb4_unicode_ci from the database default set in V1__baseline.sql;
-- each table still declares ENGINE/CHARSET/COLLATE explicitly, matching V1-V8 and V10-V11
-- style. Note that utf8mb4_unicode_ci is CASE-INSENSITIVE, which is what makes the unique
-- key on (resume_id, name) in resume_skills reject 'Java' next to 'java'.
--
-- ORDER OF ARRIVAL. V10 and V11 are already applied in the development database, so this
-- migration lands out of numeric order. That is expected and supported:
-- `spring.flyway.out-of-order` is true (see application.properties). Unlike every earlier
-- phase this migration DOES alter an earlier phase's table, but only additively and only
-- a table created by V8 - which is below V9 - so a rebuild from an empty schema applies
-- V8 before V9 and the ALTER always finds its target.

-- ---------------------------------------------------------------------------------------
-- WHAT THIS MIGRATION DELIBERATELY DOES NOT CONTAIN
-- ---------------------------------------------------------------------------------------
--
-- 1. NO STORED PDF BYTES. There is no BLOB column anywhere here and no `resume_pdfs`
--    table. The PDF is generated on demand by ResumePdfService (OpenPDF, clarification
--    G9) from the rows below, every time it is requested, by ONE code path shared by the
--    student's download, the student's preview and the admin's download from the
--    applicant list. Storing the bytes would create a second copy of the resume that
--    silently diverges from the rows the student edits, and it is the divergence - not
--    the storage - that §35 actually cares about. `locked_at` (below) is what makes the
--    rendered artifact stable instead.
--
-- 2. NO `is_default` / `is_primary` FLAG ON `resumes`. "Which resume do I attach?" is
--    answered at apply time by an explicit choice in the apply form, not by a stored
--    preference, so a default flag would be a second source of truth for a decision the
--    student makes per application anyway. The apply dialog preselects the most recently
--    updated version (idx_resumes_student_updated below serves exactly that query).
--
-- 3. NO CGPA / GRADUATION-YEAR COLUMN ON `resumes`. Both are derivable live - CGPA from
--    the Phase 5 MarksService/GradeCalculationService chain (G7), graduation year from
--    students.admission_year + ceil(courses.duration_semesters / 2), exactly as
--    V8__placement.sql documents. The PREFILL endpoint reads those live and hands them to
--    the student as suggested values; whatever the student then saves lands in
--    resume_educations.grade_value / .end_year as their own asserted content. A resume is
--    a document the student authors, so the saved copy is intentionally a snapshot of
--    what they wrote, not a live mirror of the academic record.
--
-- 4. NO `institution` VALUE THE SYSTEM CAN INVENT. This application stores departments and
--    courses but nowhere stores the name of the college itself, so `institution` is
--    NOT NULL with no default and prefill leaves it EMPTY for the student to type. Filling
--    it with a placeholder such as "My College" would be exactly the §69 fabricated
--    content this build forbids.
--
-- 5. NO SEPARATE `resume_versions` TABLE. A version IS a `resumes` row. A parent/child
--    version tree would add a second identity for every resume with no query that needs
--    it; "duplicate this version" copies a row plus its section rows and returns a new id.

-- ---------------------------------------------------------------------------------------
-- resumes  (§37: one saved version - header block, summary, template, lock)
-- ---------------------------------------------------------------------------------------
-- `student_id` cascades from `students` (which itself cascades from `users`): a resume has
-- no meaning without the student who wrote it. In practice a student row with placement
-- applications can never be deleted anyway - fk_placement_applications_student in V8 has
-- no ON DELETE clause and refuses first - so this cascade only ever fires for a student
-- who has no placement history at all.
--
-- `full_name` and `email` are COPIES of users.full_name / users.email at the moment the
-- resume was created, not a live join, and that is deliberate: a resume is a document, and
-- a student may legitimately present a different form of their name ("R. Kumar") or a
-- personal email address on it. Prefill seeds them from the account; the student may then
-- change them. They are NOT NULL because a resume with no name and no contact address is
-- not a resume - the PDF header would be blank.
--
-- `phone` is NOT prefilled from anywhere: the `users` table has no phone column (see
-- V2__auth.sql), so this is a field the student fills in, and it stays NULL until they do.
--
-- `template` is the §37 template selection. It changes ONLY how ResumePdfService lays the
-- same rows out; it never changes what data exists. The three values are the three
-- layouts that service implements, and the CHECK constraint is what stops a fourth value
-- reaching a renderer that has no branch for it.
--
-- `locked_at` IS THE §35 ARTIFACT GUARANTEE. It is NULL for a version that has never been
-- attached to a placement application, and carries the instant of the first attachment
-- afterwards. Once set it is NEVER cleared - not on withdrawal, not on rejection - because
-- the whole point is that re-rendering the resume six months later reproduces the document
-- the recruiter was actually sent. A locked version is READ-ONLY: ResumeService refuses
-- every update and every delete against it (409) and offers "duplicate" instead, which
-- creates a fresh unlocked version the student can keep editing. MySQL cannot express
-- "reject UPDATEs on this row when locked_at IS NOT NULL" without a trigger, and a trigger
-- would be invisible to Hibernate, so THAT half of the rule lives in ResumeService and
-- must be re-checked on every write path - including writes to the six section tables,
-- which are reached through their resume.
--
-- uk_resumes_id_student exists ONLY to be the parent key of the composite foreign key
-- added to placement_applications at the bottom of this file. It is the mechanism that
-- makes "student A attaches student B's resume" impossible in the DATABASE rather than
-- only in a Java ownership check. See that constraint for the full explanation.
CREATE TABLE resumes (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    student_id     BIGINT       NOT NULL,
    title          VARCHAR(150) NOT NULL,
    template       VARCHAR(20)  NOT NULL DEFAULT 'CLASSIC',
    full_name      VARCHAR(150) NOT NULL,
    email          VARCHAR(255) NOT NULL,
    phone          VARCHAR(20)  NULL,
    location       VARCHAR(150) NULL,
    linkedin_url   VARCHAR(255) NULL,
    github_url     VARCHAR(255) NULL,
    portfolio_url  VARCHAR(255) NULL,
    summary        MEDIUMTEXT   NULL,
    locked_at      DATETIME     NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- Versions are chosen from a list by NAME, at the moment of applying to a drive. Two
    -- versions called "Software Engineer" are indistinguishable in that dropdown and the
    -- student cannot tell which one they just sent. This also absorbs the realistic
    -- accident - the "Save" button pressed twice - the same way uk_jobs_* does in V8.
    -- It doubles as the index for "all of my resumes" (leftmost prefix student_id).
    UNIQUE KEY uk_resumes_student_title (student_id, title),
    -- Parent key for fk_placement_applications_resume. Never queried directly.
    UNIQUE KEY uk_resumes_id_student (id, student_id),
    CONSTRAINT fk_resumes_student
        FOREIGN KEY (student_id) REFERENCES students (id)
        ON DELETE CASCADE,
    -- The three §37 layouts ResumePdfService actually implements.
    CONSTRAINT chk_resumes_template
        CHECK (template IN ('CLASSIC', 'MODERN', 'COMPACT')),
    -- A blank title is unselectable in the apply dropdown; a blank name or email renders
    -- an anonymous PDF. Enforced here as well as by @NotBlank so no path can write one.
    CONSTRAINT chk_resumes_title_not_blank
        CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT chk_resumes_full_name_not_blank
        CHECK (CHAR_LENGTH(TRIM(full_name)) > 0),
    CONSTRAINT chk_resumes_email_not_blank
        CHECK (CHAR_LENGTH(TRIM(email)) > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- "My resumes, most recently edited first" - the resume list screen and the preselection
-- the apply dialog makes: WHERE student_id = ? ORDER BY updated_at DESC.
-- uk_resumes_student_title leads with student_id but its second column is title, so it
-- cannot serve the ordering leg without a filesort.
CREATE INDEX idx_resumes_student_updated ON resumes (student_id, updated_at);

-- ---------------------------------------------------------------------------------------
-- The six section tables
-- ---------------------------------------------------------------------------------------
-- All six share the same shape and the same rules, stated once here:
--
--   * `resume_id` is NOT NULL and ON DELETE CASCADE. A section row is meaningless without
--     its resume, and deleting a resume must not leave orphans behind. ResumeService still
--     deletes children explicitly before the parent (see the contract) so that Hibernate's
--     first-level cache never holds rows the database has already removed underneath it.
--
--   * `display_order` is the student's chosen order within the section, NOT a unique key.
--     It is not unique on purpose: making it unique turns a simple two-row swap into a
--     three-step dance through a temporary value, for no benefit. Duplicates are therefore
--     possible, so EVERY query and every renderer MUST sort by
--     `ORDER BY display_order ASC, id ASC` - the trailing id is what makes the order
--     deterministic when two rows share a display_order. Sorting by display_order alone
--     produces a PDF whose section order changes between renders.
--
--   * `idx_<table>_resume_order (resume_id, display_order)` is the index for the ONLY
--     query these tables ever serve - "every row of this section for this resume, in
--     order" - and it also satisfies InnoDB's requirement that the FK column be indexed,
--     so no separate single-column index is created.
--
--   * Every free-prose column is MEDIUMTEXT rather than VARCHAR. Any entity field mapped
--     to a MEDIUMTEXT column in this codebase MUST carry both
--     `columnDefinition = "MEDIUMTEXT"` and `@JdbcTypeCode(SqlTypes.LONGVARCHAR)` or
--     `ddl-auto=validate` rejects it at boot - see AIMessage.content and
--     InterviewQuestion.question for the established pattern (G8).

-- --- resume_educations -------------------------------------------------------------------
-- The grade is stored as a NUMBER PLUS ITS SCALE, not as free text like "8.75/10". Free
-- text cannot be validated, cannot be prefilled from the live CGPA without string
-- formatting, and cannot be range-checked - and a resume claiming a CGPA of 87.5 (a
-- percentage typed into a CGPA field) is the single most common data error in this form.
-- The pair is all-or-nothing: a value with no scale is unreadable ("8.75" of what?) and a
-- scale with no value is noise, so chk_resume_educations_grade_pair forbids both halves.
-- The range check is then per-scale, which a single BETWEEN could not express.
CREATE TABLE resume_educations (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    resume_id       BIGINT        NOT NULL,
    institution     VARCHAR(200)  NOT NULL,
    degree          VARCHAR(150)  NULL,
    field_of_study  VARCHAR(150)  NULL,
    start_year      INT           NULL,
    end_year        INT           NULL,
    grade_value     DECIMAL(5, 2) NULL,
    grade_scale     VARCHAR(20)   NULL,
    display_order   INT           NOT NULL DEFAULT 0,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_resume_educations_resume
        FOREIGN KEY (resume_id) REFERENCES resumes (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_resume_educations_institution_not_blank
        CHECK (CHAR_LENGTH(TRIM(institution)) > 0),
    CONSTRAINT chk_resume_educations_display_order
        CHECK (display_order >= 0),
    -- A typo'd year ("20223") silently sorts an entry to the end of the section and prints
    -- garbage in the PDF. The window is wide enough for any real education history.
    CONSTRAINT chk_resume_educations_start_year_range
        CHECK (start_year IS NULL OR (start_year >= 1950 AND start_year <= 2100)),
    CONSTRAINT chk_resume_educations_end_year_range
        CHECK (end_year IS NULL OR (end_year >= 1950 AND end_year <= 2100)),
    -- end_year may equal start_year (a one-year programme) but never precede it.
    CONSTRAINT chk_resume_educations_year_order
        CHECK (start_year IS NULL OR end_year IS NULL OR end_year >= start_year),
    CONSTRAINT chk_resume_educations_grade_scale
        CHECK (grade_scale IS NULL OR grade_scale IN ('CGPA', 'PERCENTAGE')),
    CONSTRAINT chk_resume_educations_grade_pair
        CHECK ((grade_value IS NULL AND grade_scale IS NULL)
               OR (grade_value IS NOT NULL AND grade_scale IS NOT NULL)),
    CONSTRAINT chk_resume_educations_grade_range
        CHECK (grade_value IS NULL
               OR (grade_scale = 'CGPA' AND grade_value >= 0 AND grade_value <= 10)
               OR (grade_scale = 'PERCENTAGE' AND grade_value >= 0 AND grade_value <= 100))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_resume_educations_resume_order
    ON resume_educations (resume_id, display_order);

-- --- resume_experiences ------------------------------------------------------------------
-- `is_current` and `end_date` are locked to each other in BOTH directions, so there is no
-- row from which the renderer cannot tell what to print:
--   * a CURRENT role must have no end date  - otherwise "Jan 2025 - Mar 2025 (Present)";
--   * a PAST role must have one             - otherwise "Jan 2025 - " with a dangling dash.
-- Together they make "Present" a fact about the row rather than a guess by the renderer.
CREATE TABLE resume_experiences (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    resume_id        BIGINT       NOT NULL,
    company_name     VARCHAR(200) NOT NULL,
    role_title       VARCHAR(150) NOT NULL,
    location         VARCHAR(150) NULL,
    employment_type  VARCHAR(20)  NULL,
    start_date       DATE         NOT NULL,
    end_date         DATE         NULL,
    is_current       TINYINT(1)   NOT NULL DEFAULT 0,
    description      MEDIUMTEXT   NULL,
    display_order    INT          NOT NULL DEFAULT 0,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_resume_experiences_resume
        FOREIGN KEY (resume_id) REFERENCES resumes (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_resume_experiences_company_not_blank
        CHECK (CHAR_LENGTH(TRIM(company_name)) > 0),
    CONSTRAINT chk_resume_experiences_role_not_blank
        CHECK (CHAR_LENGTH(TRIM(role_title)) > 0),
    CONSTRAINT chk_resume_experiences_display_order
        CHECK (display_order >= 0),
    CONSTRAINT chk_resume_experiences_employment_type
        CHECK (employment_type IS NULL
               OR employment_type IN ('INTERNSHIP', 'FULL_TIME', 'PART_TIME',
                                      'FREELANCE', 'VOLUNTEER')),
    CONSTRAINT chk_resume_experiences_date_order
        CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT chk_resume_experiences_current_has_no_end
        CHECK (is_current = 0 OR end_date IS NULL),
    CONSTRAINT chk_resume_experiences_past_has_end
        CHECK (is_current = 1 OR end_date IS NOT NULL)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_resume_experiences_resume_order
    ON resume_experiences (resume_id, display_order);

-- --- resume_projects ---------------------------------------------------------------------
-- Both dates are optional here, unlike an experience entry: students routinely list a
-- personal project with no meaningful start date. When both are present the order check
-- still applies.
CREATE TABLE resume_projects (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    resume_id       BIGINT       NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     MEDIUMTEXT   NULL,
    tech_stack      VARCHAR(255) NULL,
    project_url     VARCHAR(255) NULL,
    repository_url  VARCHAR(255) NULL,
    start_date      DATE         NULL,
    end_date        DATE         NULL,
    display_order   INT          NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_resume_projects_resume
        FOREIGN KEY (resume_id) REFERENCES resumes (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_resume_projects_name_not_blank
        CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_resume_projects_display_order
        CHECK (display_order >= 0),
    CONSTRAINT chk_resume_projects_date_order
        CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_resume_projects_resume_order
    ON resume_projects (resume_id, display_order);

-- --- resume_certifications ---------------------------------------------------------------
-- `expiry_date` NULL means "does not expire", which is the common case, so it carries no
-- separate boolean flag - a flag would immediately be able to disagree with the date.
CREATE TABLE resume_certifications (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    resume_id       BIGINT       NOT NULL,
    name            VARCHAR(200) NOT NULL,
    issuer          VARCHAR(200) NULL,
    issue_date      DATE         NULL,
    expiry_date     DATE         NULL,
    credential_id   VARCHAR(120) NULL,
    credential_url  VARCHAR(255) NULL,
    display_order   INT          NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_resume_certifications_resume
        FOREIGN KEY (resume_id) REFERENCES resumes (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_resume_certifications_name_not_blank
        CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_resume_certifications_display_order
        CHECK (display_order >= 0),
    CONSTRAINT chk_resume_certifications_date_order
        CHECK (issue_date IS NULL OR expiry_date IS NULL OR expiry_date >= issue_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_resume_certifications_resume_order
    ON resume_certifications (resume_id, display_order);

-- --- resume_skills -----------------------------------------------------------------------
-- The unique key is the point of this table. "Java" listed twice on one resume is always a
-- defect - it prints twice - and it is the exact thing an add-form with no dedupe produces
-- when the student scrolls back up. Because the collation is utf8mb4_unicode_ci, the key
-- also rejects 'Java' next to 'java' and 'JAVA', which a Java-side `contains()` on raw
-- strings would happily allow through.
--
-- `category` groups the skills into the PDF's sub-headings; LANGUAGE means a spoken
-- language (programming languages are TECHNICAL). `proficiency` is NULLABLE because a
-- student who does not want to self-rate must be able to leave it out rather than be
-- forced to assert a level they do not mean - and NULL prints nothing at all.
CREATE TABLE resume_skills (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    resume_id      BIGINT       NOT NULL,
    name           VARCHAR(100) NOT NULL,
    category       VARCHAR(20)  NOT NULL DEFAULT 'TECHNICAL',
    proficiency    VARCHAR(20)  NULL,
    display_order  INT          NOT NULL DEFAULT 0,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_resume_skills_resume_name (resume_id, name),
    CONSTRAINT fk_resume_skills_resume
        FOREIGN KEY (resume_id) REFERENCES resumes (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_resume_skills_name_not_blank
        CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_resume_skills_display_order
        CHECK (display_order >= 0),
    CONSTRAINT chk_resume_skills_category
        CHECK (category IN ('TECHNICAL', 'TOOL', 'LANGUAGE', 'SOFT')),
    CONSTRAINT chk_resume_skills_proficiency
        CHECK (proficiency IS NULL
               OR proficiency IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- uk_resume_skills_resume_name already indexes resume_id, but its second column is `name`,
-- so the ordered read still needs this one.
CREATE INDEX idx_resume_skills_resume_order
    ON resume_skills (resume_id, display_order);

-- --- resume_achievements -----------------------------------------------------------------
CREATE TABLE resume_achievements (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    resume_id      BIGINT       NOT NULL,
    title          VARCHAR(200) NOT NULL,
    description    MEDIUMTEXT   NULL,
    issuer         VARCHAR(200) NULL,
    achieved_on    DATE         NULL,
    display_order  INT          NOT NULL DEFAULT 0,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_resume_achievements_resume
        FOREIGN KEY (resume_id) REFERENCES resumes (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_resume_achievements_title_not_blank
        CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT chk_resume_achievements_display_order
        CHECK (display_order >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_resume_achievements_resume_order
    ON resume_achievements (resume_id, display_order);

-- ---------------------------------------------------------------------------------------
-- placement_applications.resume_id  (§35: the resume attached to an application)
-- ---------------------------------------------------------------------------------------
-- NULLABLE, because §35 does not require a resume to apply and because every application
-- that already exists was submitted before this column did. NULL means "no resume
-- attached", never "resume missing".
--
-- THE COMPOSITE FOREIGN KEY IS THE POINT. It references resumes (id, student_id) - not
-- resumes (id) - reusing THIS table's own `student_id` column as the second leg. The
-- effect is that MySQL itself refuses an application row whose resume belongs to a
-- different student: attaching resume 42 (owned by student 7) to student 9's application
-- has no matching parent row and the INSERT/UPDATE fails. A Java ownership check is still
-- required in PlacementApplicationService so the caller gets a clean 404/403 instead of a
-- 500, but the check is no longer the only thing standing between one student and another
-- student's resume.
--
-- MySQL applies MATCH SIMPLE semantics to a composite FK: when ANY referenced column is
-- NULL the constraint is not checked at all. resume_id is the only nullable leg, so
-- "no resume attached" passes, and any non-NULL resume_id is fully checked. That is
-- exactly the behaviour wanted here, and it is why the FK can be added to a table that
-- already holds rows.
--
-- There is deliberately NO ON DELETE clause, so the FK is RESTRICT: deleting a resume that
-- an application points at is refused by the database. ResumeService checks first and
-- returns 409 with an explanation, but the constraint is what makes the guarantee. A
-- submitted application must always be able to re-render the exact document that was sent
-- with it - that is the §35 requirement this column exists to satisfy, and it is also why
-- ResumeService sets resumes.locked_at on attach and refuses all further edits to that
-- version.
--
-- Interaction with fk_resumes_student's ON DELETE CASCADE: deleting a student would try to
-- cascade into `resumes` and be blocked here. It never gets that far in practice -
-- fk_placement_applications_student (V8) has no ON DELETE clause and refuses the student
-- delete outright - and students are deactivated rather than deleted anyway (G1).
ALTER TABLE placement_applications
    ADD COLUMN resume_id BIGINT NULL AFTER cover_note;

ALTER TABLE placement_applications
    ADD CONSTRAINT fk_placement_applications_resume
        FOREIGN KEY (resume_id, student_id) REFERENCES resumes (id, student_id);

-- The admin applicant list renders a "Resume" link per row and therefore reads resume_id
-- for a whole page of applications; the student's own list does the same. The FK above
-- already required an index on (resume_id, student_id) and InnoDB would have created one
-- implicitly under the constraint's name - this names it explicitly instead, so the schema
-- reads the same on every MySQL version and the reverse lookup "which applications used
-- resume X?" (the 409 check in ResumeService#delete) has a declared index to use.
CREATE INDEX idx_placement_applications_resume
    ON placement_applications (resume_id, student_id);
