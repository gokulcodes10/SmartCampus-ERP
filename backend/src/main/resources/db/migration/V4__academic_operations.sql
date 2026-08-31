-- V4__academic_operations.sql  --  SmartCampus ERP academic-operations schema (Phase 4)
--
-- Creates the four tables the academic-operations module owns:
--   attendance    - one row per (student, subject, date, period) teaching session, per
--                    PROJECT_PLAN.md clarification G6. A CANCELLED row records that the
--                    class did not happen and is EXCLUDED from the attendance-percentage
--                    denominator.
--   exams         - a SCHEDULED exam (G4): subject, type, date, maximum marks, owning
--                    faculty. Assignments are represented by exam_type = 'ASSIGNMENT'
--                    only -- there is no separate assignment-submission module (G5).
--                    This is also what makes "upcoming exams" on the dashboards real
--                    data rather than a placeholder.
--   marks         - a student's score in one exam. References the exam by FK, so the
--                    maximum it is validated against is never duplicated per row.
--   grade_bands   - the admin-configurable percentage->grade->grade-point mapping (G7).
--                    Credit-weighted 10-point scale. NOTHING about grading is hard-coded
--                    in Java: both the band boundaries and the grade points are rows here.
--
-- Creation order follows the FK dependency chain: everything depends on the Phase 3
-- tables (students, subjects, faculty) created by V3__academic.sql, and marks depends on
-- exams. grade_bands is standalone reference data.
--
-- Inherits utf8mb4 / utf8mb4_unicode_ci from the database default set in V1__baseline.sql;
-- each table still declares ENGINE/CHARSET/COLLATE explicitly, matching V1, V2 and V3 style.
--
-- Authorization note: every faculty write against these tables is gated in the service
-- layer by smartcampus.service.AcademicAccessGuard against the exact
-- (subject, academic_year, semester, section) tuple -- clarification G2. That is why
-- attendance and exams both carry academic_year/semester/section explicitly rather than
-- deriving them through a join: the tuple the guard checks is the tuple the row is
-- written with, so there is no gap between "what was authorized" and "what was stored".

-- ---------------------------------------------------------------------------------------
-- attendance  (G6: keyed on student + subject + date + period)
-- ---------------------------------------------------------------------------------------
-- The unique key below is clarification G6 expressed in the schema: a student can have at
-- most ONE attendance row for a given subject, on a given date, in a given period. That is
-- what makes bulk roster marking safely repeatable -- re-submitting a roster updates the
-- existing rows instead of silently doubling the denominator.
--
-- academic_year/semester/section are stored on the row (not joined from `enrollments`) for
-- three reasons: the AcademicAccessGuard tuple must be recorded as written (see the note
-- above); a student who repeats a subject in a later academic year must keep two separate,
-- independently-computed attendance percentages; and per-semester/per-year aggregation is
-- the single hottest query in this module. The service layer is responsible for verifying
-- that a matching `enrollments` row actually exists before inserting -- a cross-table
-- condition MySQL cannot express as a CHECK.
--
-- marked_by_faculty_id is NULLABLE on purpose: an ADMIN correcting a record is a real and
-- expected operation, and an admin has no `faculty` row to point at. NULL means "not marked
-- by a faculty member", never "unknown".
CREATE TABLE attendance (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    student_id            BIGINT       NOT NULL,
    subject_id            BIGINT       NOT NULL,
    academic_year         VARCHAR(9)   NOT NULL,
    semester              INT          NOT NULL,
    section               VARCHAR(10)  NOT NULL,
    attendance_date       DATE         NOT NULL,
    period                INT          NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    remarks               VARCHAR(255) NULL,
    marked_by_faculty_id  BIGINT       NULL,
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_attendance_student_subject_date_period (student_id, subject_id, attendance_date, period),
    CONSTRAINT fk_attendance_student
        FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_attendance_subject
        FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_attendance_marked_by_faculty
        FOREIGN KEY (marked_by_faculty_id) REFERENCES faculty (id),
    -- CANCELLED is a first-class status, not a deleted row: "the class was scheduled and
    -- did not happen" is information the percentage calculation needs in order to exclude
    -- it from the denominator (G6). Deleting the rows instead would lose that fact.
    CONSTRAINT chk_attendance_status
        CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'ON_DUTY', 'CANCELLED')),
    CONSTRAINT chk_attendance_period_range CHECK (period >= 1 AND period <= 12),
    CONSTRAINT chk_attendance_semester_positive CHECK (semester > 0),
    -- The "2025-2026" shape used by enrollments.academic_year and
    -- faculty_subject_assignments.academic_year. Enforced here rather than only by the
    -- @Pattern on the request DTO, because an academic_year that does not match the
    -- assignment row's spelling silently produces an attendance percentage over the wrong
    -- denominator instead of an error.
    CONSTRAINT chk_attendance_academic_year_format
        CHECK (academic_year REGEXP '^[0-9]{4}-[0-9]{4}$')
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Backs the student-facing query: "every attendance record of mine in this academic year
-- and semester, grouped by subject" -- the attendance percentage / low-attendance warning
-- calculation, and the single most frequently executed query in this module.
CREATE INDEX idx_attendance_student_scope
    ON attendance (student_id, academic_year, semester, subject_id);

-- Backs the faculty-facing query: "load the roster session for subject X, year Y,
-- semester S, section Z, on date D, period P" (bulk marking pre-fill and re-marking), and
-- its prefix "everything I have marked for this class" (the class attendance summary).
CREATE INDEX idx_attendance_class_session
    ON attendance (subject_id, academic_year, semester, section, attendance_date, period);

-- ---------------------------------------------------------------------------------------
-- exams  (G4: a scheduled exam. G5: assignments are exam_type = 'ASSIGNMENT'.)
-- ---------------------------------------------------------------------------------------
-- The unique key includes `title` deliberately. A subject/section has exactly one
-- INTERNAL_1 in a given year+semester, but it has many ASSIGNMENTs and many QUIZzes -- a
-- key of (subject, year, semester, section, exam_type) alone would make G5 unusable by
-- allowing only a single assignment per semester.
--
-- faculty_id is the owning faculty (G4) and is NULLABLE for the same reason
-- attendance.marked_by_faculty_id is: an ADMIN may schedule an exam directly.
CREATE TABLE exams (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    subject_id     BIGINT        NOT NULL,
    faculty_id     BIGINT        NULL,
    title          VARCHAR(150)  NOT NULL,
    exam_type      VARCHAR(20)   NOT NULL,
    academic_year  VARCHAR(9)    NOT NULL,
    semester       INT           NOT NULL,
    section        VARCHAR(10)   NOT NULL,
    exam_date      DATE          NOT NULL,
    maximum_marks  DECIMAL(6, 2) NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'SCHEDULED',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_exams_scope_type_title (subject_id, academic_year, semester, section, exam_type, title),
    CONSTRAINT fk_exams_subject
        FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_exams_faculty
        FOREIGN KEY (faculty_id) REFERENCES faculty (id),
    CONSTRAINT chk_exams_type
        CHECK (exam_type IN ('INTERNAL_1', 'INTERNAL_2', 'INTERNAL_3', 'ASSIGNMENT',
                             'QUIZ', 'MODEL', 'SEMESTER')),
    CONSTRAINT chk_exams_status
        CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED')),
    -- maximum_marks > 0 is what makes the marks percentage (obtained / maximum) safe: a
    -- zero maximum would be a division by zero in every grade calculation downstream.
    CONSTRAINT chk_exams_maximum_marks_range
        CHECK (maximum_marks > 0 AND maximum_marks <= 1000),
    CONSTRAINT chk_exams_semester_positive CHECK (semester > 0),
    CONSTRAINT chk_exams_academic_year_format
        CHECK (academic_year REGEXP '^[0-9]{4}-[0-9]{4}$')
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Backs "upcoming exams for the subjects I am enrolled in / teach":
-- WHERE subject_id IN (...) AND exam_date >= CURRENT_DATE ORDER BY exam_date.
CREATE INDEX idx_exams_subject_date ON exams (subject_id, exam_date);

-- Backs the calendar-style listing that filters on status first
-- (WHERE status = 'SCHEDULED' AND exam_date BETWEEN ? AND ?).
CREATE INDEX idx_exams_status_date ON exams (status, exam_date);

-- ---------------------------------------------------------------------------------------
-- marks
-- ---------------------------------------------------------------------------------------
-- One score per student per exam. maximum_marks is NOT copied here: it lives on the exam
-- row and is read through the FK, so an admin correcting an exam's maximum can never leave
-- marks rows validated against a stale maximum.
--
-- The consequence is that `marks_obtained <= exams.maximum_marks` is a CROSS-TABLE
-- condition and MySQL cannot express it as a CHECK constraint. Only the lower bound is
-- enforced here. The upper bound MUST be enforced in MarksService against the exam that
-- the mark's exam_id points at -- it is not covered by the schema, and it is the half of
-- the Phase 4 marks validation that is easiest to assume is already handled.
--
-- The exam FK is deliberately left at the default RESTRICT: deleting an exam that already
-- has marks must fail loudly (GlobalExceptionHandler already turns that into a clean 409)
-- rather than silently destroying student results.
CREATE TABLE marks (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    exam_id                BIGINT        NOT NULL,
    student_id             BIGINT        NOT NULL,
    marks_obtained         DECIMAL(6, 2) NOT NULL,
    remarks                VARCHAR(255)  NULL,
    entered_by_faculty_id  BIGINT        NULL,
    created_at             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- Makes bulk marks entry repeatable the same way the attendance key does: a second
    -- submission of the same sheet updates rather than duplicating.
    UNIQUE KEY uk_marks_exam_student (exam_id, student_id),
    CONSTRAINT fk_marks_exam
        FOREIGN KEY (exam_id) REFERENCES exams (id),
    CONSTRAINT fk_marks_student
        FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_marks_entered_by_faculty
        FOREIGN KEY (entered_by_faculty_id) REFERENCES faculty (id),
    CONSTRAINT chk_marks_obtained_non_negative CHECK (marks_obtained >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- "every mark of mine, joined to its exam" -- the student marks view and the per-subject
-- grade calculation. InnoDB already creates an index on student_id to police
-- fk_marks_student, but that single-column index does not carry exam_id, so the join still
-- needs a lookup per row; this composite covers it.
CREATE INDEX idx_marks_student_exam ON marks (student_id, exam_id);

-- ---------------------------------------------------------------------------------------
-- grade_bands  (G7: admin-configurable percentage -> grade -> grade point, 10-point scale)
-- ---------------------------------------------------------------------------------------
-- One row per grade. `grade_point` living in this same table is the whole point of G7:
-- "the grade->point mapping lives in the same admin-configurable table as the grade bands",
-- so a college on a different scale changes rows, not code. No Java class may contain a
-- literal grade letter, boundary, or grade point.
--
-- What the schema CAN enforce is here: each band is a sane, in-range interval, grades are
-- unique, and no two bands may start at the same percentage. What it CANNOT enforce is
-- that the set of bands is gap-free and non-overlapping across 0-100 -- that is a
-- whole-table condition, and GradeBandService MUST validate it on every create/update
-- (reject a band overlapping any other band; warn on a gap) or a percentage can fall
-- between two bands and produce a null grade for a student who has real marks.
CREATE TABLE grade_bands (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    grade           VARCHAR(5)    NOT NULL,
    min_percentage  DECIMAL(5, 2) NOT NULL,
    max_percentage  DECIMAL(5, 2) NOT NULL,
    grade_point     DECIMAL(4, 2) NOT NULL,
    pass_grade      TINYINT(1)    NOT NULL DEFAULT 1,
    description     VARCHAR(100)  NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_grade_bands_grade (grade),
    UNIQUE KEY uk_grade_bands_min_percentage (min_percentage),
    CONSTRAINT chk_grade_bands_min_range CHECK (min_percentage >= 0 AND min_percentage <= 100),
    CONSTRAINT chk_grade_bands_max_range CHECK (max_percentage >= 0 AND max_percentage <= 100),
    CONSTRAINT chk_grade_bands_min_le_max CHECK (min_percentage <= max_percentage),
    CONSTRAINT chk_grade_bands_point_range CHECK (grade_point >= 0 AND grade_point <= 10)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- The band lookup is WHERE ? BETWEEN min_percentage AND max_percentage. The unique key on
-- min_percentage already covers the leftmost column; this composite lets the range check
-- be answered from the index alone.
CREATE INDEX idx_grade_bands_range ON grade_bands (min_percentage, max_percentage);

-- Default 10-point scale. This is CONFIGURATION, not seed data (Phase 12 owns seed data):
-- the grading module cannot produce a grade at all with an empty table, so shipping zero
-- rows would mean every student with real marks gets a null grade on a fresh database --
-- indistinguishable from broken. Every value below is editable through
-- /api/grade-bands by an ADMIN, which is exactly what G7 requires; nothing here is
-- hard-coded in Java.
--
-- The boundaries are contiguous at 2 decimal places, and every percentage the application
-- computes is rounded to 2 decimal places (HALF_UP) before lookup, so the set covers
-- 0.00-100.00 with no gap and no overlap.
INSERT INTO grade_bands (grade, min_percentage, max_percentage, grade_point, pass_grade, description) VALUES
    ('O',   91.00, 100.00, 10.00, 1, 'Outstanding'),
    ('A+',  81.00,  90.99,  9.00, 1, 'Excellent'),
    ('A',   71.00,  80.99,  8.00, 1, 'Very good'),
    ('B+',  61.00,  70.99,  7.00, 1, 'Good'),
    ('B',   56.00,  60.99,  6.00, 1, 'Above average'),
    ('C',   50.00,  55.99,  5.00, 1, 'Average'),
    ('U',    0.00,  49.99,  0.00, 0, 'Reappear');
