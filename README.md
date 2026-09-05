# SmartCampus ERP

**AI-Powered Student Performance Analytics, Academic Management, Coding, Placement and Career Management System**

A full-stack college ERP that combines traditional academic administration — departments, courses, subjects, enrolment, attendance and examinations — with an AI study assistant, a sandboxed coding playground and contest platform, a placement portal with automated eligibility checking, a resume builder, interview preparation and scheduling, and real-time notifications.

Every feature is backed by a real API, a real database table and real persistence. There are no mock screens.

---

## Build Status

✅ **All twelve phases are built, and every §75 gap the final audit found has now been closed.** Phases 1–11 were each verified against real infrastructure — the backend boots against real MySQL with all eleven Flyway migrations applied, and every phase checkpoint was proven by actually performing it: driving the API over HTTP, reading OTP mail out of Mailpit, and making a real Groq call grounded in a student's actual academic record. The backend test suite is **379/379 green**, the frontend suite is **75/75 green across 15 files**, and `npm run build` is clean. Judge0 remains the one blocked component by design — the coding module is fully built against the real Judge0 API contract, but live execution cannot run on Docker Desktop (see [Judge0](#judge0) below), so that one checkpoint is deferred rather than faked; submission recording, the honest failure path, contest registration and leaderboard scoring were all live-verified and work correctly.

**The §75 audit found three problems. All three are now fixed and re-verified:**
- **✅ Fixed — "Faculty can send authorized announcements."** `POST /api/announcements` now admits FACULTY as well as ADMIN, and `AnnouncementService` enforces the narrower faculty rule per-row rather than refusing outright: a faculty member may announce to their **own department only**, and may edit or delete **only announcements they created**. Verified live — faculty1 posting to their own department returns `201` with a real fan-out to 7 recipients, while the same token targeting `ALL` or another department gets `403`, a student gets `403`, and a second faculty attempting to delete another's announcement gets `403` where the creator gets `204`.
- **✅ Fixed — the seed-data boot banner.** The credential banner now renders real values instead of literal `{}` placeholders.
- **✅ Fixed — 500 on a missing or malformed query parameter.** `GlobalExceptionHandler` now handles `MissingServletRequestParameterException` and `MethodArgumentTypeMismatchException`, so those endpoints return the clean §47 `400` envelope instead of an opaque `500`.

**One item remains not exercised**: the visual responsive pass (desktop/laptop/tablet/mobile) has never been independently re-verified with a real browser — no browser automation tool was available in the sessions that tried. Code inspection confirms real responsive infrastructure exists (Tailwind `sm:`/`lg:` breakpoints, a `lg:hidden` hamburger driving a `MobileNavDrawer`, a viewport meta tag, media queries in the built CSS), but that is not the same as an observed rendering pass and is reported as such rather than claimed.

Full detail, exact reproduction steps, and the complete item-by-item §75/§61 verification record are in `PROJECT_PLAN.md`'s Phase 12 note — that is the source of truth for what has actually been verified, not this summary.

Progress is tracked phase by phase in **[`PROJECT_PLAN.md`](PROJECT_PLAN.md)**, which also records the confirmed technical decisions and the ten clarifications (G1–G10) resolving gaps in the original scope document.

**Two guides sit alongside this README:**
- **[`docs/how-it-works.md`](docs/how-it-works.md)** — the whole system explained three ways in one document: how it is built and where to change it (developer), what each role can actually do (user), and how to satisfy yourself that any of it is real (evaluator).
- **[`docs/demo-playbook.md`](docs/demo-playbook.md)** — what the seeder leaves empty, how to populate those modules before a demonstration, and a running order that shows the genuinely unusual features to best effect.

| Phase | Module | Status |
|---|---|---|
| 1 | Foundation — toolchain, Docker services, scaffolding | ⚠️ Done, except Judge0 |
| 2 | Authentication — JWT, roles, OTP password reset | ✅ Done |
| 3 | Core Academic — departments, courses, subjects, students, faculty | ✅ Done |
| 4 | Academic Operations — attendance, exams, marks, grading | ✅ Done |
| 5 | Analytics — GPA/CGPA, performance trends, risk detection | ✅ Done |
| 6 | AI — study assistant, study plans, practice questions | ✅ Done |
| 7 | Coding — playground, problems, submissions, contests | ⚠️ Built; execution checkpoint deferred (Judge0) |
| 8 | Placement — companies, drives, eligibility, applications | ✅ Done |
| 9 | Resume — builder, templates, PDF export | ✅ Done |
| 10 | Interview — question bank, scheduling | ✅ Done |
| 11 | Real-Time — WebSocket notifications, announcements | ✅ Done |
| 12 | Finalization — Swagger, seed data, testing, deployment | ✅ Done; all three §75 audit findings fixed and re-verified (see above) |

The setup instructions below describe the current, intended workflow, including Phase 12 additions (Swagger UI, seed data, frontend tests, deployment configuration). Where a Phase 12 command could not be executed this session because it depends on another concurrently-running slice, that is marked explicitly rather than presented as verified.

Every checkpoint above was verified by actually performing it — booting the application against the real MySQL container, driving the API over HTTP, and reading OTP mail out of Mailpit — never by inspection alone. Where verification found defects, both the defect and its root cause are recorded in `PROJECT_PLAN.md` rather than quietly fixed.

---

## Features

### For Students
- Academic dashboard with attendance percentage, current marks, GPA/CGPA and performance trends
- Subject-wise attendance history and low-attendance warnings
- Marks by subject and exam type, with grade and percentage calculation
- **AI study assistant** grounded in the student's own academic record — topic explanations, personalised study plans, practice questions and MCQs
- **Coding playground** for Java and C++ with sandboxed remote execution
- Coding contests with live leaderboards
- **Placement portal** showing eligibility status with a clear reason when ineligible
- **Resume builder** with multiple versions, templates and PDF export
- Interview preparation question bank with progress tracking
- Interview schedule and real-time notifications

### For Faculty
- Dashboard covering assigned courses, subjects and student counts
- Bulk attendance marking by class roster
- Marks entry and revision for authorized subjects
- Student performance analytics with course, subject, semester, section and academic-year filters
- Low-attendance and at-risk student identification
- Announcements to authorized audiences

### For Administrators
- System-wide analytics: enrolment, attendance, performance, placement and coding statistics by department
- Department, course and subject management
- Student and faculty user provisioning
- Placement drives, companies and eligibility criteria
- Applicant shortlisting and interview scheduling
- Coding contest creation and problem authoring
- System-level announcements

---

## Architecture

```
                    Internet
                       │
              ┌────────▼────────┐
              │  React Frontend │   Vite · TypeScript · Tailwind · shadcn/ui
              └────────┬────────┘
                       │  REST (JWT)  +  WebSocket
              ┌────────▼────────┐
              │  Spring Boot    │
              │                 │
              │  Controller     │   HTTP handling, validation, DTO mapping
              │      ↓          │
              │  Service        │   All business logic lives here
              │      ↓          │
              │  Repository     │   Spring Data JPA
              │      ↓          │
              │  Entity         │   Hibernate ORM
              └────────┬────────┘
                       │
              ┌────────▼────────┐
              │     MySQL       │
              └─────────────────┘

External integrations, each behind a service abstraction:

  AIService            → GroqAIService      (OpenAI-compatible API)
  CodeExecutionService → Judge0Service      (sandboxed — see the Judge0 note)
  EmailService         → SmtpEmailService   (Mailpit in dev)
```

**Layering rule:** business logic never sits in a controller. Controllers handle HTTP; services own the rules; repositories own persistence.

**Security rule:** authorization is enforced in the backend service layer, not in the UI. A student cannot reach another student's data by changing an ID in a URL, and faculty can only modify subjects they are assigned to.

**Secrets rule:** the AI key, Judge0 key, JWT secret, SMTP password and database credentials exist only on the backend, supplied through environment variables. None of them ever reach the browser.

---

## Tech Stack

### Backend
| Component | Choice |
|---|---|
| Language | Java 21 (Temurin) |
| Framework | Spring Boot 4.1.0 |
| Build | Maven |
| Web | Spring Web, Spring WebSocket |
| Data | Spring Data JPA, Hibernate |
| Migrations | Flyway (`ddl-auto=validate`) |
| Security | Spring Security, JJWT, BCrypt |
| Validation | Jakarta Bean Validation |
| Docs | OpenAPI / Swagger UI |
| Testing | JUnit 5, Mockito, Spring Boot Test, Testcontainers |
| Utility | Lombok |

### Frontend
| Component | Choice |
|---|---|
| Framework | React + Vite |
| Language | TypeScript |
| Styling | Tailwind CSS + shadcn/ui |
| Routing | React Router |
| HTTP | Axios (JWT interceptor) |
| Charts | Chart.js / react-chartjs-2 |
| Editor | Monaco (coding playground) |
| Real-time | WebSocket client |
| Testing | Vitest, React Testing Library |

### Infrastructure
| Service | Runs in | Port |
|---|---|---|
| MySQL 8.4 | Docker | 3306 |
| Judge0 | Docker (profile-gated, see below) | 2358 |
| Mailpit (dev SMTP) | Docker | 1025 SMTP / 8025 UI |
| Backend | Host (JVM) | 8080 |
| Frontend | Host (Vite) | 5175 |

<a name="judge0"></a>
**Judge0 does not run on Docker Desktop.** Judge0 1.13.1 bundles isolate 1.8.1, which drives cgroup v1, while Docker Desktop's LinuxKit VM is cgroup-v2 only — every submission fails with `Failed to create control group`. This is not an Apple Silicon issue: amd64 emulation works fine, and an Intel Mac would fail identically. It is therefore kept behind a compose profile and does not start by default:

```bash
docker compose --profile judge0 up -d    # will start, but submissions will fail here
```

Code execution is not needed until Phase 7. Because `CodeExecutionService → Judge0Service` reads `JUDGE0_URL`, pointing it at a hosted Judge0 instance is a configuration change with no code impact. Full investigation transcript in [`docs/judge0-notes.md`](docs/judge0-notes.md).

---

## Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| JDK | 21 | Temurin recommended |
| Node.js | 22 LTS | via `nvm` |
| Docker Desktop | current | Must be running |
| Git | any recent | |

MySQL does **not** need to be installed on the host — it runs in Docker.

---

## Installation

```bash
git clone <your-repo-url>
cd SmartCampusERP
cp .env.example .env
```

Then edit `.env` and fill in the values described under [Environment Variables](#environment-variables).

### Start the supporting services

```bash
docker compose up -d
```

This brings up MySQL, Judge0 (with its Postgres and Redis dependencies) and Mailpit. Verify:

```bash
docker compose ps
curl http://localhost:2358/about     # Judge0
open http://localhost:8025           # Mailpit inbox
```

---

## Database Setup

The `smartcampus` database is created automatically by the MySQL container on first start. Schema is managed entirely by **Flyway migrations**, applied automatically when the backend boots — there is no manual SQL to run and no `ddl-auto=update`.

To reset the database completely:

```bash
docker compose down -v && docker compose up -d
```

### Development seed data (§65)

A full development dataset — admin, faculty, students, departments, courses, subjects, attendance, marks, companies, jobs, coding problems, contests and announcements — is loaded by `DevDataSeeder`, but **only when both of the following are true**:

```bash
cd backend
SPRING_PROFILES_ACTIVE=seed SMARTCAMPUS_SEED_ENABLED=true ./mvnw spring-boot:run
```

Neither switch alone does anything — the Spring profile *and* the `smartcampus.seed.enabled` property must both be set, and the seeder additionally refuses to run if `prod` appears anywhere in the active profiles. This is deliberate: seed data is real fake data (§69/§65 both apply — it must never become something a real deployment quietly depends on), so it is designed to require two independent, explicit opt-ins rather than firing on a normal boot or a partially-copied environment file.

> ⚠️ **These passwords are development-only. Never use them, or this activation path, against a production database.**

| Role | Email(s) | Password |
|---|---|---|
| Admin | `admin@smartcampus.local` | `Admin@Dev12345` |
| Faculty | `faculty1@smartcampus.local` … `faculty4@smartcampus.local` | `Faculty@Dev12345` |
| Student | `student1@smartcampus.local` … `student12@smartcampus.local` | `Student@Dev12345` |

### The first administrator in production (§64)

Production never runs the seed profile, so it needs a different, production-safe path to create the very first admin account — `AdminBootstrapRunner`, which runs on every boot (not profile-gated) but is a **logged no-op** unless both `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` are set, and even then only creates an account when **zero** `ADMIN` users currently exist:

```bash
BOOTSTRAP_ADMIN_EMAIL=admin@yourcollege.edu \
BOOTSTRAP_ADMIN_PASSWORD="a real, strong, unique password" \
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

It is safe to leave both variables set across restarts — after the first admin exists, every subsequent boot sees a non-zero admin count and does nothing. This replaces the old "register a student, then flip their role in MySQL by hand" instruction entirely; that manual step is no longer how this project provisions its first admin.

---

## Backend Setup

```bash
cd backend
./mvnw clean install
./mvnw spring-boot:run
```

The API becomes available at `http://localhost:8080`. Health check:

```bash
curl http://localhost:8080/actuator/health
```

---

## Frontend Setup

```bash
cd frontend
npm install
npm run dev
```

The app becomes available at `http://localhost:5175`. The port is pinned in `vite.config.ts` with `strictPort`, because 5173 and 5174 are used by other local stacks and the backend's CORS allowlist is keyed to this exact origin.

---

## Environment Variables

All secrets are supplied through environment variables. `.env.example` ships with placeholders; **never commit `.env`**.

### Database
| Variable | Description |
|---|---|
| `DB_URL` | JDBC URL, e.g. `jdbc:mysql://localhost:3306/smartcampus` |
| `DB_USERNAME` | Database user |
| `DB_PASSWORD` | Database password |

### Authentication
| Variable | Description |
|---|---|
| `JWT_SECRET` | Signing secret. Use a long random value; never reuse across environments. |
| `JWT_EXPIRATION` | Token lifetime in milliseconds |

### AI
| Variable | Description |
|---|---|
| `AI_PROVIDER` | `groq` (default) |
| `AI_API_KEY` | Provider API key |
| `AI_BASE_URL` | `https://api.groq.com/openai/v1` |
| `AI_MODEL` | Model identifier |

Groq exposes an OpenAI-compatible API, so switching to OpenAI or another compatible provider is a change to `AI_BASE_URL`, `AI_MODEL` and `AI_API_KEY` — no code changes.

### Email
| Variable | Description |
|---|---|
| `SMTP_HOST` | `localhost` in dev (Mailpit) |
| `SMTP_PORT` | `1025` in dev |
| `SMTP_USERNAME` | Blank in dev |
| `SMTP_PASSWORD` | Blank in dev |

### Code Execution
| Variable | Description |
|---|---|
| `JUDGE0_URL` | `http://localhost:2358` for the self-hosted instance |
| `JUDGE0_API_KEY` | Only required for hosted Judge0 |

### API Documentation & Rate Limiting (§61, §63)
| Variable | Description |
|---|---|
| `SWAGGER_ENABLED` | `true` in dev; set `false` to disable Swagger UI / `/v3/api-docs` (production default — see [Deployment](#deployment)) |
| `AUTH_RATE_LIMIT_PER_MINUTE` | Requests allowed per caller per window on sensitive auth endpoints (default `20`) |
| `AUTH_RATE_LIMIT_WINDOW_SECONDS` | Length of that window in seconds (default `60`) |

### Seed Data & Admin Bootstrap (§64, §65)
| Variable | Description |
|---|---|
| `SMARTCAMPUS_SEED_ENABLED` | Must be `true` **together with** `SPRING_PROFILES_ACTIVE=seed` to load development seed data; either alone is a no-op |
| `BOOTSTRAP_ADMIN_EMAIL` | Production-safe first-admin email; blank = no-op |
| `BOOTSTRAP_ADMIN_PASSWORD` | First-admin password; blank = no-op. Creates one `ADMIN` only when zero exist |

### Production Profile (§71)
| Variable | Description |
|---|---|
| `SPRING_PROFILES_ACTIVE` | Set to `prod` to activate `application-prod.properties` (Swagger off by default, SQL logging off, actuator limited to health, Flyway out-of-order off) — see [Deployment](#deployment) |

---

## API Documentation

Interactive OpenAPI documentation, with JWT-authenticated live API testing (§63):

| Surface | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Raw OpenAPI JSON | http://localhost:8080/v3/api-docs |

Endpoints are organized into 13 documentation groups, matching §63 exactly: **Authentication, Student APIs, Faculty APIs, Admin APIs, Attendance, Marks, Analytics, AI, Coding, Placement, Resume, Interview, Notifications.** Pick a group from the dropdown at the top of the Swagger UI page to see just that module's endpoints.

**Calling an authenticated endpoint through Swagger UI, step by step:**

1. Open http://localhost:8080/swagger-ui.html.
2. Expand **Authentication**, find `POST /api/auth/login`, click **Try it out**, and submit a real account's email/password (a seeded one works — see [Database Setup](#database-setup)).
3. Copy the `token` field out of the response body.
4. Click the **Authorize** button (top right, padlock icon). In the `bearerAuth` field, paste the token — just the raw JWT, Swagger UI adds the `Bearer ` prefix itself.
5. Click **Authorize**, then **Close**. Every subsequent "Try it out" call across every group now carries that token in its `Authorization` header, so you can exercise any endpoint the account's role is allowed to reach.

Set `SWAGGER_ENABLED=false` to turn this off entirely (recommended once a deployment's API surface shouldn't be publicly browsable — see [Deployment](#deployment)).

Every endpoint documented in Swagger UI is also summarized here for quick reference:

```
/api/auth          register, login, me, password-reset (request / verify / reset)
/api/users         admin-only account provisioning
/api/departments   /api/courses      /api/subjects
/api/students      /api/faculty      /api/enrollments
/api/faculty-subject-assignments
/api/attendance    /api/exams        /api/marks        /api/grade-bands
/api/analytics     /api/performance-bands
/api/ai            conversations, explanations, study plans, practice questions
/api/problems      /api/coding       /api/contests     /api/leaderboard
/api/companies     /api/jobs         /api/applications
/api/resumes       /api/interviews
/api/notifications /api/announcements     (plus the JWT-authenticated WebSocket)
```

Writes to reference data (departments, courses, subjects) and all enrolment and assignment management are `ADMIN`-only; reads are open to any authenticated role. Students may read and update only their own profile — requesting another student's record returns `404` rather than `403`, so an ID cannot be probed to distinguish "not yours" from "does not exist".

Errors use a consistent envelope, and stack traces are never returned to clients:

```json
{
  "timestamp": "2026-08-18T10:15:30Z",
  "status": 404,
  "error": "NOT_FOUND",
  "message": "Student not found",
  "path": "/api/students/10"
}
```

Large collections are paginated, returning `content`, `page`, `size`, `totalElements` and `totalPages`. Search and filtering are performed server-side.

---

## Authentication

Stateless JWT authentication with three roles: `STUDENT`, `FACULTY`, `ADMIN`.

```
Login → verify email → verify BCrypt password → issue JWT → return token + user + role
```

Protected requests carry the token:

```
Authorization: Bearer <JWT>
```

Each request passes through a JWT filter that extracts and validates the token, loads the user, resolves the role and populates the security context. Invalid or expired tokens produce an authentication error — they are never silently accepted.

**Account provisioning.** Self-registration is limited to students; it creates a pending profile that an administrator activates and links to a department, course and register number. Faculty and administrator accounts are provisioned by an existing administrator. This closes the role-escalation hole that an open, role-accepting registration endpoint would otherwise leave. (See clarification G1 in `PROJECT_PLAN.md`.)

**Password reset** is OTP-based over email. OTPs are stored hashed, expire on a configurable timer, are single-use, cap verification attempts, and respond identically whether or not the account exists, so the endpoint cannot be used to enumerate accounts. In development the mail is delivered to Mailpit and can be read at http://localhost:8025.

---

## Running the Application

With Docker services already up:

```bash
# Terminal 1 — backend (JWT_SECRET is required; there is no default, by design)
cd backend && JWT_SECRET="<a strong local secret>" ./mvnw spring-boot:run

# Terminal 2 — frontend
cd frontend && npm run dev
```

| Surface | URL |
|---|---|
| Application | http://localhost:5175 |
| API | http://localhost:8080 |
| Mailpit inbox | http://localhost:8025 |
| Swagger UI | http://localhost:8080/swagger-ui.html |

**Creating the first administrator.** Self-registration only ever creates a student, and provisioning staff accounts requires an existing admin, so *something* has to create the first one. There is no more hand-editing the database for this: use either the [development seed data](#database-setup) (`SPRING_PROFILES_ACTIVE=seed SMARTCAMPUS_SEED_ENABLED=true`, gives you `admin@smartcampus.local`) for local work, or `BOOTSTRAP_ADMIN_EMAIL`/`BOOTSTRAP_ADMIN_PASSWORD` (see [Database Setup](#database-setup)) for a real deployment — both are documented in full there.

---

## Testing

### Backend
```bash
cd backend
./mvnw test                  # 299+ green (see PROJECT_PLAN.md's Phase Tracker for the exact, currently-verified count)
./mvnw verify                # full suite including integration tests
```

Repository and integration tests run against a real MySQL instance via Testcontainers rather than an in-memory substitute, so migrations and SQL dialect behaviour are genuinely exercised. No environment variables are required — `src/test/resources/application.properties` supplies a throwaway JWT signing key so a fresh clone can run the suite immediately.

Coverage spans every phase: registration, login, duplicate email, non-enumerating invalid credentials, JWT validation including tampered and expired tokens, role denial against real admin-only endpoints, OTP reset round trip with attempt-cap enforcement, cross-student/cross-faculty access attempts and privilege-escalation sweeps, SQL-level pagination, attendance and grading math (including boundary cases), placement eligibility rules and application concurrency, coding verdict aggregation and contest scoring, interview-scheduling conflict detection under concurrent load, and real-time notification fan-out and per-user WebSocket isolation. Phase 12 adds a dedicated §61 security-verification suite (BCrypt, JWT, authorization, input validation, SQL-injection protection, CORS, secure headers, rate limiting, OTP expiry, login error handling, no-password-in-response, no-secrets-in-git) as its own test package, written by an agent other than the one implementing the corresponding security code, specifically so a security control isn't graded by the same hand that built it.

### Frontend
```bash
cd frontend
npm test                     # vitest run — full suite, once
npm run test:watch           # vitest — watch mode
npm run test:coverage        # vitest run --coverage
npm run build                # tsc -b + vite build
npm run lint                 # oxlint
```

Phase 12 adds the frontend test runner (Vitest + React Testing Library) and covers the authentication flow (login/logout, token persistence, protected-route redirects), API integration (requests, error envelopes, auth header injection) and form validation, per §64's frontend testing requirement. HTTP is exercised through direct module mocking (`vi.mock`) and, for the axios interceptor tests specifically, a hand-written fake `axios` adapter driving the real interceptor logic end to end — not MSW, which was evaluated and deliberately dropped since every backend call already goes through one shared axios instance already fully under test control. See `PROJECT_PLAN.md`'s Phase 12 note for the exact test count as verified against a real run — this README does not restate a number that could drift out of sync with it.

---

## Deployment

Full detail, including a real "what must change for production" checklist (not platitudes), is in **[`docs/deployment.md`](docs/deployment.md)**. Short version:

```
                    Internet
                       │  HTTPS (terminated in front of this stack)
              ┌────────▼────────┐
              │  React Frontend │   nginx: static bundle + SPA fallback +
              └────────┬────────┘   reverse proxy for /api and /ws
                       │
              ┌────────▼────────┐
              │  Spring Boot    │
              └────────┬────────┘
         ┌──────────────┼──────────────┬───────────────┐
         ▼              ▼              ▼               ▼
     ┌───────┐    ┌───────────┐  ┌───────────┐   ┌─────────────┐
     │ MySQL │    │  AI API   │  │  Judge0   │   │    SMTP     │
     └───────┘    └───────────┘  └───────────┘   └─────────────┘
```

### Containers

`backend/Dockerfile` and `frontend/Dockerfile` are both multi-stage builds (Maven → JRE 21 for the backend, Node → nginx for the frontend), verified in this repository by actually running `docker build` for both — see `docs/deployment.md` §6 for the exact commands and result. `docker-compose.prod.yml` wires frontend + backend + MySQL together for a single-host deployment, with every credential read from the environment and nothing committed.

```bash
cp .env.example .env    # fill in REAL production values first — see the checklist below
docker compose -f docker-compose.prod.yml up -d --build
```

### What must change for production — the real list

| Dev default | Production requirement |
|---|---|
| `CORS_ALLOWED_ORIGINS=http://localhost:5175,http://localhost:5173,http://localhost:5174` | Narrow to the deployed frontend's real origin only. **This is the single most dangerous default in the config** — left as-is, any site can drive authenticated requests against a live API using a stolen token. |
| `JWT_SECRET=` (empty) | Generate a real one: `openssl rand -base64 48`. The empty default is deliberate — the app fails to boot rather than signing tokens with a guessable key. That failure is a feature, not a bug to route around. |
| `SWAGGER_ENABLED` unset (defaults true) | Set `false` if the API surface shouldn't be public. `application-prod.properties` already flips the default to false under `SPRING_PROFILES_ACTIVE=prod`. |
| `FLYWAY_OUT_OF_ORDER=true` | Set `false` against a fresh database. It is `true` here only because parallel build waves applied this repo's dev-database migrations out of numeric order — a real production database gets V1..V12 in order and should fail loudly on anything else. |
| `SMTP_HOST=localhost` (Mailpit) | Real SMTP provider credentials. |
| `JUDGE0_URL=http://localhost:2358` (non-functional here) | A Judge0 instance actually reachable from the backend container — see the honesty note below. |
| No TLS in this repo | HTTPS termination in front of the stack. Without it, the `Strict-Transport-Security` header never actually engages. |
| `DB_USERNAME=smartcampus` / `DB_PASSWORD=smartcampus` | Credentials unique to the deployment — `docker-compose.prod.yml` has no default and refuses to start without them. |
| (no profile) | `SPRING_PROFILES_ACTIVE=prod`, and the `seed` profile **never** included. |

**Judge0 — still unresolved (honesty note).** The coding module is fully built against the real Judge0 API contract, but live code execution cannot run on this development machine: Docker Desktop's LinuxKit VM is cgroup-v2-only, and Judge0 1.13.1's bundled `isolate` sandbox requires cgroup v1, so every submission returns status 13 Internal Error (measured, not assumed — see `docs/judge0-notes.md`). `docker-compose.prod.yml` does not self-host Judge0 for this reason; a real deployment needs `JUDGE0_URL` pointed at either a hosted instance (e.g. RapidAPI) or a genuinely cgroup-v1 host. This is not solved by any file in this phase — see `docs/deployment.md` §4 for the full explanation.

**File upload validation (§61) — not applicable.** This system has no file upload capability anywhere: zero `MultipartFile` references, no multipart config, no `profileImage` column on `Student`, no `logo` column on `Company`. The one file-shaped feature that exists is a server-generated resume PDF *download* (`GET /api/resumes/{id}/pdf`) — nothing is ever uploaded into the system. See `docs/deployment.md` §3.

---

## Folder Structure

```
SmartCampusERP/
├── backend/
│   ├── Dockerfile                # multi-stage: Maven build → JRE 21 runtime
│   └── src/
│       ├── main/
│       │   ├── java/smartcampus/
│       │   │   ├── config/          # app, CORS, WebSocket, OpenAPI config
│       │   │   ├── controller/      # REST endpoints
│       │   │   ├── dto/             # request and response DTOs
│       │   │   ├── entity/          # JPA entities
│       │   │   ├── exception/       # custom exceptions, global handler
│       │   │   ├── realtime/        # WebSocket handling (Phase 11)
│       │   │   ├── repository/      # Spring Data repositories
│       │   │   ├── security/        # JWT filter, security config
│       │   │   ├── seed/            # DevDataSeeder, AdminBootstrapRunner (Phase 12)
│       │   │   ├── service/         # business logic
│       │   │   └── util/
│       │   └── resources/
│       │       ├── application.properties
│       │       ├── application-prod.properties   # production overrides (Phase 12)
│       │       └── db/migration/    # Flyway migrations, V1–V11 (V12 unclaimed)
│       └── test/
├── frontend/
│   ├── Dockerfile                # multi-stage: Node build → nginx runtime
│   ├── nginx.conf                # SPA fallback + /api, /ws reverse proxy
│   ├── .env.example
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── layouts/
│   │   ├── services/            # API clients
│   │   ├── hooks/
│   │   ├── context/             # auth and app state
│   │   ├── routes/              # protected and role-based routing
│   │   ├── lib/
│   │   ├── types/
│   │   ├── utils/
│   │   ├── assets/
│   │   ├── test/                # Vitest setup (jest-dom, RTL cleanup) (Phase 12)
│   │   └── App.tsx
│   ├── package.json
│   └── vite.config.ts
├── docs/
│   ├── SmartCampus-ERP-Scope.pdf
│   ├── scope-extracted.md
│   ├── how-it-works.md          # the system explained for developers, users and evaluators
│   ├── demo-playbook.md         # populating data and running a live demonstration
│   ├── judge0-notes.md
│   └── deployment.md            # detailed production deployment guide (Phase 12)
├── docker-compose.yml           # dev supporting services: MySQL + Mailpit (+ Judge0, gated)
├── docker-compose.prod.yml      # frontend + backend + MySQL containers (Phase 12)
├── PROJECT_PLAN.md
├── README.md
├── .env.example
└── .gitignore
```

JPA entities are never exposed directly from the API — every endpoint speaks in DTOs, and passwords never appear in a response.

---

## Screenshots

Not captured yet. No image files exist in this repository to link to — this section will be filled in with real captures rather than placeholder links.

---

## Future Enhancements

- Additional languages in the coding playground beyond Java and C++
- Timetable and class scheduling, enabling period-level "upcoming classes"
- Assignment submission and grading as a first-class module
- Mobile application
- Bulk import of students and marks from spreadsheets
- Parent and guardian portal
- Alumni network and referral tracking
- Automated attendance via biometric or RFID integration

---

## Author

**Shaffan Ahmed**

---

## License

Not yet specified.
