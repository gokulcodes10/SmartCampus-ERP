-- V8__placement.sql  --  SmartCampus ERP placement schema (Phase 8, Placement)
--
-- Creates the four tables the placement module owns:
--   companies                 - a recruiting organisation. Reference data an admin
--                                maintains; every drive hangs off one of these rows.
--   jobs                      - one placement drive / job posting, carrying BOTH the
--                                posting details and the §34 ELIGIBILITY CRITERIA
--                                (minimum CGPA, minimum marks percentage, required
--                                graduation year), plus the §35/§53 application
--                                deadline.
--   job_eligible_departments  - the department leg of the eligibility criteria, as an
--                                explicit child table rather than a @ManyToMany. An
--                                EMPTY set means "every department is eligible"; any
--                                row present narrows the drive to exactly the listed
--                                departments.
--   placement_applications    - a student's application to one drive. The unique key on
--                                (job_id, student_id) IS the §35 duplicate-application
--                                guard, enforced by the database rather than only by a
--                                Java `existsBy...` check that races itself under
--                                concurrent submits.
--
-- Creation order follows the FK dependency chain: companies -> jobs ->
-- job_eligible_departments / placement_applications. `jobs` additionally references
-- `users` (V2) and `job_eligible_departments` references `departments` (V3);
-- `placement_applications` references `students` (V3) and `users` (V2).
--
-- Inherits utf8mb4 / utf8mb4_unicode_ci from the database default set in V1__baseline.sql;
-- each table still declares ENGINE/CHARSET/COLLATE explicitly, matching V1-V7 style.
--
-- ---------------------------------------------------------------------------------------
-- WHAT THIS MIGRATION DELIBERATELY DOES NOT CONTAIN
-- ---------------------------------------------------------------------------------------
--
-- 1. NO CGPA COLUMN ON `students`, AND NO PLACEMENT-SIDE GPA CACHE. §34 evaluates a
--    student's CGPA, and the only correct source of that number is the Phase 5
--    AnalyticsService -> MarksService -> GradeCalculationService chain, which derives it
--    credit-weighted from `marks`/`exams`/`subjects` (G7). Storing a second copy of CGPA
--    here would immediately become a staler, divergent answer to the same question, and
--    a student's eligibility would silently depend on which copy was read. The eligibility
--    engine calls Phase 5 live.
--
--    The ONE exception is `placement_applications.cgpa_at_application` /
--    `.percentage_at_application` below, and it is not a cache: it is a HISTORICAL RECORD
--    of the figures that justified accepting the application, written once at insert and
--    never refreshed. It is read by the admin applicant view and by nothing that decides
--    eligibility.
--
-- 2. NO GRADUATION-YEAR COLUMN ON `students`. A student's graduation year is already
--    fully determined by data V3 stores: `students.admission_year` +
--    ceil(`courses.duration_semesters` / 2). Adding a stored column would create a
--    second source of truth that an admin could set inconsistently with the course the
--    student is actually enrolled in. Students whose `admission_year` or `course_id` is
--    NULL (i.e. still PENDING per G1) have NO derivable graduation year, and the
--    eligibility engine must report that as an explicit, honest reason
--    (GRADUATION_YEAR_UNKNOWN) rather than guessing a year or defaulting to eligible.
--
-- 3. NO `resume_id` ON `placement_applications`. §35 wants a resume attachable to an
--    application, but `resumes` does not exist until V9__resume.sql (Phase 9). A FK to a
--    table that does not exist yet fails the migration; a nullable BIGINT with no FK is
--    an unpoliced pointer. Phase 9 owns adding `resume_id BIGINT NULL` plus its FK to
--    this table in V9 - that is an additive ALTER on an empty-or-small table and is the
--    correct place for it.
--
-- 4. NO BACKLOG / ARREAR COLUMNS. Real placement drives filter on backlogs, but this
--    application has no data anywhere from which a backlog count could be truthfully
--    derived, and §34 does not ask for it. A column that could only ever be filled with
--    an invented number is exactly the §69 "fake functionality" this build forbids.

-- ---------------------------------------------------------------------------------------
-- companies  (§33: the recruiting organisations an admin maintains)
-- ---------------------------------------------------------------------------------------
-- `status` is a soft-delete/visibility flag, not a workflow: INACTIVE means "we no longer
-- recruit with them", and it exists because a company with historical drives and
-- applications must never be hard-deleted - the applications that reference those drives
-- are a permanent record. CompanyService therefore refuses a DELETE when any `jobs` row
-- points at the company (409) and offers deactivation instead; the FK below has no
-- ON DELETE clause precisely so the database refuses it too if that check is ever bypassed.
--
-- `description` is MEDIUMTEXT rather than VARCHAR because a company profile is free-form
-- prose of unbounded length. Any entity field mapped to a MEDIUMTEXT column in this
-- codebase MUST carry both `columnDefinition = "MEDIUMTEXT"` and
-- `@JdbcTypeCode(SqlTypes.LONGVARCHAR)` or `ddl-auto=validate` rejects it - see
-- AIMessage.content and CodingContest.description for the established pattern (G8).
CREATE TABLE companies (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(150) NOT NULL,
    industry        VARCHAR(100) NULL,
    website         VARCHAR(255) NULL,
    description     MEDIUMTEXT   NULL,
    location        VARCHAR(150) NULL,
    contact_person  VARCHAR(120) NULL,
    contact_email   VARCHAR(255) NULL,
    contact_phone   VARCHAR(20)  NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- One row per recruiting organisation. Two "Infosys" rows would split that company's
    -- drives and its placement statistics across two identities, which is precisely the
    -- kind of duplicate the admin screen cannot detect after the fact.
    UNIQUE KEY uk_companies_name (name),
    CONSTRAINT chk_companies_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- The admin company list is "ACTIVE companies, alphabetical" with an optional name
-- search: WHERE status = ? [AND name LIKE ?] ORDER BY name. uk_companies_name serves
-- the ordering alone but cannot serve it once status is pinned, because status is not
-- in that key; this index carries both legs.
CREATE INDEX idx_companies_status_name ON companies (status, name);

-- ---------------------------------------------------------------------------------------
-- jobs  (§33-§35: one placement drive, its posting details, its §34 eligibility criteria
-- and its §53 application deadline)
-- ---------------------------------------------------------------------------------------
-- ELIGIBILITY CRITERIA. The four criterion columns/relations are, in evaluation order:
--
--   min_cgpa               NULL = "no CGPA requirement". A non-NULL value is compared
--                          against the student's LIVE credit-weighted CGPA from
--                          AnalyticsService (never a stored copy - see note 1 above).
--   min_marks_percentage   NULL = "no percentage requirement". Compared against the
--                          student's overall marks percentage from the same source.
--   graduation_year        NULL = "any batch". A non-NULL value must equal the student's
--                          DERIVED graduation year (note 2 above) exactly. It is a single
--                          year, not a range, because a drive targets one batch; a
--                          company recruiting two batches gets two drives, which is also
--                          how their deadlines and openings actually differ in practice.
--   job_eligible_departments  EMPTY = "every department". See that table below.
--
-- All four are NULLABLE/optional on purpose: a drive with no criteria at all is legal and
-- means "open to every ACTIVE student", which is a real thing colleges post. NULL here
-- always means "this criterion is not applied" - never "unknown" and never "zero". A job
-- with min_cgpa = 0.00 is NOT the same as min_cgpa = NULL: the former still requires the
-- student to HAVE a computable CGPA (i.e. something graded), the latter does not. The
-- eligibility engine must preserve that distinction, and the checkpoint tests it.
--
-- Two conditions the schema cannot enforce and the service therefore must:
--   a) A drive may only be moved to 'OPEN' if its application_deadline is in the future.
--      MySQL forbids NOW()/CURRENT_TIMESTAMP inside a CHECK (non-deterministic), so a
--      "deadline must be in the future" constraint is impossible here - and would be
--      wrong anyway, since a CLOSED drive's deadline is legitimately in the past.
--   b) Every department listed in job_eligible_departments must exist (the FK covers
--      that) AND the set must not name the same department twice (the unique key covers
--      that) - but "at least one department" is deliberately NOT required, because the
--      empty set is the meaningful "all departments" value.
--
-- status is the AUTHORING/lifecycle state, and it is what makes a drive visible:
--   DRAFT     - being authored. Invisible to students entirely (JobService returns 404,
--               not 403, for a non-admin requesting one, matching the house convention
--               that an id must not be probeable).
--   OPEN      - published and accepting applications. This is the ONLY status in which
--               POST /api/applications may succeed.
--   CLOSED    - published, no longer accepting applications. Still visible to students
--               so a drive they applied to does not vanish from their history.
--   CANCELLED - the drive was called off. Invisible to students, like DRAFT.
--
-- posted_by references `users`, not `faculty`: drives are posted by an ADMIN (the
-- placement cell), and an admin has no `faculty` row. It is NOT NULL because every drive
-- has an accountable author, and there is no ON DELETE clause - a user row backing an
-- audit trail must not be removable while it is referenced.
CREATE TABLE jobs (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    company_id            BIGINT         NOT NULL,
    title                 VARCHAR(150)   NOT NULL,
    description           MEDIUMTEXT     NULL,
    location              VARCHAR(150)   NULL,
    job_type              VARCHAR(20)    NOT NULL,
    openings              INT            NULL,
    salary_min            DECIMAL(12, 2) NULL,
    salary_max            DECIMAL(12, 2) NULL,
    salary_currency       VARCHAR(3)     NOT NULL DEFAULT 'INR',
    min_cgpa              DECIMAL(4, 2)  NULL,
    min_marks_percentage  DECIMAL(5, 2)  NULL,
    graduation_year       INT            NULL,
    application_deadline  DATETIME       NOT NULL,
    drive_date            DATE           NULL,
    status                VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    posted_by             BIGINT         NOT NULL,
    created_at            DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- Guards the realistic authoring accident: the admin "Create drive" form submitted
    -- twice (double click, retried request) produces two identical drives, students then
    -- apply to whichever one they happened to load, and the applicant list for the drive
    -- is silently split in half. The same company posting the same title with a DIFFERENT
    -- deadline is a genuinely different drive and is unaffected. Its leading column also
    -- makes it the index for "every drive from this company", so no separate index on
    -- company_id is created (InnoDB would have added one for the FK anyway).
    UNIQUE KEY uk_jobs_company_title_deadline (company_id, title, application_deadline),
    CONSTRAINT fk_jobs_company
        FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_jobs_posted_by
        FOREIGN KEY (posted_by) REFERENCES users (id),
    CONSTRAINT chk_jobs_job_type
        CHECK (job_type IN ('FULL_TIME', 'PART_TIME', 'INTERNSHIP', 'CONTRACT')),
    CONSTRAINT chk_jobs_status
        CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED', 'CANCELLED')),
    CONSTRAINT chk_jobs_openings_positive
        CHECK (openings IS NULL OR openings > 0),
    CONSTRAINT chk_jobs_salary_non_negative
        CHECK ((salary_min IS NULL OR salary_min >= 0) AND (salary_max IS NULL OR salary_max >= 0)),
    -- A range whose top is below its bottom renders as a nonsense figure on the student
    -- job card and cannot be sorted on. Rejecting it here is cheaper than discovering it
    -- in the UI.
    CONSTRAINT chk_jobs_salary_range
        CHECK (salary_min IS NULL OR salary_max IS NULL OR salary_max >= salary_min),
    -- ISO-4217-shaped. The currency lives in the database rather than as a hardcoded
    -- currency symbol in a React component, so a non-INR drive is expressible without a
    -- frontend change (§69: nothing displayed may be a literal that pretends to be data).
    --
    -- The `COLLATE utf8mb4_bin` is load-bearing and was verified empirically against the
    -- real MySQL 8.4 server, not assumed. This table's collation is utf8mb4_unicode_ci,
    -- and REGEXP evaluates under the operand's collation - so WITHOUT the explicit binary
    -- collation, '^[A-Z]{3}$' happily matches 'inr' and the constraint enforces nothing
    -- about case. It does NOT change the column's own type or collation (Hibernate still
    -- sees varchar(3) and ddl-auto=validate is unaffected); it only forces this one
    -- comparison to be case-sensitive.
    --
    -- Every other CHECK ... IN (...) in this migration has the same theoretical
    -- case-insensitivity, and is deliberately left alone to match V1-V7 style: those
    -- columns are written exclusively by @Enumerated(EnumType.STRING), so the value is
    -- always the exact uppercase enum constant. salary_currency is the one column here
    -- fed by free text off a request body, which is why it is the one that is tightened.
    CONSTRAINT chk_jobs_salary_currency
        CHECK (salary_currency COLLATE utf8mb4_bin REGEXP '^[A-Z]{3}$'),
    -- The same 0-10 scale as grade_bands.grade_point and performance_bands.min_gpa, so a
    -- drive can never demand a CGPA that no grade scale in this system can produce.
    CONSTRAINT chk_jobs_min_cgpa_range
        CHECK (min_cgpa IS NULL OR (min_cgpa >= 0 AND min_cgpa <= 10)),
    CONSTRAINT chk_jobs_min_marks_percentage_range
        CHECK (min_marks_percentage IS NULL OR (min_marks_percentage >= 0 AND min_marks_percentage <= 100)),
    CONSTRAINT chk_jobs_graduation_year_range
        CHECK (graduation_year IS NULL OR (graduation_year >= 1950 AND graduation_year <= 2100)),
    -- The on-campus drive cannot happen before applications have closed. DATE() is
    -- deterministic and therefore legal inside a MySQL CHECK; NOW() would not be.
    CONSTRAINT chk_jobs_drive_after_deadline
        CHECK (drive_date IS NULL OR drive_date >= DATE(application_deadline))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- The student-facing drive list, which is the hottest read in this module: "OPEN drives,
-- soonest deadline first" -- WHERE status = 'OPEN' [AND application_deadline >= NOW()]
-- ORDER BY application_deadline. Also serves the admin list filtered by status.
CREATE INDEX idx_jobs_status_deadline ON jobs (status, application_deadline);

-- The deadline sweep that closes expired drives and the "closing soon" dashboard tile,
-- both of which range over application_deadline WITHOUT pinning status:
-- WHERE application_deadline BETWEEN ? AND ?.
CREATE INDEX idx_jobs_deadline ON jobs (application_deadline);

-- ---------------------------------------------------------------------------------------
-- job_eligible_departments  (the department leg of the §34 criteria)
-- ---------------------------------------------------------------------------------------
-- Modelled as an explicit child table with its own surrogate key and its own JPA entity
-- + repository, NOT as a JPA @ManyToMany join table. That is deliberate and matches the
-- rule the rest of this codebase already follows: `spring.jpa.open-in-view` is false, so
-- a lazily-loaded collection touched during response serialization throws, and every
-- other parent/child pair here (CodingContest/ContestProblem, CodingContest/
-- ContestParticipant) is therefore read through its own repository rather than through a
-- mapped collection. See the CodingContest javadoc for the precedent.
--
-- SEMANTICS, stated here so schema and code cannot drift: ZERO rows for a job means the
-- drive is open to EVERY department. One or more rows means the drive is restricted to
-- exactly those departments. There is no "all departments" sentinel row and no NULL
-- department_id - the absence of rows is the value.
--
-- ON DELETE CASCADE on job_id: these rows are pure criteria detail with no independent
-- meaning, so a deleted drive must take them with it. JobService still deletes them
-- explicitly (deleteByJobId) before deleting the job, because Hibernate does not know
-- about the database-level cascade and would otherwise hold stale children in the
-- persistence context. The cascade is the backstop, not the mechanism. Note also that a
-- drive with applications is refused deletion outright by the FK from
-- placement_applications, which has NO cascade.
CREATE TABLE job_eligible_departments (
    id             BIGINT   NOT NULL AUTO_INCREMENT,
    job_id         BIGINT   NOT NULL,
    department_id  BIGINT   NOT NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- Listing a department twice would make the criteria set report "2 departments" for
    -- one real department and would double-count it in any join. Its leading column also
    -- serves the only read this table does per drive -- "the departments for job X".
    UNIQUE KEY uk_job_eligible_departments_job_department (job_id, department_id),
    CONSTRAINT fk_job_eligible_departments_job
        FOREIGN KEY (job_id) REFERENCES jobs (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_job_eligible_departments_department
        FOREIGN KEY (department_id) REFERENCES departments (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- The reverse lookup "which drives is a student in department D eligible for?", which the
-- student drive list runs as an anti-join (a drive qualifies when it has NO rows here, or
-- has a row for D). InnoDB already indexes department_id on its own to police the FK
-- above, so no separate CREATE INDEX is added for it.

-- ---------------------------------------------------------------------------------------
-- placement_applications  (§35, §36: a student's application to one drive, and its
-- admin-driven status lifecycle)
-- ---------------------------------------------------------------------------------------
-- THE DUPLICATE GUARD (§35) IS uk_placement_applications_job_student BELOW. It is the
-- reason this table exists in this shape. A Java-only `existsByJobIdAndStudentId` check
-- is not a guard: two concurrent submits both read "no row", both insert, and the student
-- has applied twice. PlacementApplicationService still performs that check first, so the
-- normal path returns a clean 409 with a readable message rather than a raw constraint
-- violation - but the service MUST also catch DataIntegrityViolationException from the
-- insert and translate it to the same 409, because the check and the insert are not
-- atomic with respect to each other and the database is the only thing that is.
--
-- WITHDRAWAL DOES NOT DELETE THE ROW, and therefore does not free the slot: a student who
-- withdraws cannot re-apply to the same drive. That is a deliberate decision, not an
-- oversight. Deleting on withdrawal would destroy the record that the student was ever
-- in the pipeline (which the company and the placement cell both need), and would turn
-- the unique key into a guard that a student can defeat at will by withdrawing and
-- re-applying. WITHDRAWN is terminal; re-entry is an admin action.
--
-- cgpa_at_application / percentage_at_application are a HISTORICAL RECORD written once at
-- insert from the same AnalyticsService figures the eligibility engine just evaluated -
-- never recomputed, never refreshed. Two reasons they are worth a column: the admin
-- applicant list can be sorted and displayed without re-running a heavy per-student
-- analytics aggregation for every row, and six months later "why was this student
-- accepted into a 7.5 CGPA drive" has a truthful answer even though their CGPA has since
-- moved. They are NULLABLE because a drive with no CGPA/percentage criterion legitimately
-- accepts a student who has nothing graded yet; NULL means "not computable at the time of
-- application", never zero. The cross-table invariant "if the job set min_cgpa then this
-- must be non-NULL and >= it" is not expressible as a CHECK and is the eligibility
-- engine's responsibility.
--
-- status is VARCHAR(30), not the VARCHAR(20) used by every other status column in this
-- schema, purely because 'INTERVIEW_SCHEDULED' is 19 characters and leaves no headroom.
-- The JPA entity MUST declare `length = 30` on this column or ddl-auto=validate fails
-- the boot (G8).
--
-- The status set is CLOSED by the CHECK below and by the ApplicationStatus enum:
--   APPLIED             - the student applied. The only status a row may be inserted with.
--   UNDER_REVIEW        - the placement cell is screening.
--   SHORTLISTED         - §36 shortlisting.
--   INTERVIEW_SCHEDULED - included NOW, in Phase 8, so that Phase 10's interview module
--                         needs no ALTER to this CHECK. Phase 8 implements the transition
--                         into it; Phase 10 links it to a real Interview row.
--   SELECTED / REJECTED / WITHDRAWN - terminal. WITHDRAWN is student-initiated and is the
--                         ONLY transition an admin may not perform; the other six are
--                         admin-only.
CREATE TABLE placement_applications (
    id                         BIGINT         NOT NULL AUTO_INCREMENT,
    job_id                     BIGINT         NOT NULL,
    student_id                 BIGINT         NOT NULL,
    status                     VARCHAR(30)    NOT NULL DEFAULT 'APPLIED',
    cover_note                 VARCHAR(2000)  NULL,
    cgpa_at_application        DECIMAL(4, 2)  NULL,
    percentage_at_application  DECIMAL(5, 2)  NULL,
    applied_at                 DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status_changed_at          DATETIME       NULL,
    status_changed_by          BIGINT         NULL,
    decision_note              VARCHAR(500)   NULL,
    created_at                 DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- §35 DUPLICATE-APPLICATION GUARD. See the block comment above: this key, not the
    -- service-layer check, is what makes "cannot apply twice" true. Its leading column
    -- also makes it the index for the admin applicant view ("every applicant to job X").
    UNIQUE KEY uk_placement_applications_job_student (job_id, student_id),
    CONSTRAINT fk_placement_applications_job
        FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT fk_placement_applications_student
        FOREIGN KEY (student_id) REFERENCES students (id),
    -- NULLABLE and NOT cascading: it names the admin (or, for WITHDRAWN, the student's own
    -- user) who last moved the row, and NULL is legal only while the row is still APPLIED.
    CONSTRAINT fk_placement_applications_status_changed_by
        FOREIGN KEY (status_changed_by) REFERENCES users (id),
    CONSTRAINT chk_placement_applications_status
        CHECK (status IN (
            'APPLIED',
            'UNDER_REVIEW',
            'SHORTLISTED',
            'INTERVIEW_SCHEDULED',
            'SELECTED',
            'REJECTED',
            'WITHDRAWN')),
    CONSTRAINT chk_placement_applications_cgpa_range
        CHECK (cgpa_at_application IS NULL
               OR (cgpa_at_application >= 0 AND cgpa_at_application <= 10)),
    CONSTRAINT chk_placement_applications_percentage_range
        CHECK (percentage_at_application IS NULL
               OR (percentage_at_application >= 0 AND percentage_at_application <= 100)),
    -- Every status change is attributed. A row that has moved off APPLIED without a
    -- timestamp and an actor is an unauditable decision, and §36's status transitions are
    -- exactly the thing a placement cell gets asked to justify. This makes a half-written
    -- transition impossible at the database rather than merely unlikely in Java: the
    -- service MUST set both fields in the same UPDATE that changes status.
    CONSTRAINT chk_placement_applications_status_change_attributed
        CHECK (status = 'APPLIED'
               OR (status_changed_at IS NOT NULL AND status_changed_by IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- "My applications, newest first" -- the student's placement history screen:
-- WHERE student_id = ? [AND status = ?] ORDER BY applied_at DESC. The unique key leads
-- with job_id and so cannot serve a student-led query at all.
CREATE INDEX idx_placement_applications_student_recent
    ON placement_applications (student_id, applied_at);

-- The admin applicant view filtered by pipeline stage, and the per-drive funnel counts on
-- the placement analytics screen: WHERE job_id = ? AND status = ?, and
-- WHERE job_id IN (...) GROUP BY job_id, status. uk_placement_applications_job_student
-- leads with job_id but its second column is student_id, so it cannot serve the status leg.
CREATE INDEX idx_placement_applications_job_status
    ON placement_applications (job_id, status);

-- The system-wide placement funnel (§36 analytics): SELECT status, COUNT(*) ...
-- GROUP BY status, with no job pinned. Covered by this index alone, so the aggregation
-- never touches the table itself.
CREATE INDEX idx_placement_applications_status
    ON placement_applications (status);
