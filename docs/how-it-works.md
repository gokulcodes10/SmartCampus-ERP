# How SmartCampus ERP Works

One document, three readings of the same system.

- **[Part 1 — Developer's view](#part-1--developers-view)**: how it is built, why it is built that way, and where to change things.
- **[Part 2 — User's view](#part-2--users-view)**: what a student, a faculty member and an administrator can actually do.
- **[Part 3 — Evaluator's view](#part-3--evaluators-view)**: how to satisfy yourself that any of it is real.

Companion documents: [`README.md`](../README.md) for setup, [`PROJECT_PLAN.md`](../PROJECT_PLAN.md) for the phase-by-phase verification record, [`demo-playbook.md`](demo-playbook.md) for running a live demonstration.

---

# Part 1 — Developer's view

## 1.1 The shape of the thing

Two deployable pieces and a database.

```
React + TypeScript (Vite)          Spring Boot 4.1 (Java 21)          MySQL 8.4
  browser, port 5175      ──HTTP──▶   REST API, port 8080     ──JDBC──▶  schema: smartcampus
        │                                     │                              ▲
        └──────── WebSocket /ws/notifications ┘                              │
                                              │                         Flyway V1..V11
                                     Groq (AI), Judge0 (code),
                                     SMTP/Mailpit (mail)
```

The backend owns every rule. The frontend renders what the backend returns and never decides who may see what. That is deliberate: a role check that exists only in React is a suggestion, not a control, because the API is reachable without React.

## 1.2 Backend layering

Requests flow through fixed layers, and each layer has one job:

| Layer | Package | Responsibility |
|---|---|---|
| Controller | `smartcampus.controller` | HTTP shape only. Bind and validate the request, call one service, return a DTO. |
| Service | `smartcampus.service` | All business rules, all authorization decisions, all transactions. |
| Repository | `smartcampus.repository` | Spring Data JPA queries. Parameterized, never string-concatenated. |
| Entity | `smartcampus.entity` | The JPA mapping, validated at boot against the migrated schema. |
| DTO | `smartcampus.dto` | Request and response records. Flat, never nested entities. |
| Security | `smartcampus.security` | JWT filter, WebSocket handshake authentication, auth rate limiting. |

There are 34 controllers, roughly 65 services and about 80 entity and enum types. Every controller has a service behind it, and no controller talks to a repository directly.

**DTOs are flat, always.** A course response carries `departmentId` and `departmentName` as scalars rather than a nested department object. This is not a style preference. It was a real defect: the frontend was written against a designed contract with nested objects while the backend returned flat ones, `npm run build` passed anyway because TypeScript only checked the frontend's own wrong types, and every affected admin page would have crashed at runtime. Flat and denormalized is now the convention. Match it.

**Entities are never serialized to the wire.** A response DTO is always constructed explicitly. That is what keeps password hashes out of responses structurally rather than by remembering to exclude them.

## 1.3 Authentication and authorization

Stateless JWT. `AuthController` issues a token on login, `JwtAuthenticationFilter` validates it on every request and populates the security context, and there is no server session to hijack. Passwords are BCrypt hashed. The signing secret has **no default** — the app refuses to boot without `JWT_SECRET` rather than starting with a guessable key.

Authorization happens at three depths, and you need all three:

1. **Route rules** in `SecurityConfig` — coarse, role-level. "Only an admin may POST a department."
2. **Service-layer ownership checks** — "this student may read this record because it is theirs." Cross-student reads return `404`, not `403`, so an attacker cannot use the status code to discover which record ids exist.
3. **`AcademicAccessGuard`, reached through `ScopedWriteAuthorizer`** — the assignment-scoped check. A faculty member may write attendance or marks only for the exact `(subject, academic year, semester, section)` tuple they are assigned to teach, per the `FacultySubjectAssignment` table.

That third one is the interesting one. The original scope made the entire faculty permission model depend on faculty-to-subject assignment while never defining such an entity. It is clarification **G2** in the plan, and every faculty write routes through it.

**When you add a faculty-writable endpoint, route it through `ScopedWriteAuthorizer`.** A role check alone is not enough. `hasRole("FACULTY")` proves someone is a teacher, not that they teach *this* class.

## 1.4 Schema management

Flyway migrations `V1` through `V11`, with `spring.jpa.hibernate.ddl-auto=validate`. Hibernate never alters the schema; it only checks at boot that its mapping matches what the migrations produced, and refuses to start on drift. Migrations were applied out of order during parallel development, which `spring.flyway.out-of-order=true` permits.

Constraints live in the database, not only in Java. Grades, attendance percentages, application uniqueness, salary ranges and announcement targeting are all enforced by real `CHECK` and `UNIQUE` constraints. The Java layer validates the same rules first so callers get a clean `400` instead of a constraint violation surfacing as a `500`, but the database is the backstop that holds even under concurrency. The duplicate-application guard was proven against a real multi-threaded race, not merely asserted.

## 1.5 Traps that have already bitten, in this codebase

These are documented because each one was found by a failing test or a live probe after someone believed the feature was done.

**The fan-out detaches your entities.** `NotificationService.dispatchAll` calls `entityManager.clear()` to bound memory on a large fan-out. That clears the *entire* persistence context of your transaction, not just its own rows. Any lazy association you read afterwards throws `LazyInitializationException`. **Build your response DTO before you dispatch notifications, not after.** This has now bitten three separate call sites: `JobService`, `CodingContestService`, and most recently `AnnouncementService.create`, where it only manifested on the faculty path because an admin's department arrives fully loaded from `findById` while a faculty member's arrives as an uninitialized proxy whose id can be read without ever touching the database.

**A transaction rollback silently undoes your counter.** The OTP attempt cap incremented a counter and then threw an unchecked exception from the same `@Transactional` method, so Spring's default rollback rule discarded the increment. The cap never engaged and five wrong guesses followed by the right one still succeeded. If you increment a counter and then throw, you need `noRollbackFor`.

**A lock failure is not a constraint violation.** Interview scheduling caught `DataIntegrityViolationException` around its insert but not `PessimisticLockingFailureException` around the lock itself, so under real concurrent load MySQL picking a deadlock victim produced raw `500`s instead of a clean `409`.

**Green tests are not a working screen.** Both the student search box and the deactivate toggle in the admin UI were silently non-functional while every test passed, because the frontend sent a parameter name the backend did not read, and a field the backend deliberately ignored. Verify over real HTTP against the real backend, not against your own type declarations.

## 1.6 The external integrations

Each is behind an interface so the provider is a configuration change rather than a code change.

- **AI** — `AIService` with a `GroqAIService` implementation against Groq's OpenAI-compatible chat completions API, pinned to `openai/gpt-oss-120b`. The key is backend-only and never reaches the browser. Two hard-won details: most models on the account cannot chat at all (speech-to-text and classifier models are in the same list, so "pick the first available id" breaks everything), and because this is a reasoning model whose reasoning tokens bill against `max_tokens`, a stingy budget returns HTTP 200 with empty content. Empty content is treated as an honest failure, never stored as an answer.
- **Code execution** — `CodeExecutionService` with a `Judge0Service` implementation. The application server never executes submitted code itself. Judge0 cannot run on Docker Desktop because its bundled sandbox needs cgroup v1 while Docker Desktop's VM is cgroup v2 only. This is clarification **G10**, and it is an environment limitation rather than a code defect. Submissions record an honest `INTERNAL_ERROR` with the real connection message; the database will not store an `ACCEPTED` verdict that was not earned.
- **Mail** — `EmailService` with an SMTP implementation, delivering to Mailpit in development so OTP mail is readable without a real mailbox.

## 1.7 Real-time

Raw Spring WebSocket at `/ws/notifications`. A browser cannot set an `Authorization` header on a WebSocket upgrade, so the socket is authenticated by `JwtHandshakeInterceptor` at handshake time rather than by the servlet filter chain, and `/ws/**` is `permitAll()` in the filter chain for exactly that reason. The server binds identity at handshake and there is no client-supplied subscribe destination, so a client cannot ask for another user's stream. Hostile frames naming another user's id are covered by tests.

## 1.8 Frontend structure

`components/ pages/ layouts/ services/ hooks/ context/ utils/ routes/ types/`. Axios carries the JWT through an interceptor and redirects to login on a `401`. `useServerTable` is the shared hook behind every paginated admin table, so pagination, search and filtering are genuinely server-side — a real `LIMIT` and a separate `COUNT`, confirmed by reading Hibernate's generated SQL rather than by trusting the envelope shape.

The dev server port is **pinned to 5175** with `strictPort` in `vite.config.ts`, because the backend's CORS allowlist is keyed to that exact origin. Letting Vite drift to another port produces a CORS failure that looks convincingly like a broken backend.

## 1.9 Testing

379 backend tests and 75 frontend tests. Repository and integration tests run against real MySQL through Testcontainers rather than an in-memory substitute, so migrations and MySQL dialect behaviour are genuinely exercised. The suite needs no environment variables — a throwaway signing key lives in the test resources so a fresh clone can run it immediately.

```bash
cd backend  && ./mvnw test      # 379 tests
cd frontend && npm test         # 75 tests
```

---

# Part 2 — User's view

Three roles, three different applications behind one login.

## 2.1 Getting in

Self-registration always creates a **student**, and it creates a *pending* one. That is clarification **G1**, and it exists because the original specification exposed an open registration endpoint that accepted a role field, which would have let anyone register as an administrator. Faculty and admin accounts are provisioned by an existing admin instead.

A new student registers, lands in a pending state, and an administrator activates the account and assigns department, course and register number. Only then does the account become fully functional.

Forgot your password? Request a reset, receive a six-digit code by email, verify it, set a new password. The code expires, is single-use, and the number of wrong guesses is capped. The system's replies never reveal whether an email address exists.

## 2.2 As a student

**Your academics.** Attendance by subject with a real percentage, marks by exam with grades, and a credit-weighted GPA and CGPA that do not bleed across semesters or academic years. When you have no marks yet, the system says so rather than showing a fabricated zero. Cancelled classes are excluded from your attendance denominator, so a class the college cancelled cannot damage your percentage.

**Your analytics.** Performance trends, weak subjects, and a classification of Excellent, Good, Average or At Risk. The thresholds are configuration an administrator controls, not numbers baked into the code. If there is not enough data to classify you, the category is empty rather than defaulted to At Risk.

**Your AI assistant.** A chat that already knows your actual record. It reads your real marks, your real attendance, your genuinely weak subjects and your real upcoming exams before answering, so asking "what should I focus on" produces advice about your specific subjects with your specific numbers rather than generic study tips. Conversations persist, can be renamed and continued, and are private to you. It can also generate study plans that you can then edit, explain topics, and produce practice questions and revision schedules.

**Coding.** A Monaco-based playground for Java and C++, a problem catalogue with sample and hidden test cases, submission history, contests with penalty-based scoring, and both per-contest and global leaderboards. On this machine code execution is unavailable, so a submission records honestly that the execution service could not be reached instead of inventing a verdict.

**Placement.** Browse drives, and see immediately whether you are eligible. If you are not, you are told exactly why — the CGPA, percentage, department or graduation year rule you miss — rather than being silently blocked. Apply once per drive; a second attempt is refused, and that guarantee holds under concurrency because the database enforces it.

**Your resume.** Build one from prefilled profile data, with education, projects, experience, certifications, skills and achievements. Keep multiple versions, choose among three genuinely different templates, preview it, download a real PDF, and attach that PDF to a placement application.

**Interview preparation.** A question bank across technical, HR, behavioural, coding, aptitude and company-specific categories, with answers and explanations, bookmarks, completion tracking and progress. You can generate fresh practice questions with AI. You can schedule interviews, and the system refuses to double-book you — two overlapping interviews for the same student are rejected even when the requests race each other.

**Notifications.** A bell with an unread count and a notification centre. Announcements, placement updates, interview changes, contest and leaderboard movements and attendance warnings arrive live over a WebSocket, appearing without a page refresh.

## 2.3 As a faculty member

You see the classes you actually teach, and only those. The roster you can mark, the marks you can enter and the cohort analytics you can read are all scoped to your assignments. Attempting to act on a subject or section you are not assigned to is refused.

**Attendance.** Mark a whole roster at once, at period-level granularity, including cancelling a class so it leaves everyone's percentage untouched.

**Exams and marks.** Schedule exams with a maximum mark, then enter marks in bulk. Marks below zero or above the maximum are rejected. Grades and grade points follow the administrator's configured bands, so a change to the grading scale re-grades consistently.

**Analytics.** Class and cohort views filtered by course, subject, semester, section and academic year, with real aggregations rather than sampled estimates.

**Announcements.** You may announce to **your own department**, and you may edit or delete only the announcements you created. Broader audiences remain an administrator's decision. Note that this capability currently works through the API and Swagger; a faculty-facing composition screen in the web UI is not yet built.

## 2.4 As an administrator

Provision faculty and admin accounts. Activate pending students. Manage departments, courses, subjects, enrolments and faculty-subject assignments, each with server-side search, filtering and pagination.

Configure the grading scale and the performance thresholds rather than accepting hard-coded ones. The seeded ten-point scale runs from O at 91 percent down to U, and the performance bands require both a marks and an attendance floor.

Run placements end to end: companies, drives with eligibility criteria, applicant lists, shortlisting, status transitions and placement analytics. Run the coding side: problems with hidden test cases, and contests. Manage the interview question bank and scheduling. Announce to everyone, to students, to faculty or to a single department, with priority and expiry, and watch the real recipient count come back.

Institution-wide analytics show genuine counts drawn from the database.

---

# Part 3 — Evaluator's view

The claim this project makes is narrow and checkable: **every feature is backed by a real API, a real table and real persistence, and nothing that could not be verified is presented as verified.** Here is how to test that claim rather than take it.

## 3.1 The five-minute version

Start the stack per the README, then:

1. **Log in as three roles.** `admin@smartcampus.local`, `faculty1@smartcampus.local`, `student1@smartcampus.local` with the passwords in the README. Each lands on a different dashboard with different navigation.
2. **Pick a number off the student dashboard and chase it into the database.** Attendance percentage, CGPA, any of them. Query the underlying rows in MySQL and recompute by hand. They match, because the figure is an aggregation rather than a constant.
3. **Try to read another student's record.** Log in as `student1`, take `student2`'s id, and request it directly. You get `404`. Not `403` — the status deliberately does not confirm the record exists.
4. **Ask the AI assistant what to focus on.** The answer cites the student's actual subjects, actual marks and actual attendance figures, which you can verify against the marks page.
5. **Submit a coding solution.** It records a real submission with an honest failure status, because code execution is genuinely unavailable here. Nothing pretends to have run.

## 3.2 What is genuinely unusual here

Most student ERP projects are CRUD with a dashboard. The parts that are not ordinary:

- **AI grounded in the student's own record.** Not a chatbot bolted onto the side. The prompt builder reuses the very same services that render the attendance and marks pages, so the assistant's numbers cannot drift from what the student sees. The exact payload sent to the provider was captured during verification and contained the student's real values verbatim.
- **Assignment-scoped faculty authorization.** Permission is not "is this person a teacher" but "does this person teach this exact subject, year, semester and section." That entity did not exist in the original specification and had to be designed.
- **An eligibility engine that explains itself.** A student who cannot apply is told which specific rule they fail.
- **Configuration where most projects hard-code.** Grade bands, grade points, performance thresholds and attendance minimums are all administrator-editable rows. Editing a grade band re-grades already-computed results.
- **Honest failure as a design rule.** No fabricated verdicts, no defaulted classifications, no placeholder dashboard numbers. A student with no marks shows an empty percentage, not a zero.
- **Real-time that actually arrives.** An announcement appears in an already-open notification centre without a refresh, verified over a live socket rather than inferred from a test name.

## 3.3 Verifying the tests rather than trusting them

```bash
cd backend  && ./mvnw test     # expect 379 passing
cd frontend && npm test        # expect 75 passing
cd frontend && npm run build   # expect a clean build
```

The backend suite is worth a closer look than the number. Integration tests run against real MySQL through Testcontainers, so the migrations and MySQL's actual constraint behaviour are exercised rather than an in-memory approximation. The concurrency tests are real races: twelve threads competing to book the same interview slot, and concurrent duplicate placement applications.

## 3.4 What is honestly not finished

This is the part worth reading, because a project that reports nothing outstanding is usually a project that did not look.

| Item | State | Why |
|---|---|---|
| Code execution | Blocked | The sandbox needs cgroup v1; Docker Desktop provides only v2. Everything around execution works; execution itself needs a hosted endpoint or a Linux host. A hosting decision is open and recorded. |
| Responsive rendering pass | Not exercised | The responsive code is present and inspectable, but no session had a browser automation tool available, so no one has *observed* it render at each breakpoint. Reported as unverified rather than claimed. |
| Faculty announcement UI | Not built | The capability works through the API. The composition screen for faculty in the web app does not exist yet. |
| File upload validation | Not applicable | The application has no file upload of any kind, confirmed by scanning for multipart handling. Noted as a scope gap rather than ticked off. |

Each of these is recorded in `PROJECT_PLAN.md` with its reasoning. The project's verification record also documents the defects found *after* build agents reported success, including the security-relevant ones, rather than quietly fixing them.

## 3.5 Security, checkable in minutes

| Claim | How to check |
|---|---|
| Passwords are hashed | Read `users.password` directly in MySQL. You will see a BCrypt hash. |
| Tokens are validated | Alter one character of a JWT and call any protected endpoint. `401`. |
| Roles are enforced server-side | Call an admin route with a student token. `403`. Bypassing the UI does not help. |
| SQL injection is not possible | Send `' OR '1'='1` through a search parameter. Safe result, no leak, row counts unchanged. |
| CORS is restrictive | Send an `Origin` header from an unlisted origin. No allow-origin header comes back. |
| No secrets in the bundle | Grep the built frontend for key-shaped strings. None. |
| No secrets in git | The environment file is ignored and has never been committed. |
| Login is rate limited | Repeat failed logins. The responses turn into `429`. |
| Passwords never leave | Grep any response body for a password field. Absent. |
