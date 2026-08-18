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
| 2 | Authentication | ⬜ Not started | |
| 3 | Core Academic | ⬜ Not started | |
| 4 | Academic Operations | ⬜ Not started | |
| 5 | Analytics | ⬜ Not started | |
| 6 | AI | ⬜ Not started | |
| 7 | Coding | ⬜ Not started | |
| 8 | Placement | ⬜ Not started | |
| 9 | Resume | ⬜ Not started | |
| 10 | Interview | ⬜ Not started | |
| 11 | Real-Time | ⬜ Not started | |
| 12 | Finalization | ⬜ Not started | |

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
