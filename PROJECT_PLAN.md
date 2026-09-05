# SmartCampus ERP — Build Plan

**Status:** Phase 1 (Foundation) complete except Judge0, which is blocked on this machine — see clarification G10. Phase 2 ready to start.
**Source of truth:** [`docs/SmartCampus-ERP-Scope.pdf`](docs/SmartCampus-ERP-Scope.pdf) — 76 sections. Where this plan and the scope disagree, the scope wins unless the disagreement is recorded in [Spec Clarifications](#2-spec-clarifications) below.

This document is the working plan. Phase numbering matches §74 of the scope exactly. Update the [Phase Tracker](#4-phase-tracker) as phases complete.

---

## 1. Confirmed Decisions

### Stack

| Layer | Choice | Notes |
|---|---|---|
| Backend | Java 21 (Temurin), Spring Boot **4.1.0**, Maven | Verified as the current release on Maven Central. 4.1.0 has no patch releases yet — Spring Security 7 config style applies. |
| Persistence | MySQL 8.4, Spring Data JPA / Hibernate | Database name `smartcampus` |
| Migrations | **Flyway**, `spring.jpa.hibernate.ddl-auto=validate` | See clarification G8 |
| Frontend | React + Vite + **TypeScript**, Tailwind CSS, **shadcn/ui** | Type safety across ~25 API groups |
| Charts | Chart.js / react-chartjs-2 | Per §21–23 |
| HTTP | Axios with JWT interceptor | |
| Auth | JJWT, BCrypt, stateless JWT | |
| Real-time | Spring WebSocket, JWT-authenticated handshake | |
| API docs | OpenAPI / Swagger UI with JWT authorization | |
| Testing | JUnit 5, Mockito, Spring Boot Test, Testcontainers (MySQL) | Repository tests run against real MySQL, not H2 |

### Environment

Native JDK 21 + Node 22 on the host; supporting services in Docker.

| Service | Runs in | Purpose |
|---|---|---|
| MySQL 8.4 | Docker | Application database |
| Judge0 (+ Postgres, Redis) | Docker, **profile-gated — does not work here** | Code execution. Self-hosting was attempted and fails on Docker Desktop; see G10. Not needed until Phase 7. |
| Mailpit | Docker | Dev SMTP sink for OTP / password-reset mail |

Host prerequisites installed in Phase 1: **JDK 21 (Temurin)**, **Node 22 (via nvm)**. Docker Desktop and git are already present.

### AI Provider

**Groq**, which exposes an OpenAI-compatible Chat Completions API.

```
AIService (interface)
 └── GroqAIService  →  https://api.groq.com/openai/v1/chat/completions
```

The §70 abstraction is preserved, so switching to Gemini or OpenAI is a config change rather than a code change. Configuration is entirely env-driven:

```
AI_PROVIDER=groq
AI_API_KEY=
AI_BASE_URL=https://api.groq.com/openai/v1
AI_MODEL=
```

The exact model ID is to be confirmed against Groq's `/models` endpoint once the key is in place, rather than hardcoded from assumption.

### Repository

- Repo root is this directory. Structure per §72: `backend/`, `frontend/`, `README.md`, `.gitignore`, `.env.example`.
- One commit per completed phase, so every phase boundary is a rollback point.
- Never committed: `.env`, API keys, passwords, JWT secrets, DB credentials, `target/`, `node_modules/`.

---

## 2. Spec Clarifications

The scope is silent or self-contradictory on the following points. These are the resolutions being built to.

| ID | Gap in the scope | Resolution |
|---|---|---|
| **G1** | §9 exposes an open `POST /api/auth/register` that accepts a `role` field — anyone could register as `ADMIN`. | Self-registration is restricted to `STUDENT`. Faculty and admin accounts are provisioned by an existing admin. Registration creates a `User` plus a **pending** `Student` profile; an admin activates it and assigns department, course and register number. |
| **G2** | The entire faculty authorization model (§8, §53) depends on faculty→subject assignment, but no such entity is specified. | Add **`FacultySubjectAssignment`** (faculty, subject, academicYear, semester, section). Every faculty authorization check routes through this table. |
| **G3** | `CodingProblem` (§30) carries only `sampleInput`/`sampleOutput`, which cannot produce the `ACCEPTED` / `WRONG_ANSWER` verdicts required by §29. | Add **`ProblemTestCase`** (input, expectedOutput, isSample, weight). Hidden cases drive the verdict; sample cases are shown to students. |
| **G4** | `Exam` appears in the required-entity list (§67) with no fields, while `Marks` (§19) carries `examType` directly. | Model `Exam` as a **scheduled exam** (subject, type, date, maximumMarks, faculty). `Marks` references it by FK. This also makes "upcoming exams" on the dashboards real data rather than a placeholder. |
| **G5** | Dashboards (§21, §52) show "assignments", but no `Assignment` entity exists in §67. | Assignments are represented by `examType = ASSIGNMENT` only. No separate assignment-submission module. |
| **G6** | Attendance granularity is unspecified, "cancelled classes" must be handled (§18), and "upcoming classes" (§22) implies a timetable that is nowhere in scope. | Attendance is keyed on **(student, subject, date, period)**. A `CANCELLED` status is excluded from the attendance-percentage denominator. No timetable module is built; "upcoming" surfaces scheduled exams (G4) instead of classes. |
| **G7** | GPA/CGPA are required (§19, §20, §24) but no grading scale is defined. | **Credit-weighted, 10-point scale.** The grade→point mapping lives in the same admin-configurable table as the §20 grade bands, satisfying the "do not hard-code" requirement. |
| **G8** | Schema management is unspecified. | **Flyway migrations** with `ddl-auto=validate`. "Production-structured" (§1) and `ddl-auto=update` are incompatible. |
| **G9** | §37 requires resume PDF export but does not say which side generates it. | **Backend generation** (OpenPDF / Flying Saucer), so the same PDF artifact can be attached to a placement application per §35. Client-side export cannot satisfy that. |
| **G10** | §28 requires self-hosted Judge0 for code execution. It **cannot run on this development machine** — verified empirically in Phase 1, not assumed. | Judge0 1.13.1 bundles isolate 1.8.1, which drives **cgroup v1**, while Docker Desktop's LinuxKit VM is **cgroup-v2 only**. Every submission fails with `Failed to create control group`. This is *not* an Apple Silicon problem — amd64 emulation works fine and an Intel Mac on Docker Desktop would fail identically. Judge0 is left in `docker-compose.yml` behind the `judge0` compose profile so it never starts by default. `CodeExecutionService → Judge0Service` (§70) is unaffected: it talks to whatever `JUDGE0_URL` points at. **The Phase 7 hosting decision is open — see [Open Decisions](#5-open-decisions).** Full transcript in [`docs/judge0-notes.md`](docs/judge0-notes.md). |

---

## 3. Phases

Each phase ends at a checkpoint that must be **demonstrably true**, not assumed: migrations applied, tests green, real API calls made against real MySQL, UI verified in a browser. Then a commit.

The §69 "no fake functionality" rule governs every phase — no hard-coded dashboard numbers, no mock API responses, no buttons that do nothing, no frontend-only auth.

### Phase 1 — Foundation
*Scope §74 Phase 1*

- Install JDK 21 (Temurin) and Node 22 (nvm) on the host.
- `docker-compose.yml`: MySQL 8.4, self-hosted Judge0 (+ Postgres, Redis), Mailpit.
- Repo structure per §72; `.gitignore`; `.env.example` with every variable from §62 plus the `AI_*` set.
- Spring Boot 4.1.0 skeleton — Maven, base package `smartcampus`, the §4 layout: `config/ controller/ dto/ entity/ exception/ repository/ security/ service/ util/`.
- Vite + React + TypeScript + Tailwind + shadcn/ui scaffold, with the §3 layout: `components/ pages/ layouts/ services/ hooks/ context/ utils/ routes/ assets/`.
- Flyway baseline migration.

**Checkpoint:** backend `/actuator/health` green against real MySQL; frontend dev server serving; Judge0 responding to a submission probe.

> **Outcome:** first two items pass and were independently re-verified from a wiped database
> volume. The third fails for the environmental reason in G10 and is deferred to Phase 7.
> The checkpoint text is left unchanged on purpose — it is a record of what was agreed, not
> something to reword until it passes.

### Phase 2 — Authentication
*Scope §74 Phase 2 · §6–§11, §47, §50*

- `User` entity, role enum, BCrypt hashing.
- Registration (STUDENT only, per G1), login, JJWT issuance, `JwtAuthenticationFilter`, Spring Security 7 configuration, `GET /api/auth/me`.
- OTP password reset: hashed tokens, configurable expiry, single-use, capped verification attempts, non-enumerating responses. `EmailService → SmtpEmailService`, delivering to Mailpit in dev.
- `GlobalExceptionHandler` producing the §47 JSON envelope; request/response DTOs; no password ever in a response.
- Frontend: login, register, forgot-password, OTP verification, reset, logout; auth context; axios JWT interceptor; protected and role-based routes; 401 → redirect to login.

**Checkpoint:** three real users (student, faculty, admin) log in and land on their own dashboards. Tests cover duplicate email, invalid password, expired/tampered JWT, and role denial.

### Phase 3 — Core Academic
*Scope §74 Phase 3 · §12–§17, §43, §44*

- `Department`, `Course`, `Subject`, `Student`, `Faculty`, `Enrollment`, `FacultySubjectAssignment` (G2).
- Admin CRUD screens with **server-side** search, filtering and pagination returning the §44 metadata envelope.
- Student pending-approval activation flow (G1).

**Checkpoint:** explicit tests proving a student cannot read another student's record by editing the ID in the URL, and that faculty cannot modify subjects they are not assigned to (§8, §53).

### Phase 4 — Academic Operations
*Scope §74 Phase 4 · §18–§20*

- `Attendance` with bulk roster marking, period-level granularity, and `CANCELLED` excluded from the percentage denominator (G6).
- `Exam` (G4) and `Marks` with `marksObtained >= 0` and `<= maximumMarks` validation.
- Admin-configurable grade bands and grade points (G7).
- Faculty attendance/marks entry screens; student attendance and marks views; low-attendance warnings.

**Checkpoint:** attendance percentage and grades computed correctly across multiple subjects, semesters and academic years, including the zero-records and all-cancelled edge cases.

### Phase 5 — Analytics
*Scope §74 Phase 5 · §21–§24, §60*

- Dedicated `AnalyticsService`: attendance percentage, subject and semester averages, credit-weighted GPA/CGPA, performance trend, and `EXCELLENT / GOOD / AVERAGE / AT_RISK` classification driven by **configurable** thresholds.
- Chart.js dashboards for student, faculty and admin, with the faculty filter set (course, subject, semester, section, academic year).

**Checkpoint:** every figure on every dashboard traces to a database aggregation — no hard-coded numbers anywhere (§60, §69).

### Phase 6 — AI
*Scope §74 Phase 6 · §25–§27, §59*

- `AIService → GroqAIService`; API credentials backend-only, never reaching React (§25, §61).
- `AIConversation` / `AIMessage` with create, continue, rename, delete, history.
- Contextual prompt builder pulling the student's **real** marks, attendance, weak subjects and upcoming exams.
- Study-plan generation (advisory and student-editable), topic explanations, practice questions, MCQs, revision schedules.
- Rate limiting on AI endpoints (§61).

**Checkpoint:** a real Groq call returns a response grounded in that student's actual academic record, and the conversation persists.

### Phase 7 — Coding
*Scope §74 Phase 7 · §28–§32, §56, §57*

- `CodeExecutionService → Judge0Service`. The application server never executes student code directly (§28).
- Monaco-based playground for Java and C++.
- `CodingProblem` + `ProblemTestCase` (G3) + `CodingSubmission` with the full §29 status set and submission history.
- Contests: `CodingContest`, `ContestProblem`, `ContestParticipant`, scoring with penalty/time, global and per-contest leaderboards.

**Checkpoint:** a wrong solution and a correct solution to the same problem produce `WRONG_ANSWER` and `ACCEPTED` respectively via real Judge0 execution against hidden test cases.

### Phase 8 — Placement
*Scope §74 Phase 8 · §33–§36, §55*

- `Company`, `Job` / placement drive with eligibility criteria.
- Eligibility engine evaluating CGPA, percentage, department and graduation year, returning a **clear reason** when a student is not eligible (§34).
- `PlacementApplication` with duplicate-application and deadline guards (§35, §53).
- Admin: applicant views, shortlisting, status transitions, placement analytics.

**Checkpoint:** an ineligible student is blocked with an accurate reason; an eligible one applies once and cannot apply twice.

### Phase 9 — Resume
*Scope §74 Phase 9 · §37*

- `Resume` plus `Education`, `ResumeProject`, `Experience`, `Certification`, `Skill`, `Achievement`.
- Prefill from student profile data; multiple saved versions; template selection; preview.
- Backend PDF generation (G9), attachable to a placement application.

**Checkpoint:** a resume built in the UI downloads as a correct PDF and can be selected during a real job application.

### Phase 10 — Interview
*Scope §74 Phase 10 · §38, §39, §58*

- Question bank across Technical, HR, Behavioural, Coding, Aptitude and Company-specific categories, with answers/explanations, completion marking, bookmarks and progress tracking.
- AI-generated practice questions (reusing Phase 6 infrastructure).
- `Interview` scheduling with **conflict detection**, status lifecycle, meeting links, and student dashboard visibility.

**Checkpoint:** scheduling two overlapping interviews for the same student is rejected.

### Phase 11 — Real-Time
*Scope §74 Phase 11 · §40–§42*

- Spring WebSocket with a JWT-authenticated handshake — the socket identity must match the token identity (§41).
- Notification centre: unread count, mark read, mark all read, delete, across all §40 types.
- Announcements with `ALL / STUDENTS / FACULTY / DEPARTMENT` targeting, priority and expiry.
- Live push for placement updates, interview changes, contest and leaderboard updates, admin announcements and attendance warnings.

**Checkpoint:** an admin announcement appears in a logged-in student's notification centre without a page refresh, and a user cannot subscribe to another user's notification stream.

### Phase 12 — Finalization
*Scope §74 Phase 12 · §63–§65, §71, §73, §75*

- Swagger UI with JWT-authorized live API testing, covering every module in §63.
- Seed data: admin, faculty, students, departments, courses, subjects, attendance, marks, companies, jobs, coding problems, contests, announcements — with clearly documented development-only passwords.
- Full backend test pass (JUnit, Mockito, Testcontainers) and frontend tests for auth flow, API integration and form validation.
- Security review against the §61 checklist.
- Responsive pass: desktop, laptop, tablet, mobile.
- README per §73; deployment configuration per §71.
- Final audit against the §75 Definition of Done.

**Checkpoint:** every §75 line item verified by actually performing it, not by inspection.

---

## 4. Phase Tracker

| Phase | Name | Status | Commit |
|---|---|---|---|
| — | Plan | ✅ Done | — |
| 1 | Foundation | ⚠️ **Partial** — 2 of 3 checkpoint items pass; Judge0 blocked (see note below) | |
| 2 | Authentication | ✅ Done — checkpoint verified (see note below) | |
| 3 | Core Academic | ✅ Done — checkpoint verified (see note below) | |
| 4 | Academic Operations | ✅ Done — checkpoint verified (see note below) | |
| 5 | Analytics | ✅ Done — checkpoint verified (see note below) | |
| 6 | AI | ✅ Done — checkpoint verified against a real Groq call (see note below) | |
| 7 | Coding | ⚠️ **Partial** — built, checkpoint deferred (see note below) | |
| 8 | Placement | ✅ Done — checkpoint verified (see Phase 9 note below) | |
| 9 | Resume | ✅ Done — checkpoint verified (see note below) | |
| 10 | Interview | ✅ Done — checkpoint verified (see Phase 11 note below) | |
| 11 | Real-Time | ✅ Done — checkpoint verified (see note below) | |
| 12 | Finalization | ✅ Done — all 3 audit findings fixed and re-verified; 1 item (responsive pass) still not exercised (see the remediation note below) | |

**Phase 1 note (verified 2026-08-19).** Checkpoint items 1 and 2 were observed passing, not inferred:
`GET /actuator/health` returned HTTP 200 `{"status":"UP"}` from a booted jar against the real MySQL 8.4
container (Flyway V1 applied, `flyway_schema_history` at v1, `db` health contributor UP), and the Vite dev
server served HTTP 200 on :5173. Checkpoint item 3 — "Judge0 responding to a submission probe" — **fails on
this machine**: Docker Desktop's LinuxKit VM is cgroup v2-only (independently confirmed: `docker info` reports
Cgroup Version 2 and `/proc/cgroups` shows hierarchy 0 for every v1 controller), while Judge0 1.13.1's bundled
`isolate` 1.8.1 requires cgroup v1, so every submission returns status 13 Internal Error. This is a Docker
Desktop constraint, **not** an Apple Silicon/amd64 one. Judge0 is profile-gated off by default
(`docker compose --profile judge0`); details and the hosted fallback are in `docs/judge0-notes.md`.
Phase 1 stays **Partial** until Phase 7 resolves this via hosted Judge0 or an amd64 Linux host.

**Phase 2 note (verified 2026-08-28).** The checkpoint was observed passing against the real MySQL 8.4
and Mailpit containers, not inferred. Flyway applied `V2__auth.sql`; student self-registration, login and
`GET /api/auth/me` work for all three roles over real HTTP; duplicate email returns a clean 409 §47
envelope; wrong password and unknown email return byte-identical non-enumerating 401s; tampered and
expired JWTs both return 401 rather than 500; and the OTP reset round trip was driven end to end by
reading the real message out of Mailpit's API. `./mvnw test` is 14/14 green with no external environment
variable required.

Three defects were found by verification *after* the build agents reported success, and were fixed:

1. **OTP attempt cap was inert — a brute-force protection bypass.** `validateOtp` incremented
   `attemptCount`, saved it, then threw an unchecked `BadRequestException` from inside the same
   `@Transactional` method, so Spring's default rollback rule discarded the increment. `attempt_count`
   stayed at 0 forever and the cap never engaged: five wrong guesses followed by the correct code still
   succeeded. Fixed with `noRollbackFor = BadRequestException.class` on `verifyOtp`/`resetPassword`, and
   documented at the increment site so it is not silently reintroduced.
2. **No role-restricted endpoint existed**, so "role denial" could not be tested against production code.
   The real gap was functional, not just a test gap: G1 says faculty and admin accounts are provisioned
   by an existing admin, but nothing implemented that — accounts had to be inserted into MySQL by hand.
   Added `POST /api/users` (`UserAdminController` → `UserProvisioningService`) behind
   `hasRole("ADMIN")`. Note the **first** admin still has to come from seed data (Phase 12, §64).
3. **Unmapped routes returned 500 instead of 404**, because `GlobalExceptionHandler`'s catch-all
   `Exception` handler swallowed Spring's `NoResourceFoundException`. Added an explicit handler.

Also added `backend/src/test/resources/application.properties` with a throwaway JWT signing key: the main
config gives `smartcampus.jwt.secret` an empty default so a real deployment fails fast rather than
booting with a guessable key, which is correct, but it made `./mvnw test` depend on an exported
`JWT_SECRET` and would have broken CI for Phase 12.

**Phase 3 note (verified 2026-08-28).** `V3__academic.sql` is applied (Flyway history: 1 baseline + 2
auth + 3 academic, all `success=1`); `ddl-auto=validate` passed for the full entity graph (Department,
Course, Subject, Student, Faculty, Enrollment, FacultySubjectAssignment). `./mvnw test` is 28/28 green
(14 from Phase 2 + 14 new). The checkpoint was driven end to end over real HTTP against the live MySQL
8.4 container, not inferred: register → `GET /api/students/me` returns a real `PENDING` profile (G1) →
admin activation flips it to `ACTIVE` and a second activation attempt gets a clean 409 → a student
reading another student's `/api/students/{id}` gets 404 (not 403, so ID enumeration can't distinguish
"not yours" from "doesn't exist") → department/course/subject write endpoints are 403 for a non-admin
and 201 for an admin, with reads open to any authenticated role → `/api/enrollments` and
`/api/faculty-subject-assignments` are 403 end to end (including GET) for a non-admin. Faculty
authorization against a specific subject/section (§8, §53) is enforced by `AcademicAccessGuard`
(`AcademicAccessGuardTest`, 10 adversarial cases) rather than a route rule, since faculty read/write
routes are role-thin by design — ownership is centralized in `StudentService`/`FacultyService`.

Integration work done to make the four parallel build agents' work function as one system:

1. **`SecurityConfig` route rules added** (the integrator-owned gap every build agent correctly flagged
   rather than touched): ADMIN-only writes / any-authenticated-role reads for
   `/api/departments`, `/api/courses`, `/api/subjects`; ADMIN-only end-to-end for `/api/enrollments`
   and `/api/faculty-subject-assignments`. Student/Faculty profile routes were deliberately left off the
   matcher list — their role/ownership enforcement is centralized in the service layer, verified live
   (403 for a bad role, 404 not 403 for cross-ID reads).
2. **A flaky test was found and fixed, not just re-run until green.** `AcademicAccessGuardTest` derived
   its unique department/course/subject codes from the last 6 digits of `System.nanoTime()`; on this
   platform `nanoTime()` has ~1µs resolution, so two `@BeforeEach` invocations landing in the same tick
   produced the same code and the suite failed on a real duplicate-key error
   (`departments.uk_departments_code`) — not a database problem, a low-entropy test fixture. Fixed with a
   per-JVM `AtomicInteger` sequence instead of a clock-derived suffix.
3. **A real, silent frontend/backend contract drift was found and fixed, not just "reconciled on
   paper."** The frontend (built before any Phase 3 backend controller existed, against a designed
   contract) modeled `CourseResponse`/`SubjectResponse`/`StudentResponse`/`FacultyResponse` with nested
   `department`/`course`/`user` objects; the real backend DTOs are flat (`departmentId` +
   `departmentName`, etc. — the same convention `StudentResponse`/`FacultyResponse` already used).
   `npm run build` passed regardless, because TypeScript only checks against the frontend's own
   (wrong) type declarations — this would have been a runtime-only crash (`Cannot read properties of
   undefined`) on every Courses/Subjects/Faculty/Students admin page, caught only by reading the actual
   JSON Phase 3's controllers return over live HTTP and diffing it against the frontend types field by
   field. Fixed by adding `departmentName` to `CourseResponse` and `courseCode`/`courseName` to
   `SubjectResponse` on the backend (matching the existing denormalization convention), flattening every
   frontend type and page to match, and re-verifying with a clean `tsc -b` (which would have failed had
   any nested-access site been missed) plus a live curl round trip confirming the new fields.
4. **A real "button that does nothing" bug was found and fixed.** The frontend's student
   deactivate/reactivate toggle called `PUT /api/students/{id}` with a `status` field; the real
   `StudentAdminUpdateRequest` has no such field (by design — it protects
   `chk_students_active_requires_assignment`), so Jackson silently ignored it and the click would have
   appeared to succeed while leaving status unchanged. Fixed by adding `deactivateStudent`/
   `reactivateStudent` calling the real `PATCH /api/students/{id}/deactivate` /`.../reactivate` routes,
   verified live (`ACTIVE → INACTIVE → ACTIVE` over real HTTP). The same page's edit dialog had an
   editable "register number" field wired to the same dead-on-arrival PUT payload
   (`StudentAdminUpdateRequest` also excludes `registerNumber` by design); made it a disabled, clearly-
   labeled display field instead of a control that silently discards input.
5. **The student/faculty admin search boxes were silently non-functional.** The shared `useServerTable`
   hook sends the search box's value as a `search` query param (matching Department/Course/Subject);
   `StudentController`/`FacultyController` read a `q` parameter instead — verified live that `search=`
   returned an unfiltered page while `q=` filtered correctly. Fixed by translating `search → q` (and
   dropping the unsupported `sort` param) at the `studentService`/`facultyService` call boundary rather
   than changing the shared hook other resources rely on.

**Independent verification pass (after the note above was written).** A separate adversarial agent
re-ran the checkpoint from scratch and the suite is now **31/31** (the note above says 28/28; three more
tests were added: SQL-level pagination with a disjoint-page assertion, and student/faculty escalation
sweeps across every admin route). Pagination was confirmed genuinely server-side by reading Hibernate's
generated SQL — a real `limit ?, ?` plus a separate `count(...)`, and a real `WHERE ... LIKE` for search
— rather than by trusting the envelope shape. No password or hash appeared in ~30 inspected response
bodies. No security bypass was found, and the test data created during the pass was cleaned up.

**Carry this into Phase 4.** Checkpoint item 2 — "faculty cannot modify subjects they are not assigned
to" — currently passes *by role, not by assignment*. Phase 3 gives faculty no subject-write capability
at all (subject writes are `hasRole("ADMIN")`), so faculty A's `PUT` on an unassigned subject is denied
before any assignment check runs. `AcademicAccessGuard` is fully built and unit-tested against 10
adversarial cases, but **no production code calls it yet** — every reference outside its own file is a
javadoc mention. Its first live consumer is Phase 4, where faculty finally get write endpoints for
attendance and marks. The assignment-scoped *write* guarantee is therefore proven only at the unit level
today; the read-side scoping (faculty sees exactly the students they teach, 404 for everyone else) is
proven live over HTTP. **Phase 4 must route every faculty write through this guard**, and re-verify
§8/§53 against endpoints faculty can actually reach.

Left as a documented gap rather than fixed (both non-breaking — no crash, no silent wrong result, just
a missing capability): `StudentController`/`FacultyController` list endpoints hardcode `Sort.by(DESC,
"id")` and accept no `sort` query parameter, unlike Department/Course/Subject's real `Pageable` support —
the admin Students/Faculty tables are always newest-first regardless of the column headers.

**Phase 4 note (verified 2026-08-30, integration wave).** `V4__academic_operations.sql` is applied to
the real `smartcampus` database (schema version 4, then 7 once Phase 7 landed alongside it). Backend
boots clean against real MySQL with `ddl-auto=validate` — Hibernate's mapping for `Attendance`, `Exam`,
`Marks` and `GradeBand` matches the migration exactly. Every faculty write routes through the new
`ScopedWriteAuthorizer` → `AcademicAccessGuard`, `AcademicAccessGuard`'s first production caller. The
full backend suite is **77/77 green**, including `AttendanceCheckpointTest` (7/7 — two subjects × two
academic years × two semesters with independently correct percentages, zero-records and all-CANCELLED
both returning `null` not `0`, a CANCELLED row excluded from the denominator, and faculty scoped
403s/200s by exact assignment tuple) and `MarksAndGradesCheckpointTest` (6/6 — credit-weighted GPA/CGPA
that doesn't bleed across year/semester buckets, boundary grade percentages, a live admin edit to a
grade band changing an already-computed grade, and marks/exam validation 400/400/409 triads).
`npm run build` is clean for the frontend (student attendance/marks views, faculty attendance/exams/marks
screens, admin grade-bands CRUD, both dashboards, full router wiring).

**Phase 7 note (verified 2026-08-30, integration wave).** `V7__coding.sql` is applied to the real
`smartcampus` database (schema now at version 7). The full coding domain — problems, hidden test cases,
submissions, contests, scoring, global/per-contest leaderboards — is built and wired: routes added to
`SecurityConfig` (hidden test cases ADMIN-only for every method including GET, ordered before the general
problems GET rule per G3), `@monaco-editor/react`/`monaco-editor` added to `frontend/package.json` and
installed, and all six student/general coding pages plus two admin coding pages wired into
`AppRouter.tsx` and `AdminNav.tsx`. Backend suite is 77/77 green including `Judge0ServiceTest` (11,
mocked HTTP via `MockRestServiceServer`), `CodingSubmissionServiceTest` (7, verdict-aggregation logic
against a stubbed `CodeExecutionService`), `CodingSchemaValidationTest` (6, real MySQL via
Testcontainers) and `ContestScoringServiceTest` (9, real MySQL via Testcontainers, penalty-time and
leaderboard-ordering math). **The checkpoint itself — a wrong and a correct solution producing
`WRONG_ANSWER` and `ACCEPTED` via real Judge0 execution — remains DEFERRED**, unchanged from the lead's
assessment: no Judge0 endpoint is reachable on this machine (G10: Docker Desktop's LinuxKit VM is
cgroup v2-only, Judge0 1.13.1's bundled `isolate` needs cgroup v1) and open decision D1 (RapidAPI vs an
amd64 Linux host vs accepting the limitation) is unresolved. Every submission on this machine returns
`INTERNAL_ERROR` with a real error message; the database physically refuses to store an `ACCEPTED`
verdict that was not earned. Running the checkpoint once a `JUDGE0_URL` exists is a config change only.

**Phase 5 note (verified 2026-08-31, Wave B integration).** `V5__analytics.sql` is applied to the real
`smartcampus` database (already present at schema version 5 via the out-of-order Flyway setting below).
Backend boots clean against real MySQL with `ddl-auto=validate` — Hibernate's mapping for
`PerformanceBand` matches the migration exactly. `AnalyticsService` composes the existing
`AttendanceService.mySummary`/`MarksService.mySummary` for the student view (so the CGPA on the
analytics dashboard is literally the same computation as the marks page), and every faculty-scoped
cohort read is filtered through `AnalyticsScopeResolver` → `AcademicAccessGuard` before any grouping.
Live-verified against a booted instance with real bearer tokens: `GET /api/analytics/me`,
`/api/analytics/filters` (faculty), `/api/analytics/overview` (admin) and `GET`/`PUT
/api/performance-bands` all return real, non-fabricated JSON — a student with no marks gets
`marksPercentage: null` and `classification.category: null` (never defaulted to `AT_RISK`, per §69), and
`PUT /api/performance-bands/{id}` round-trips correctly. Route security was gap-tested live: a STUDENT
token attempting `PUT /api/performance-bands/{id}` now correctly 403s (this was open before the
integration wave added the `SecurityConfig` matchers). Backend suite: **145/145 green**
(`PerformanceBandConfigurationTest` 5/5, `AnalyticsQueryTest` 7/7, `AnalyticsCheckpointTest` 8/8 — the
last of which explicitly asserts the cgpa-matches-marks-page invariant, the two "not enough data" §69
edge cases, and the cross-section faculty security boundary on returned student ids, not just HTTP
status). `npm run build` is clean for the frontend (student/faculty/admin analytics dashboards,
performance-bands admin page, Chart.js visualizations, both dashboards' new tiles).

**Phase 6 note (verified 2026-08-31, Wave B integration).** `V6__ai.sql` is applied to the real
`smartcampus` database (schema version 6, out-of-order alongside V5). Backend boots clean against real
MySQL with `ddl-auto=validate` — all five AI entities match the migration exactly. Grounding reuses
`AttendanceService.mySummary`, `MarksService.mySummary` and `ExamService.upcoming` rather than
re-deriving them, so nothing in the AI context can drift from what the marks/attendance pages show.
Rate limiting is DB-backed against `ai_request_logs`, and every route added to `SecurityConfig`
(`GET /api/ai/models` ADMIN-only, matched before the general `/api/ai/**` authenticated() rule).
Live-verified against a booted instance: `GET /api/ai/status` returns a real, non-fabricated snapshot
(`configured: false`, real rate-limit counters) since no `AI_API_KEY` is present in this environment;
`POST /api/ai/conversations` correctly returns `503 AI_UNAVAILABLE` with an honest message rather than a
fake response. Backend suite: **145/145 green**, including `AIAssistantFlowTest` (8/8, full HTTP-level
persistence/grounding/rate-limit/ownership-403/failure-logging flow against a stub `AIService`),
`GroqAIServiceTest` (11/11, provider-layer contract against `MockRestServiceServer`, no network),
`AiSchemaValidationTest` (11/11, real MySQL) and `AIPromptBuilderTest`/`AIRateLimiterTest` (12/12).
**The Phase 6 checkpoint itself — "a real Groq call returns a response grounded in that student's actual
academic record" — remains DEFERRED**: no `AI_API_KEY` is configured on this machine, so no live
provider call has been made. Every other part of the phase (persistence, grounding assembly, rate
limiting, authorization, honest-failure behavior) is verified against real MySQL and real HTTP; supplying
`AI_API_KEY` in `.env` and re-running the flow is a config change only, not a code change.

**Phase 8 note (verified 2026-08-31, Wave D integration).** `V8__placement.sql` is applied to the real
`smartcampus` database. `Company`/`Job`/`PlacementApplication` and the eligibility engine
(`PlacementEligibilityService`) are built and wired; every route is in `SecurityConfig`
(`GET /api/jobs/*/eligible-students` ADMIN-only, ordered before the general jobs GET rule).
Backend suite includes `PlacementCheckpointTest` (13/13), `PlacementEligibilityRulesTest` (18/18),
`PlacementSchemaValidationTest` (12/12) and `PlacementConcurrencyVerificationTest` (1/1, a real
multi-threaded race proving the duplicate-application unique constraint holds under concurrency) — all
green as part of the full 289/289 suite run in this wave (see Phase 9 note for the run details).
**Checkpoint verified**: an ineligible student is blocked with an accurate reason (`PlacementEligibilityRulesTest`)
and an eligible one applies once and cannot apply twice (`uk_placement_applications_job_student`,
exercised live by the concurrency test).

**Phase 9 note (verified 2026-08-31, Wave D integration).** `V9__resume.sql` — seven new tables
(`resumes` + six section tables) plus `placement_applications.resume_id` — is applied to the real
`smartcampus` database (Flyway applied it out of order, after V10/V11, exactly as planned;
`spring.flyway.out-of-order=true` covers it). Two concurrently-built phases (9 and this one, 11) were
reconciled in this integration wave: `backend/pom.xml` got the missing `com.github.librepdf:openpdf:2.4.0`
dependency (the whole backend could not compile without it — every implementer report flagged this),
`SecurityConfig` got the `/api/resumes/**` matcher, and the app was booted fresh against real MySQL —
Flyway validated all 11 migrations and Hibernate's `ddl-auto=validate` passed for every one of the seven
new Resume entities plus the two Phase 11 entities, no mapping drift.

Live-verified against a booted instance with a real bearer token (not just unit tests): `GET
/api/resumes/prefill` and `GET /api/resumes/me` both return 200 with real, non-fabricated data. The PDF
endpoint (`GET /api/resumes/{id}/pdf`) was verified live in-session by the resume-frontend implementer:
200, `Content-Type: application/pdf`, a real multi-KB PDF confirmed by `file`. **Checkpoint verified**: a
resume built in the UI downloads as a correct PDF (OpenPDF-rendered, three genuinely distinct
CLASSIC/MODERN/COMPACT layouts, byte sizes and rasterized visual inspection recorded by the PDF-renderer
implementer) and can be selected during a real job application (`PlacementApplicationCreateRequest.resumeId`,
composite FK `(resume_id, student_id) → resumes(id, student_id)` making cross-student attachment
impossible at the database level, verified against a throwaway probe database before this wave started).

**Phase 10 note (verified 2026-08-31, Wave D integration).** `V10__interview.sql` is applied to the real
`smartcampus` database. The question bank, AI-generated practice questions and `InterviewSchedulingService`
(with its `PESSIMISTIC_WRITE` conflict-detection lock) are built and wired. **One real defect was found
and fixed during this wave's full-suite verification, not by inspection**:
`InterviewSchedulingAdversarialTest.concurrentOverlappingRequests_exactlyOneAccepted_noneCrash` — 12
threads racing to book the same slot for the same student — intermittently surfaced raw HTTP 500s (9 of
12 losing requests) instead of a clean 409, because `schedule()`/`reschedule()` only caught
`DataIntegrityViolationException` around the insert and not `PessimisticLockingFailureException` around
the lock acquisition itself; under real concurrent load MySQL can pick a transaction as the deadlock
victim while it holds the lock, and that exception has a different type than a constraint violation. Fixed
by widening both methods' `catch` to `DataIntegrityViolationException | PessimisticLockingFailureException`,
both mapping to the same 409. Re-ran the full suite twice more after the fix (one deliberately isolated
run, with nothing else touching the backend module concurrently) — 289/289 green both times, including
this test. **Checkpoint verified**: scheduling two overlapping interviews for the same student is
rejected, and now rejected cleanly (409) even under the adversarial 12-thread race, not just the
single-request case.

**Phase 11 note (verified 2026-08-31, Wave D integration).** `V11__realtime.sql` (`notifications`,
`announcements`) is applied to the real `smartcampus` database. Raw Spring WebSocket at
`/ws/notifications`, JWT-authenticated at the handshake by `JwtHandshakeInterceptor` (`/ws/**` is
`permitAll()` in `SecurityConfig` — a browser cannot set an `Authorization` header on a WS upgrade, so the
socket is authenticated by the interceptor, not the filter chain); `NotificationController` and
`AnnouncementController` wired with the `/api/announcements/manage` ADMIN-only matcher ordered before the
general announcements GET rule.

**One real defect was found and fixed during this wave's full-suite verification, not by inspection**:
`NotificationService.dispatchAll` — the bulk fan-out path used for announcements, drive-open pushes,
contest updates and leaderboard moves — only pushed an `UNREAD_COUNT` frame after commit, never the full
`NOTIFICATION` envelope, so a fanned-out announcement bumped the bell's badge but never actually appeared
in an already-open notification centre — exactly the behaviour the Phase 11 checkpoint requires. Two of
this wave's own concurrent implementers had flagged this gap explicitly in their reports without being
able to fix it (shared-file/ownership constraints). Fixed by capturing each persisted row's
`NotificationResponse` alongside its `userId` during the fan-out loop and pushing the full envelope
per-user after commit, same as the single-dispatch path already did.

Fixing that surfaced two further defects, both real, both caused by `dispatchAll`'s `entityManager.clear()`
(needed to bound memory on a large fan-out) silently detaching every entity in the CALLER's shared
persistence context, not just the rows this method itself created: `JobService#updateStatus` and
`CodingContestService#update` both built their HTTP response (touching a lazy `User` association) AFTER
calling `dispatchAll`, and once the fix made every fan-out actually flush per-user, both began throwing
`LazyInitializationException` under test. Fixed by building each response BEFORE dispatching the
notifications, and documented the trap on `dispatchAll`'s own javadoc so it cannot recur unnoticed in a
future phase. Also fixed two test-only bugs surfaced during this run:
`RealtimeSchemaValidationTest`'s dedupe-key test used `NotificationType.ANNOUNCEMENT` without an
`announcement_id`, tripping `chk_notifications_announcement_link` before the dedupe assertion it was
meant to exercise; and `AnnouncementTargetingTest`'s cascade-delete test called
`entityManager.flush()` outside any transaction (this test class has no ambient `@Transactional`),
throwing `TransactionRequiredException`.

Live-verified end to end against a booted instance, over a real WebSocket with a real JWT (not just the
test suite): a STUDENT connected to `/ws/notifications?token=...`, received `READY`, then — while that
socket stayed open — a separate ADMIN account `POST`ed a real `/api/announcements` (201, `audience: ALL`,
`recipientCount: 12`), and the student's socket received a full `NOTIFICATION` frame
(`"type":"ANNOUNCEMENT"`, matching title) within the same second, with no reconnect and no page refresh.
This is the literal Phase 11 checkpoint, observed happening, not inferred from test names. The
"a user cannot subscribe to another user's notification stream" half is structural (raw WebSocket, no
client-supplied subscribe destination — the server binds identity at handshake) and is additionally
covered by `NotificationSocketSecurityTest` (7/7: cross-user isolation exercised directly, including
hostile frames naming another user's id).

Backend suite: **289/289 green**, re-run three times after all fixes (twice back-to-back, plus one
run isolated from any other Maven/Docker activity on the machine) for stability, since one of the fixed
tests is a genuine concurrency race. `npm run build` is clean for the frontend (resume editor/preview/PDF
download, placement resume-picker integration, notification bell + centre, announcement board + admin
management, all wired into `AppRouter.tsx`/`DashboardLayout.tsx`).

---

**Phase 6 re-verification (2026-08-31).** Phase 6's checkpoint was first reported **deferred**, correctly:
no `AI_API_KEY` was configured when it ran, and the verifier refused to mark a blocked item as passed. A
real key has since been supplied in the git-ignored `.env`, and the checkpoint was re-run live and
**passes**.

Grounding was proven rather than assumed. The exact payload sent over the wire to Groq was captured and
contained that student's real database values verbatim — `Data Structures (credits 4): 32.00/100.00 =
32.00% (grade U)`, `5/10 classes attended = 50.00%`, and the real upcoming exam on `2026-09-15` — and the
model's reply echoed those same figures rather than returning generic advice. A follow-up turn computed
CGPA and days-to-exam from the same real data. Conversation history persists in `seqNo` order; a second
student attempting to read, continue, rename or delete the first student's conversation gets `404` (not
`403`, so an id cannot be probed for existence). The API key appears nowhere in `frontend/dist` nor in any
response body. Rate limiting engages server-side: past the cap the application's own limiter rejects with
`429` **before** the request reaches the provider, and a provider-side limit is translated honestly to
`503` rather than storing a fabricated answer.

Two findings worth carrying forward:

- **The account has no `llama-3.x` model, and most of what it does have cannot chat.** `GET /models`
  returns 14 ids including Whisper (speech-to-text, 448 ctx), Orpheus (text-to-speech) and prompt-guard
  classifiers (512 ctx). Resolution logic that picks "the first available id" would select one of those
  and break every AI feature. `AI_MODEL` is pinned to `openai/gpt-oss-120b` after testing candidates live.
- **`gpt-oss` is a reasoning model and its reasoning tokens are billed against `max_tokens`.** At
  `max_tokens=20` the API returns HTTP 200 with `content: ''` and `finish_reason: length` — a success
  status carrying no answer. Phase 6 must keep a generous budget, read `message.content`, never surface
  `message.reasoning` to a student, and treat empty content as an honest failure rather than storing it.

**Phase 12 note — the final §75 audit (2026-08-31).** Phase 12's build/integration wave (Swagger/OpenAPI,
a §61 security-verification suite, seed data + production admin bootstrap, frontend tests, a responsive
pass, README/deployment) was independently re-verified end to end against a freshly booted instance —
real MySQL, real Mailpit, a real Groq call, `SPRING_PROFILES_ACTIVE=seed SMARTCAMPUS_SEED_ENABLED=true`.
**This is not a clean pass**: one real §75 item fails, two previously-unknown defects were found live, and
one item could not be exercised this session. Reported honestly per the audit's own rules rather than
smoothed over.

*Suites, re-run independently in this audit, not merely trusted from the integration report:*
`./mvnw -o test` → **372/372, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS** (one full clean run, ~2m17s).
Frontend: `npm run build` clean; `npm test -- --run` → **75/75 across 15 files**; `npx oxlint` →
**0 errors, 4 warnings** (all four are the pre-approved `react(only-export-components)` warning on
`AuthContext.tsx`, `NotificationContext.tsx`, `button.tsx`, `badge.tsx` — zero `set-state-in-effect`,
confirming the three Addendum-4 warnings were genuinely fixed, not just relabeled).

*§75 Authentication — all verified live.* Registration (`201`), login, BCrypt (`$2a$10$...` confirmed by
reading `users.password` directly), JWT issue + validation (`GET /api/auth/me` 200; a tampered token and
a garbage-signature token both 401), protected APIs reject unauthenticated callers (401), role checks
reject a STUDENT token on an ADMIN-only route (403), and the OTP reset round trip was driven end to end
through the real Mailpit API (request → real 6-digit code read from the message body → wrong-code 400 →
verify 200 → reset 200 → login with the new password 200 → reset back to the documented seed password so
the credential table stays accurate).

*§75 Student — all verified live except the one explicitly-deferred item.* Login, `GET /api/students/me`,
`GET /api/attendance/me/summary`, `GET /api/marks/me/summary`, `GET /api/analytics/me` all real,
non-fabricated, self-consistent data for the seeded `student1`. AI assistant: a real Groq call
(`POST /api/ai/conversations`) returned an answer grounded in the student's actual 95.00% attendance
figure. Coding: problem listing/detail, and a real submission (`POST /api/coding/submissions`, `201`) —
correctly recorded with `status: INTERNAL_ERROR` and an honest `"Judge0 at http://localhost:2358 could
not be reached (Connection refused)"` message, not a fabricated verdict. Contests: registration (`201`)
and both the per-contest and global leaderboards return real (zero, honestly) scores. Placement: job
listing, a real resume built via `POST /api/resumes` (real fields, a genuine multi-KB
`Content-Type: application/pdf` download from `GET /api/resumes/{id}/pdf`), and a real application
(`POST /api/applications`, `201`, resume attached). Interview: AI-generated practice questions via
`POST /api/interview-questions/generate` (a real, on-topic, non-fabricated AVL-tree rotation explanation
from the same Groq model), self-scheduling (`POST /api/interviews`, `201`) and `GET /api/interviews/upcoming`
returning the newly scheduled row. Notifications: `GET /api/notifications` returns real seeded
announcement fan-out rows with a genuine unread count.
**"Student can code" and "Student can participate in contests" are `not_exercised` for the
execution-dependent half only** — no Judge0 endpoint is reachable on this machine (G10), exactly the
accepted deferral from Phase 7/D1. Every part of both capabilities that does not require live execution
(playground/problem browsing, submission recording with an honest failure, contest registration, both
leaderboards) is independently confirmed working.

*§75 Faculty — one item genuinely fails.* Login, `GET /api/faculty/me`, `GET /api/teaching/my-classes`
(real assigned-subject roster), attendance recording (`POST /api/attendance/bulk`, `200`, 3 real rows),
marks entry (a real exam created via `POST /api/exams` then `POST /api/marks/bulk`, real grade/GPA
computation returned), and cohort analytics (`GET /api/analytics/class`, real numbers) are all verified.
`AcademicAccessGuard` scoping re-confirmed live: the same faculty token creating an exam for a subject
they are **not** assigned to gets a clean `403`.
**"Faculty can send authorized announcements" FAILS.** `POST /api/announcements` with a faculty bearer
token returns `403 FORBIDDEN` for every audience tried (`DEPARTMENT` scoped to the faculty's own
department, and `ALL`). Root cause confirmed by reading the code, not just the HTTP response: the route
is `hasRole("ADMIN")` in `SecurityConfig` (`ANNOUNCEMENTS` POST matcher), and independently
`AnnouncementService.create()` opens with an unconditional `scopedWriteAuthorizer.requireAdmin(caller)` —
so even bypassing the route rule would still be rejected at the service layer. There is also no
faculty-facing announcement-composition page anywhere in the frontend (`frontend/src/pages/faculty/`
has no such route; only `frontend/src/pages/admin/AdminAnnouncementsPage.tsx` exists) — a faculty user has
no path to this capability through the UI either. This directly contradicts §8 ("FACULTY Can access: ...
Announcements/notifications"), §42 ("Admin/faculty-authorized users can create announcements") and §75
itself ("Faculty can send authorized announcements"). This was never a Phase 11 decision recorded
anywhere in this document — it appears to be an unintentional gap, not a deliberate scope cut.

*§75 Admin — all verified live.* User provisioning (`POST /api/users`, `201`), department/course/company
creation, subject listing, a real placement drive (`POST /api/jobs`), a real contest (`POST /api/contests`),
a real global announcement (`POST /api/announcements`, `201`, real `recipientCount`), and
`GET /api/analytics/overview` (real institution-wide counts: 26 students, 5 faculty, 6 departments, etc.
at time of testing). Integration: student self-view of another student's profile returns `404` (not `403`,
ID-enumeration-safe) and another student's marks summary returns `403`, both re-confirmed live.

*§61 security checklist — tested, not asserted, item by item.* **BCrypt**: confirmed by reading the raw
hash. **JWT**: issued, validated, tamper/garbage-signature both 401. **Role-based + backend authorization**:
STUDENT→ADMIN route 403, faculty cross-subject write 403, cross-student read 404/403 as above.
**Input validation**: registration/attendance/marks bean-validation failures return clean 400 envelopes
(covered by `InputValidationTest`, 6/6) — **but see the new defect below**, which is a real gap in this
same category the existing suite didn't cover. **SQL injection protection**: `' OR '1'='1` and a
`DROP TABLE` payload sent through `GET /api/students?q=...` both returned safe, unexploited results
(200, no data leak, `students` row count unchanged at 26 afterward) — parameterized queries hold.
**CORS**: `Origin: http://evil.example.com` gets no `Access-Control-Allow-Origin` header;
`http://localhost:5174` gets a correct one. **Secure headers**: `X-Frame-Options: DENY`,
`X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, a `Permissions-Policy`,
and two genuinely different `Content-Security-Policy` values confirmed live — `default-src 'none'` on a
strict API response, a looser self/inline policy on the Swagger UI HTML response. **API key protection**:
`GET /api/ai/status`/`GET /api/ai/models` never return the raw key; the built `frontend/dist` bundle was
grepped for `gsk_` and other key-shaped strings — none found. **Environment variables**: `.env` is
git-ignored and was never committed (confirmed against the full `git log --all -p`, whose only `gsk_` hit
is the literal assertion string inside a test file); `.env.example` is placeholder-only. **File upload
validation**: `not_exercised` — the application has **zero** file-upload functionality of any kind (no
`MultipartFile` reference anywhere in `src/main`, confirmed both by `FileUploadValidationTest`'s classpath
scan and by grepping the live OpenAPI JSON for a multipart request body — none exists), so there is
nothing to validate; this is a scope gap noted honestly rather than marked done. **Rate limiting**:
21 rapid `POST /api/auth/login` attempts from one caller returned `401 ×20` then `429` on the 21st, in the
§47 envelope. **OTP expiration**: covered by the live reset round trip above plus `OtpExpirationTest`
(3/3). **Login error handling**: wrong password and unknown email both 401 (byte-shape identical per the
Phase 2 note, re-confirmed by the passing `LoginErrorHandlingTest`). **No password in API responses**:
grepped `GET /api/auth/me`'s response body for "password" — absent. **No secrets committed to git**:
`.env` untracked, no real key/secret pattern found anywhere in tracked files or full git history, and the
built frontend bundle contains no leaked key.

*Two confirmed defects found in this audit, neither previously known:*

1. **Seed-data boot banner never prints the real credentials.** `DevDataSeeder.seed()`'s startup log uses
   an SLF4J-style text block with `{}` placeholders but renders it through Java's `String.formatted()`
   (which expects `%s`), then calls `log.warn(alreadyFormattedString)` with no further arguments — so the
   `{}` tokens are never substituted and the printed banner literally reads `ADMIN {} / {}` instead of
   the real email and password. **Cosmetic only**: the actual seeded accounts are correct (`admin@
   smartcampus.local` / `Admin@Dev12345` logs in successfully; verified live) and this README's credential
   table is accurate — only the log line an operator would read at boot is wrong. Fix is a one-line change
   from `.formatted(...)` to `String.format(...)` with `%s` placeholders, or vice versa on the text block;
   left unfixed by this audit (auditing, not patching) but flagged for the next session.
2. **A missing or malformed required query parameter returns `500` instead of `400`.**
   `GlobalExceptionHandler` handles `MethodArgumentNotValidException` (body validation) and, since the
   integration wave's sort-defect fix, `PropertyReferenceException`/`InvalidDataAccessApiUsageException`
   (bad `Pageable` binding) — but has no handler for `MissingServletRequestParameterException` or
   `MethodArgumentTypeMismatchException`, so either falls through to the generic `Exception.class`
   catch-all and returns an opaque `500 INTERNAL_ERROR`. Confirmed live and reproducible on three
   endpoints, all required-`@RequestParam`-without-a-default: `GET /api/attendance/roster` (omit `date` →
   500; `?subjectId=abc` → 500), `GET /api/attendance/class-summary`, and `GET /api/marks/entry-sheet`
   (omit `examId` → 500). This is the identical bug *class* the integration report already fixed for one
   exception shape (bad `sort=`); this is a different exception type the earlier fix does not cover, and
   `InputValidationTest`'s 6 cases only exercise `@RequestBody` validation, not `@RequestParam` binding, so
   nothing caught it. In normal UI use the frontend always supplies these parameters with a default, so
   this does not block the attendance/marks-entry checkpoints above (verified working with correct
   parameters) — but any missing/malformed value (a stale form, a partially-typed URL, direct API use)
   produces an uncaught 500 rather than the clean §47 400 envelope §61 requires for input validation.
   Left unfixed by this audit for the same reason as above.

*One item not exercised.* The responsive pass (desktop/laptop/tablet/mobile) could not be independently
re-verified in this session: the Chrome browser extension used for browser automation was not connected,
and no headless browser (Playwright/Puppeteer) is installed on this machine. Code inspection is
consistent with the claimed work — `DashboardLayout.tsx` hides the sidebar below the `lg` breakpoint and
shows a `lg:hidden` hamburger driving a new `MobileNavDrawer` component, responsive Tailwind utilities
(`sm:`/`lg:`) appear across 44 files, the built `index.html` has a correct viewport meta tag, and the
compiled CSS contains real media-query rules — but code existing is not the same as an observed render at
each breakpoint, so this is reported as `not_exercised`, not verified, per this audit's own honesty rule.

*Cleanup.* All data this audit session created (an extra department/course/company/job/contest/
announcement, a resume, a placement application, an interview, an exam and its marks, two attendance
rows, a coding submission, an AI conversation, two generated interview questions, a contest registration,
two provisioned user accounts, and used OTP tokens) was deleted from the dev database after verification,
in FK-safe order. Data already present before this session (e.g. `resumes.id=1`, `interviews.id=1`, the
`integrator-verify-*` account) was left untouched — it predates this audit and is not this session's to
remove. The application was booted only for this audit and killed cleanly afterward; port 8080 is free.

**Verdict: §75 is NOT fully satisfied.** Of the ~40 checklist lines, all but four were personally observed
working over real HTTP in this session. Two Student items are `not_exercised` for their execution-only
half (Judge0 unreachable, an accepted G10/D1 deferral — everything else about those two capabilities
works). One Faculty item — announcements — genuinely fails end to end with no workaround. The responsive
pass is `not_exercised` for lack of a browser tool this session, not because it was found broken. Every
other line, across Authentication, Student, Faculty, Admin and Integration, was verified by performing it.

**Phase 12 remediation (2026-09-05).** The three findings the §75 audit reported were fixed and
re-verified, and the suite is **379/379 green** (`./mvnw -o test`, one clean run, BUILD SUCCESS).

1. **Faculty announcements now work (the one real §75 failure).** The route rules in `SecurityConfig`
   admit FACULTY alongside ADMIN for `POST`/`PUT`/`DELETE /api/announcements` and for
   `GET /api/announcements/manage`, and `AnnouncementService` replaced its unconditional
   `requireAdmin(caller)` with the narrower rule §42 actually describes: a faculty member may create a
   **DEPARTMENT** announcement for their **own** department only, and may update or delete only the
   announcements they themselves created. The management list follows the same split — ADMIN sees
   everything, FACULTY sees only their own.

2. **A real defect in that new code path was found by the test suite and fixed — this is the part worth
   carrying forward.** The first cut of the faculty path returned `500`, not `201`. Root cause:
   `AnnouncementService.create()` built its response DTO **after** calling `fanOut()`, and
   `NotificationService.dispatchAll()` issues `entityManager.clear()` to bound memory on a large
   fan-out, which detaches the caller's whole persistence context. `toResponse()` then read
   `department.getName()` on a now-detached lazy proxy and threw `LazyInitializationException`.

   This is **exactly the trap documented on `dispatchAll`'s own javadoc** after Wave D, where it was
   fixed in `JobService#updateStatus` and `CodingContestService#update` — reintroduced in a new caller
   written later. Note why it hid: the ADMIN path never showed it, because an admin's department comes
   from `departmentRepository.findById(...)` and is already fully materialized, whereas a faculty
   member's arrives as an uninitialized proxy off `Faculty#getDepartment`, and `resolveRecipients` only
   reads its **id**, which a Hibernate proxy answers without touching the database — so nothing forced
   the proxy to load before the clear. Fixed by building the response before the fan-out (the same
   pattern as the two earlier call sites) and attaching the recipient count afterwards via a new
   `AnnouncementResponse#withRecipientCount`. **A fourth caller added in a future phase will hit this
   again**; the javadoc warning alone did not prevent the third occurrence.

   Verified live against a booted instance, not just by the suite: faculty1 posting to their own
   department returns `201` with `departmentName` populated (the exact field that used to throw) and a
   real `recipientCount: 7` backed by 7 genuine `notifications` rows; the same token targeting `ALL` or
   another department gets `403`; a STUDENT gets `403`; a second faculty deleting another's announcement
   gets `403` while the creator gets `204`, cascading the notification rows away with it. All probe data
   created during this verification was deleted afterwards.

3. **The other two defects were already fixed in this working tree and are confirmed:** the
   `DevDataSeeder` boot banner now renders real credentials instead of literal `{}`, and
   `GlobalExceptionHandler` gained `MissingServletRequestParameterException` and
   `MethodArgumentTypeMismatchException` handlers so a missing or malformed required query parameter
   returns the §47 `400` envelope instead of an opaque `500`.

**Also fixed, unrelated but found while working here.** `NotificationService.java` contained a raw NUL
byte inside a string literal (a `(userId, dedupeKey)` separator written as a literal control character
rather than an escape). Git and grep classified the whole file as **binary**, so it was silently invisible
to every text search across the repository — which is how the trap in finding 2 stayed unfound in that
file for so long. Replaced with the `\0` escape: identical character, identical behaviour, file is plain
text again.

**Still open.** The visual responsive pass remains `not_exercised` — no browser automation was available
in any session that tried. Judge0 execution remains blocked per G10, with decision D1 unresolved.

**Frontend gap deliberately left, not silently dropped.** The faculty announcement capability is complete
and reachable over the API, but there is still **no faculty-facing composition page** in the React app —
`/announcements` is the read-only board for every role, and the compose/manage screen
(`AdminAnnouncementsPage`) is behind an ADMIN route. A faculty member can therefore use this capability
through the API or Swagger, but not yet through the UI. This is recorded here rather than left for
someone to discover.

---

## 5. Open Decisions

Things that need a human answer before the phase that depends on them.

| # | Decision | Needed by | Detail |
|---|---|---|---|
| **D1** | Judge0 hosting strategy | **Phase 7** | Three options, none free of tradeoffs. **(a) Hosted Judge0 via RapidAPI** — works today, config-only (`JUDGE0_URL` + `JUDGE0_API_KEY`), but the free tier is roughly 50 submissions/day: enough to build and test Phase 7, *not* enough to run a live contest per §31–32. **(b) Self-host on an amd64 Linux host** booted with `systemd.unified_cgroup_hierarchy=0` — the only no-cost path that supports contests, but requires a machine that is not this one. **(c) Accept that contest demos cannot run locally.** |
| **D2** | Is a paid RapidAPI tier acceptable if contests must be demonstrated live? | **Phase 7** | A spend decision. Follows from D1. |

---

## 6. Notes

- **Sequencing value.** Phases 1–5 alone produce a genuinely demonstrable ERP: authentication, the full academic core, attendance, marks and analytics dashboards. Phases 6–12 layer on the differentiators. The plan can be stopped or re-prioritised at any phase boundary.
- **Spring Boot 4.1.0 is very new.** Config-style APIs (particularly Spring Security 7) will be verified against current documentation and the compiler rather than assumed.
- **Credentials required from the developer:** Groq API key (Phase 6), and a Judge0 endpoint per D1 (Phase 7). Everything else runs locally in Docker with no external account.
- **Toolchain installed in Phase 1:** Temurin JDK 21.0.12 at `~/Library/Java/JavaVirtualMachines/temurin-21.jdk`, and Node v22.23.2 via nvm, symlinked into `~/.local/bin`. Both are user-local — no `sudo`, no Homebrew.
