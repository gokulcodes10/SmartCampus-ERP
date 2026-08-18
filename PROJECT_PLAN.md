# SmartCampus ERP — Build Plan

**Status:** Planning complete. Phase 1 not started.
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
| Judge0 (+ Postgres, Redis) | Docker, **self-hosted** | Code execution. Self-hosted rather than RapidAPI — no rate limits during contests, works offline. |
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
| 1 | Foundation | ⬜ Not started | |
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

---

## 5. Notes

- **Sequencing value.** Phases 1–5 alone produce a genuinely demonstrable ERP: authentication, the full academic core, attendance, marks and analytics dashboards. Phases 6–12 layer on the differentiators. The plan can be stopped or re-prioritised at any phase boundary.
- **Spring Boot 4.1.0 is very new.** Config-style APIs (particularly Spring Security 7) will be verified against current documentation and the compiler rather than assumed.
- **Credentials required from the developer:** Groq API key (Phase 6). Everything else runs locally in Docker with no external account.
