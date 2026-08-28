-- V3__academic.sql  --  SmartCampus ERP core academic schema (Phase 3, Core Academic)
--
-- Creates the seven tables the academic-core module owns:
--   departments                    - top-level academic units (e.g. "Computer Science").
--   courses                        - a program offered by a department (e.g. "B.Tech CSE"),
--                                     with the number of semesters the program runs.
--   subjects                       - a syllabus subject taught within one course/semester,
--                                     carrying the credit weight Phase 5 uses for GPA.
--   students                       - one row per STUDENT user, in PENDING or ACTIVE state.
--   faculty                        - one row per FACULTY user, admin-provisioned.
--   enrollments                    - a student's registration in a subject for a given
--                                     academic year/semester/section (the roster).
--   faculty_subject_assignments    - which faculty teaches which subject/section, in which
--                                     academic year/semester. Every faculty authorization
--                                     check in the application routes through this table
--                                     (PROJECT_PLAN.md clarification G2).
--
-- Creation order follows the FK dependency chain: departments -> courses -> subjects ->
-- students/faculty -> enrollments/faculty_subject_assignments.
--
-- Inherits utf8mb4 / utf8mb4_unicode_ci from the database default set in V1__baseline.sql;
-- each table still declares ENGINE/CHARSET/COLLATE explicitly, matching V1 and V2 style.

-- ---------------------------------------------------------------------------------------
-- departments
-- ---------------------------------------------------------------------------------------
CREATE TABLE departments (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(10)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_departments_code (code),
    UNIQUE KEY uk_departments_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------------------
-- courses  (a program offered by a department, e.g. "B.Tech Computer Science")
-- ---------------------------------------------------------------------------------------
CREATE TABLE courses (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    code                VARCHAR(20)  NOT NULL,
    name                VARCHAR(150) NOT NULL,
    department_id       BIGINT       NOT NULL,
    duration_semesters  INT          NOT NULL DEFAULT 8,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_courses_code (code),
    CONSTRAINT fk_courses_department
        FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT chk_courses_duration_positive CHECK (duration_semesters > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Admin screens list/filter courses by department (Phase 3 server-side filtering).
-- InnoDB already indexes department_id automatically to police the FK above, so no
-- separate CREATE INDEX is needed for that single-column lookup.

-- ---------------------------------------------------------------------------------------
-- subjects  (a syllabus subject taught within one course, in one semester of that course)
-- ---------------------------------------------------------------------------------------
CREATE TABLE subjects (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    credits     INT          NOT NULL,
    semester    INT          NOT NULL,
    course_id   BIGINT       NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_subjects_code (code),
    CONSTRAINT fk_subjects_course
        FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT chk_subjects_credits_range CHECK (credits > 0 AND credits <= 10),
    CONSTRAINT chk_subjects_semester_positive CHECK (semester > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- "list subjects for course X, semester Y" is the standard course-structure and
-- enrollment-picker query; course_id alone is already indexed by the FK above, so this
-- composite index adds the semester leg on top of it.
CREATE INDEX idx_subjects_course_semester ON subjects (course_id, semester);

-- ---------------------------------------------------------------------------------------
-- students  (one row per STUDENT user; PENDING at registration, ACTIVE once an admin
-- assigns department, course and register number -- PROJECT_PLAN.md clarification G1)
-- ---------------------------------------------------------------------------------------
-- Self-registration (AuthService.register, Phase 2) creates the `users` row and MUST
-- also insert the matching pending student row here: department_id, course_id,
-- current_semester, section and register_number all NULL, status = 'PENDING'. An admin
-- activation endpoint (Phase 3) then sets all four and flips status to 'ACTIVE'. The
-- CHECK constraint below makes that transition impossible to do halfway.
CREATE TABLE students (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL,
    register_number   VARCHAR(20)  NULL,
    department_id     BIGINT       NULL,
    course_id         BIGINT       NULL,
    current_semester  INT          NULL,
    section           VARCHAR(10)  NULL,
    admission_year    INT          NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_students_user_id (user_id),
    UNIQUE KEY uk_students_register_number (register_number),
    CONSTRAINT fk_students_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_students_department
        FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_students_course
        FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT chk_students_status CHECK (status IN ('PENDING', 'ACTIVE', 'INACTIVE')),
    -- Admission gate for G1: a student cannot be ACTIVE with any of the four
    -- admin-assigned fields still missing. MySQL 8.4 enforces CHECK constraints.
    CONSTRAINT chk_students_active_requires_assignment CHECK (
        status <> 'ACTIVE'
        OR (
            register_number IS NOT NULL
            AND department_id IS NOT NULL
            AND course_id IS NOT NULL
            AND current_semester IS NOT NULL
        )
    )
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Backs the admin "pending activation" queue (WHERE status = 'PENDING') and any
-- status-filtered admin listing; not a FK column so InnoDB does not index it for free.
CREATE INDEX idx_students_status ON students (status);

-- Backs the Phase 3 admin server-side search/filter/pagination screen: list students
-- by department + course + semester (+ section), the exact filter combination those
-- screens expose.
CREATE INDEX idx_students_filter ON students (department_id, course_id, current_semester, section);

-- ---------------------------------------------------------------------------------------
-- faculty  (one row per FACULTY user; admin-provisioned, so department and employee_code
-- are known and required at creation time -- there is no PENDING state for faculty)
-- ---------------------------------------------------------------------------------------
CREATE TABLE faculty (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    employee_code  VARCHAR(20)  NOT NULL,
    department_id  BIGINT       NOT NULL,
    designation    VARCHAR(100) NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_faculty_user_id (user_id),
    UNIQUE KEY uk_faculty_employee_code (employee_code),
    CONSTRAINT fk_faculty_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_faculty_department
        FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT chk_faculty_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Backs admin listing of faculty by status (active/deactivated); department_id is
-- already indexed automatically by the FK above.
CREATE INDEX idx_faculty_status ON faculty (status);

-- ---------------------------------------------------------------------------------------
-- enrollments  (a student's registration in one subject, for one academic year/semester
-- section -- the roster that attendance and marks entry read from in Phase 4)
-- ---------------------------------------------------------------------------------------
CREATE TABLE enrollments (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    student_id     BIGINT       NOT NULL,
    subject_id     BIGINT       NOT NULL,
    academic_year  VARCHAR(9)   NOT NULL,
    semester       INT          NOT NULL,
    section        VARCHAR(10)  NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- A student cannot be enrolled in the same subject twice in the same academic
    -- year/semester. This also serves as the primary lookup index for "this student's
    -- enrollments" queries, since student_id is its leftmost column.
    UNIQUE KEY uk_enrollments_student_subject_year_sem (student_id, subject_id, academic_year, semester),
    CONSTRAINT fk_enrollments_student
        FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_enrollments_subject
        FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT chk_enrollments_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'DROPPED')),
    CONSTRAINT chk_enrollments_semester_positive CHECK (semester > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Backs the class-roster query attendance/marks entry screens run: "every student
-- enrolled in subject X, academic year Y, semester S, section Z".
CREATE INDEX idx_enrollments_roster ON enrollments (subject_id, academic_year, semester, section);

-- ---------------------------------------------------------------------------------------
-- faculty_subject_assignments  (which faculty teaches which subject/section, in which
-- academic year/semester -- PROJECT_PLAN.md clarification G2. Every faculty
-- authorization check in the application routes through this table.)
-- ---------------------------------------------------------------------------------------
CREATE TABLE faculty_subject_assignments (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    faculty_id     BIGINT      NOT NULL,
    subject_id     BIGINT      NOT NULL,
    academic_year  VARCHAR(9)  NOT NULL,
    semester       INT         NOT NULL,
    section        VARCHAR(10) NOT NULL,
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- The exact tuple G2 defines. Its column order (faculty_id first) makes it serve,
    -- as a byproduct, the authorization check every faculty-write endpoint must run:
    -- "is this faculty assigned to this subject (optionally + year/semester/section)?"
    -- via WHERE faculty_id = ? AND subject_id = ? [AND academic_year = ? AND
    -- semester = ? AND section = ?] -- a leftmost-prefix match against this key.
    UNIQUE KEY uk_fsa_faculty_subject_year_sem_section (faculty_id, subject_id, academic_year, semester, section),
    CONSTRAINT fk_fsa_faculty
        FOREIGN KEY (faculty_id) REFERENCES faculty (id),
    CONSTRAINT fk_fsa_subject
        FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT chk_fsa_semester_positive CHECK (semester > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Backs the reverse lookup "which faculty is assigned to teach subject X, section Z,
-- in year Y semester S" (roster/timetable-style screens, and validating that a section
-- actually has a faculty before attendance/marks entry is allowed).
CREATE INDEX idx_fsa_subject_lookup ON faculty_subject_assignments (subject_id, academic_year, semester, section);
