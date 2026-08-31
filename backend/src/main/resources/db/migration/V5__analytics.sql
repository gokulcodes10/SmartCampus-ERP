-- V5__analytics.sql  --  SmartCampus ERP analytics schema (Phase 5, Analytics)
--
-- Phase 5 computes every dashboard figure LIVE from the Phase 3/Phase 4 tables
-- (`attendance`, `marks`, `exams`, `enrollments`, `subjects`, `students`). There is no
-- snapshot, rollup or cache table here on purpose: the Phase 5 checkpoint is "every
-- figure on every dashboard traces to a database aggregation", and a materialised copy
-- would immediately become a second, staler source of truth for the same number.
--
-- So this migration adds exactly two things:
--
--   1. performance_bands  - the CONFIGURABLE thresholds that turn a student's real
--                            aggregates into an EXCELLENT / GOOD / AVERAGE / AT_RISK
--                            classification (§22-§24, §60). Nothing about that
--                            classification may be a literal in Java.
--   2. four indexes        - on `attendance` and `exams`, backing the aggregation shapes
--                            Phase 5 introduces that Phase 4's indexes do not already
--                            cover (time-series trend, and scope filters that leave the
--                            subject unpinned).
--
-- WHY NOT REUSE grade_bands (V4)?  It was considered and it does not fit. `grade_bands`
-- maps ONE percentage to a letter grade and a grade point (G7) - a single-axis lookup
-- over one input. The Phase 5 classification is a different question over DIFFERENT
-- inputs: it combines a student's marks percentage, attendance percentage and (optionally)
-- credit-weighted GPA into one of four fixed performance categories. Overloading
-- `grade_bands` with attendance and GPA columns would corrupt the G7 grade scale that
-- MarksService, GradeCalculationService and /api/grade-bands all read. Hence a separate
-- table. The two are related only in that both are admin-editable rows, never Java
-- literals.
--
-- Inherits utf8mb4 / utf8mb4_unicode_ci from the database default set in V1__baseline.sql;
-- the table still declares ENGINE/CHARSET/COLLATE explicitly, matching V1-V4 style.

-- ---------------------------------------------------------------------------------------
-- performance_bands  (§22-§24: configurable EXCELLENT / GOOD / AVERAGE / AT_RISK bands)
-- ---------------------------------------------------------------------------------------
-- Exactly four rows, one per category. The category set is CLOSED - it is fixed by the
-- CHECK constraint below and by the PerformanceCategory enum, and the API exposes only
-- GET (list) and PUT (update thresholds). There is no create and no delete, because a
-- fifth category would have no meaning to the classifier and a deleted category would
-- make some students unclassifiable. What an admin configures is the THRESHOLDS, which
-- is what §60 asks for.
--
-- Classification rule (implemented in AnalyticsService, stated here so the schema and the
-- code cannot drift): bands are read in display_order ASC - 1 = EXCELLENT, the strictest,
-- through 4 = AT_RISK, the catch-all. A band matches when the student's marks percentage
-- >= min_marks_percentage AND their attendance percentage >= min_attendance_percentage
-- AND (min_gpa IS NULL OR their GPA >= min_gpa). The FIRST band that matches wins.
--
-- The two things the schema deliberately does NOT try to enforce, because both are
-- whole-table conditions MySQL cannot express as a single-row CHECK, and both are
-- therefore PerformanceBandService's job on every update:
--
--   a) MONOTONICITY. A stricter band (lower display_order) must have thresholds that are
--      >= every looser band's. Without it a band becomes unreachable - e.g. GOOD
--      requiring 90% while EXCELLENT requires 85% means GOOD can never be reached,
--      because EXCELLENT is tested first and always wins.
--   b) THE CATCH-ALL. The band with the highest display_order must keep
--      min_marks_percentage = 0, min_attendance_percentage = 0 and min_gpa NULL, or a
--      real student with real aggregates matches no band at all and comes back
--      unclassified.
--
-- min_gpa is NULLABLE and means "this band imposes no GPA requirement" - never "unknown"
-- and never "0". It is seeded NULL for all four bands so the shipped default classifies
-- on marks + attendance only; an admin who wants a CGPA floor sets it. A band with
-- min_gpa set is unreachable for a student who has attendance but nothing graded yet,
-- which is correct: that student falls through to a looser band rather than being
-- awarded a category on evidence that does not exist.
--
-- color_hex is VARCHAR(7), not CHAR(7), deliberately. Hibernate maps a String property to
-- JDBC VARCHAR, and `ddl-auto=validate` fails the boot with "wrong column type ... found
-- char, expected varchar" against a CHAR column (G8). It lives in the database rather than
-- in the React chart config so that the colour of a category on a Chart.js dataset is one
-- more thing an admin configures, not a literal in a component - the same rule as the
-- thresholds themselves.
CREATE TABLE performance_bands (
    id                         BIGINT        NOT NULL AUTO_INCREMENT,
    category                   VARCHAR(20)   NOT NULL,
    display_order              INT           NOT NULL,
    min_marks_percentage       DECIMAL(5, 2) NOT NULL,
    min_attendance_percentage  DECIMAL(5, 2) NOT NULL,
    min_gpa                    DECIMAL(4, 2) NULL,
    color_hex                  VARCHAR(7)    NOT NULL,
    description                VARCHAR(150)  NULL,
    created_at                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- One row per category, and one band per position in the ordering. Together with the
    -- CHECK on `category` below, these two keys cap the table at exactly four rows and
    -- make "two bands tested at the same priority" impossible rather than merely
    -- unlikely. uk_performance_bands_display_order also serves the only read this table
    -- ever does - "every band, in display_order" - so no separate index is created.
    UNIQUE KEY uk_performance_bands_category (category),
    UNIQUE KEY uk_performance_bands_display_order (display_order),
    CONSTRAINT chk_performance_bands_category
        CHECK (category IN ('EXCELLENT', 'GOOD', 'AVERAGE', 'AT_RISK')),
    CONSTRAINT chk_performance_bands_display_order_range
        CHECK (display_order >= 1 AND display_order <= 4),
    CONSTRAINT chk_performance_bands_min_marks_range
        CHECK (min_marks_percentage >= 0 AND min_marks_percentage <= 100),
    CONSTRAINT chk_performance_bands_min_attendance_range
        CHECK (min_attendance_percentage >= 0 AND min_attendance_percentage <= 100),
    -- NULL is a legal, meaningful value here ("no GPA requirement"); a NON-NULL value is
    -- constrained to the same 0-10 scale as grade_bands.grade_point, so a band can never
    -- demand a GPA no grade scale can produce.
    CONSTRAINT chk_performance_bands_min_gpa_range
        CHECK (min_gpa IS NULL OR (min_gpa >= 0 AND min_gpa <= 10)),
    -- A malformed colour reaches the browser as an invisible or default-coloured chart
    -- dataset, which looks like a rendering bug rather than bad configuration. Rejecting
    -- it at the database is cheaper than debugging it in Chart.js.
    CONSTRAINT chk_performance_bands_color_hex
        CHECK (color_hex REGEXP '^#[0-9A-Fa-f]{6}$')
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Default thresholds. This is CONFIGURATION, not seed data (Phase 12 owns seed data):
-- with an empty table the classifier cannot produce a category at all, so every student
-- on a fresh database would come back unclassified - indistinguishable from broken. Every
-- number below is editable through PUT /api/performance-bands/{id} by an ADMIN, which is
-- exactly what §60 requires; no Java class and no React component may contain any of them.
--
-- The AT_RISK row is the catch-all described above: 0 / 0 / NULL, so any student with
-- both a real marks percentage and a real attendance percentage always lands somewhere.
-- A student missing either figure is reported with a null classification and an explicit
-- reason - never defaulted into AT_RISK, which would be a fabricated verdict (§69).
INSERT INTO performance_bands
    (category, display_order, min_marks_percentage, min_attendance_percentage, min_gpa, color_hex, description) VALUES
    ('EXCELLENT', 1, 85.00, 90.00, NULL, '#16A34A', 'Consistently high marks and attendance'),
    ('GOOD',      2, 70.00, 80.00, NULL, '#2563EB', 'Solid performance, above the class norm'),
    ('AVERAGE',   3, 50.00, 75.00, NULL, '#CA8A04', 'Meeting the minimum expectations'),
    ('AT_RISK',   4,  0.00,  0.00, NULL, '#DC2626', 'Below the minimum on marks, attendance or both');

-- ---------------------------------------------------------------------------------------
-- Indexes for the Phase 5 aggregations
-- ---------------------------------------------------------------------------------------
-- Phase 4 already indexed the two attendance shapes IT needed:
--   idx_attendance_student_scope   (student_id, academic_year, semester, subject_id)
--   idx_attendance_class_session   (subject_id, academic_year, semester, section,
--                                   attendance_date, period)
-- and the two exam shapes:
--   uk_exams_scope_type_title      (subject_id, academic_year, semester, section, ...)
--   idx_exams_subject_date         (subject_id, exam_date)
--   idx_exams_status_date          (status, exam_date)
-- Phase 5 adds three query shapes those do not serve. Nothing else is added: an index
-- that no real query uses is pure write cost.

-- Shape 1 - the student PERFORMANCE TREND. "Group my attendance by calendar month,
-- across every academic year and semester I have records for, ordered by date":
--   WHERE student_id = ? [AND attendance_date >= ?] GROUP BY YEAR/MONTH(attendance_date)
-- idx_attendance_student_scope leads with student_id but its next column is
-- academic_year, so it cannot serve the date range or the ordering when the trend is not
-- pinned to one year+semester (the default view on the student dashboard). This index
-- puts attendance_date directly behind student_id.
CREATE INDEX idx_attendance_student_date ON attendance (student_id, attendance_date);

-- Shape 2 - the faculty/admin filter set with the SUBJECT LEFT BLANK. The Phase 5 faculty
-- filters are course, subject, semester, section and academic year, and subject is
-- optional: "attendance for 2025-2026, semester 3, section A, across every subject of
-- this course" is a first-class query. idx_attendance_class_session leads with
-- subject_id, so it is unusable the moment subject is not supplied; this index leads with
-- the scope columns that are always supplied and carries the date for the trend variant
-- of the same query.
CREATE INDEX idx_attendance_scope_date
    ON attendance (academic_year, semester, section, attendance_date);

-- Shape 3 - the same unpinned-subject filter over exams, which is how the class/cohort
-- marks aggregations reach the `marks` rows ("every exam in this year/semester/section,
-- newest first, then its marks via uk_marks_exam_student"). Both existing exam indexes
-- lead with subject_id or status, neither of which is the leading filter here.
CREATE INDEX idx_exams_scope_date
    ON exams (academic_year, semester, section, exam_date);
