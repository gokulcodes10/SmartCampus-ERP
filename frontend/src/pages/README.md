# pages/

Route-level screens — one file (or folder) per route rendered by
`routes/`, e.g. `LoginPage.tsx`, `StudentDashboardPage.tsx`,
`AdminStudentsPage.tsx`. Per the phase plan, real pages land in Phase 2+
(auth) through Phase 11 (real-time), grouped by module/role as the scope
requires (student, faculty, admin dashboards; academics; coding; placement;
resume; interview).

Pages own data-fetching (via `services/` and `hooks/`) and layout choice
(via `layouts/`); they compose `components/` rather than reimplementing UI.
