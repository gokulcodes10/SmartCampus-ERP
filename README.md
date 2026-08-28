# SmartCampus ERP

**AI-Powered Student Performance Analytics, Academic Management, Coding, Placement and Career Management System**

A full-stack college ERP that combines traditional academic administration — departments, courses, subjects, enrolment, attendance and examinations — with an AI study assistant, a sandboxed coding playground and contest platform, a placement portal with automated eligibility checking, a resume builder, interview preparation and scheduling, and real-time notifications.

Every feature is backed by a real API, a real database table and real persistence. There are no mock screens.

---

## Build Status

🚧 **In development.** Phases 1–3 are complete and verified against real infrastructure: the backend boots against real MySQL with all three Flyway migrations applied, authentication works end to end (registration, JWT login, role-based access and OTP password reset delivered through a real SMTP sink), and the academic core — departments, courses, subjects, students, faculty, enrolments and faculty–subject assignments — is administrable through real admin screens with server-side search, filtering and pagination. The backend test suite is **31/31 green**. Judge0 remains the one blocked component — it cannot run on Docker Desktop (see [Judge0](#judge0) below). Feature work continues in Phase 4.

Progress is tracked phase by phase in **[`PROJECT_PLAN.md`](PROJECT_PLAN.md)**, which also records the confirmed technical decisions and the ten clarifications (G1–G10) resolving gaps in the original scope document.

| Phase | Module | Status |
|---|---|---|
| 1 | Foundation — toolchain, Docker services, scaffolding | ⚠️ Done, except Judge0 |
| 2 | Authentication — JWT, roles, OTP password reset | ✅ Done |
| 3 | Core Academic — departments, courses, subjects, students, faculty | ✅ Done |
| 4 | Academic Operations — attendance, exams, marks, grading | ⬜ Not started |
| 5 | Analytics — GPA/CGPA, performance trends, risk detection | ⬜ Not started |
| 6 | AI — study assistant, study plans, practice questions | ⬜ Not started |
| 7 | Coding — playground, problems, submissions, contests | ⬜ Not started |
| 8 | Placement — companies, drives, eligibility, applications | ⬜ Not started |
| 9 | Resume — builder, templates, PDF export | ⬜ Not started |
| 10 | Interview — question bank, scheduling | ⬜ Not started |
| 11 | Real-Time — WebSocket notifications, announcements | ⬜ Not started |
| 12 | Finalization — Swagger, seed data, testing, deployment | ⬜ Not started |

The setup instructions below describe the intended workflow. Commands that depend on code from a phase that has not shipped will not work yet.

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

### Infrastructure
| Service | Runs in | Port |
|---|---|---|
| MySQL 8.4 | Docker | 3306 |
| Judge0 | Docker (profile-gated, see below) | 2358 |
| Mailpit (dev SMTP) | Docker | 1025 SMTP / 8025 UI |
| Backend | Host (JVM) | 8080 |
| Frontend | Host (Vite) | 5173 |

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

Development seed data (admin, faculty, students, departments, courses, subjects, attendance, marks, companies, jobs, coding problems, contests and announcements) is loaded by a dedicated seed profile. Seed passwords are development-only and documented alongside the seed script.

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

The app becomes available at `http://localhost:5173`.

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
| Application | http://localhost:5173 |
| API | http://localhost:8080 |
| Mailpit inbox | http://localhost:8025 |
| Swagger UI | http://localhost:8080/swagger-ui.html *(Phase 12)* |

**Creating the first administrator.** Self-registration only ever creates a student, and provisioning staff accounts requires an existing admin — so the very first admin has to be promoted by hand:

```bash
# register normally at /register, then:
docker exec smartcampus-mysql mysql -usmartcampus -psmartcampus smartcampus \
  -e "UPDATE users SET role='ADMIN' WHERE email='you@example.com';"
```

Seed data that removes this manual step is Phase 12 scope.

---

## API Documentation

Interactive OpenAPI documentation at `/swagger-ui.html` arrives in Phase 12; until then the endpoints below are exercised directly over HTTP.

**Live today (Phases 2–3):**

```
/api/auth          register, login, me, password-reset (request / verify / reset)
/api/users         admin-only account provisioning (faculty and admin accounts)
/api/departments   /api/courses      /api/subjects
/api/students      /api/faculty      /api/enrollments
/api/faculty-subject-assignments
```

**Planned in later phases:**

```
/api/attendance    /api/marks        /api/analytics     /api/ai
/api/coding        /api/problems     /api/contests      /api/leaderboard
/api/companies     /api/jobs         /api/applications  /api/resumes
/api/interviews    /api/notifications /api/announcements
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

## Testing

### Backend
```bash
cd backend
./mvnw test                  # 31/31 green as of Phase 3
./mvnw verify                # full suite including integration tests
```

Repository and integration tests run against a real MySQL instance via Testcontainers rather than an in-memory substitute, so migrations and SQL dialect behaviour are genuinely exercised. No environment variables are required — `src/test/resources/application.properties` supplies a throwaway JWT signing key so a fresh clone can run the suite immediately.

Current coverage: registration, login, duplicate email, non-enumerating invalid credentials, JWT validation including tampered and expired tokens, role denial against a real admin-only endpoint, OTP reset round trip with attempt-cap enforcement, cross-student access attempts across every route that returns student data, faculty subject/section scoping, privilege escalation sweeps, SQL-level pagination, and the G1 activation flow. Attendance, marks, placement, coding and notification coverage arrives with the phases that build them.

### Frontend
No test runner is installed yet — frontend tests are Phase 12 scope. The build and linter are the current gates:

```bash
cd frontend
npm run build                # tsc -b + vite build
npm run lint                 # oxlint
```

---

## Deployment

```
Internet → React (static build) → Spring Boot → MySQL
                                       │
                                       ├── AI API
                                       ├── Judge0
                                       ├── SMTP
                                       └── WebSocket
```

```bash
cd frontend && npm run build      # emits dist/
cd backend && ./mvnw clean package -DskipTests
java -jar target/smartcampus-*.jar
```

All deployment configuration is supplied through environment variables. Production requires a real SMTP provider in place of Mailpit, a strong `JWT_SECRET` unique to the environment, HTTPS termination, and a CORS allowlist restricted to the deployed frontend origin.

---

## Folder Structure

```
SmartCampusERP/
├── backend/
│   └── src/
│       ├── main/
│       │   ├── java/smartcampus/
│       │   │   ├── config/          # app, CORS, WebSocket, OpenAPI config
│       │   │   ├── controller/      # REST endpoints
│       │   │   ├── dto/             # request and response DTOs
│       │   │   ├── entity/          # JPA entities
│       │   │   ├── exception/       # custom exceptions, global handler
│       │   │   ├── repository/      # Spring Data repositories
│       │   │   ├── security/        # JWT filter, security config
│       │   │   ├── service/         # business logic
│       │   │   └── util/
│       │   └── resources/
│       │       ├── application.properties
│       │       └── db/migration/    # Flyway migrations
│       └── test/
├── frontend/
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── layouts/
│   │   ├── services/            # API clients
│   │   ├── hooks/
│   │   ├── context/             # auth and app state
│   │   ├── routes/              # protected and role-based routing
│   │   ├── utils/
│   │   ├── assets/
│   │   └── App.tsx
│   ├── package.json
│   └── vite.config.ts
├── docs/
│   └── SmartCampus-ERP-Scope.pdf
├── docker-compose.yml
├── PROJECT_PLAN.md
├── README.md
├── .env.example
└── .gitignore
```

JPA entities are never exposed directly from the API — every endpoint speaks in DTOs, and passwords never appear in a response.

---

## Screenshots

Added as each phase ships. See the [Build Status](#build-status) table for current progress.

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
