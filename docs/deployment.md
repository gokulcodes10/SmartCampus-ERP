# Deployment (scope §71)

This is the detail behind the README's [Deployment](../README.md#deployment) section:
the real production topology, exactly what changes from the development defaults, and
how to actually build and run the containers. Read the README section first for the
short version; this document is the long version.

---

## 1. Topology

Scope §71 draws the production shape as a straight line with the backend's external
dependencies hanging off it — this is that diagram, not a reinterpretation:

```
                         Internet
                            │
                            │  HTTPS (terminated in front of this stack —
                            │  see §5. Nothing in this repo terminates TLS itself)
                            ▼
                  ┌───────────────────┐
                  │  React Frontend   │   nginx serving the Vite static build
                  │  (frontend image) │   SPA history fallback + reverse proxy
                  └─────────┬─────────┘
                             │  same-origin /api/*, /ws/*
                             │  (nginx forwards to the backend container —
                             │   see frontend/nginx.conf)
                             ▼
                  ┌───────────────────┐
                  │  Spring Boot API  │
                  │  (backend image)  │
                  └─────────┬─────────┘
                             │
              ┌──────────────┼──────────────┬───────────────┐
              ▼              ▼              ▼               ▼
        ┌──────────┐  ┌───────────┐  ┌───────────┐   ┌─────────────┐
        │  MySQL   │  │  AI API   │  │  Judge0   │   │    SMTP     │
        │ (mysql   │  │  (Groq —  │  │ (external,│   │ (external   │
        │  image)  │  │  external)│  │  external)│   │  provider)  │
        └──────────┘  └───────────┘  └───────────┘   └─────────────┘
```

The WebSocket in scope §71's external list (`/ws/notifications`) is not a separate
service — it is the same Spring Boot backend process, reached through the same nginx
proxy as the REST API (see the `location /ws/` block in `frontend/nginx.conf`). It is
listed as external in the scope diagram because it is a distinct *protocol* off the
backend, not a distinct box; that is reflected here by drawing it as the same arrow
the REST API takes, not a fifth external dependency.

`docker-compose.prod.yml` builds and wires the frontend, backend and MySQL boxes.
The three external dependencies (AI API, Judge0, SMTP) are never containers in that
file — they are always reached over the network via a URL and a key/credential in
the environment, per scope §70's "external services must be abstracted behind
backend service classes so providers can be changed later" rule. This is already how
`AIService → GroqAIService`, `CodeExecutionService → Judge0Service` and
`EmailService → SmtpEmailService` are built (Phases 6, 7, 2).

---

## 2. What must change for production

None of these are platitudes — each one is a specific default in this repository
today, and each one is wrong to ship as-is.

| # | Default today (dev) | What it must become in production | Why |
|---|---|---|---|
| 1 | `CORS_ALLOWED_ORIGINS=http://localhost:5175,http://localhost:5173,http://localhost:5174` | The deployed frontend's real origin only, e.g. `https://erp.example.edu` | **This is the single most dangerous default in the config.** Left as-is, any site can drive authenticated cross-origin requests against a production API using a token stolen or phished from a browser that has one — the CORS check is the only thing standing between "the API only answers our frontend" and "the API answers whoever sets an Origin header it likes." |
| 2 | `JWT_SECRET=` (empty) | A freshly generated random secret, unique to this environment: `openssl rand -base64 48` | The main config gives this key an **empty default on purpose** — Spring Security fails to boot rather than silently signing tokens with a guessable or shared key. Treat a boot failure here as the config working correctly, not a bug to route around; supply a real secret instead. Never reuse the value from `.env`/dev or from another environment. |
| 3 | `SWAGGER_ENABLED=true` (implicit default) | `SWAGGER_ENABLED=false` unless the API surface is intentionally public | `application-prod.properties` already flips the *default* to false when `SPRING_PROFILES_ACTIVE=prod` is set, so this is usually already handled — call it out explicitly anyway in whatever secret store holds the production environment, since an operator who copies the dev `.env` wholesale would re-enable it. |
| 4 | `FLYWAY_OUT_OF_ORDER=true` | `false` (also the `application-prod.properties` default) | True is only correct here because Phases 1–12 were built as parallel waves that reached this repo's shared *development* database out of numeric version order (V7 before V5/V6, V9 after V10/V11 — see `PROJECT_PLAN.md`'s phase notes). A fresh production database receives V1..V12 in order; `out-of-order=false` means an unexpected out-of-sequence migration fails the boot loudly instead of applying silently, which is what you want the one time it actually indicates a mistake. |
| 5 | `SMTP_HOST=localhost` / `SMTP_PORT=1025`, Mailpit | A real SMTP provider's host, port, username and password (SES, Mailgun, a Gmail app password, etc.) | Mailpit is a dev-only sink — it accepts anything and delivers nothing onward. OTP mail, password-reset mail and notification mail all go nowhere in production until this is a real provider. |
| 6 | `JUDGE0_URL=http://localhost:2358` (self-hosted, and non-functional on this machine — see §4) | A Judge0 instance actually reachable from the backend container | See the honesty note in §4 below — this is not solved by a config value alone. |
| 7 | No TLS anywhere in this repo | HTTPS termination in front of the stack | See §5. Without it, HSTS (added by task 1's secure-headers work — see the README's Authentication/Security notes) never actually engages, since `Strict-Transport-Security` is meaningless advice to a browser that already reached the site over plain HTTP. |
| 8 | `DB_USERNAME=smartcampus` / `DB_PASSWORD=smartcampus` | Credentials unique to the production database, not the well-known dev pair committed in `docker-compose.yml` | `docker-compose.yml`'s dev MySQL credentials are intentionally committed — they are dev-only and documented as such. `docker-compose.prod.yml` has **no default** for `DB_USERNAME`/`DB_PASSWORD`/`DB_ROOT_PASSWORD` — compose refuses to start without them (see its `:?` required-variable syntax) rather than silently falling back to `smartcampus`/`smartcampus`. |
| 9 | (no profile set) | `SPRING_PROFILES_ACTIVE=prod`, and the `seed` profile **never** included alongside it | `docker-compose.prod.yml` hardcodes `SPRING_PROFILES_ACTIVE: prod` and separately hardcodes `SMARTCAMPUS_SEED_ENABLED: "false"` (not environment-driven) specifically so a production deployment cannot accidentally seed fake students, fake marks and a known-password admin into a real database. See §65 in `docs/scope-extracted.md` and the seed-vs-bootstrap explanation in the README's Database Setup section. |

**A caveat worth carrying into any future multi-instance deployment**: the §61 auth
rate limiter (`AuthRateLimiter`/`AuthRateLimitFilter`, gating `/api/auth/login`,
`/api/auth/register` and `/api/auth/password-reset/**`) is an in-memory fixed-window
counter. It does not persist across a restart and does not coordinate across
replicas — running two backend containers behind a load balancer effectively doubles
the real per-caller limit, and a rolling restart resets it to zero. `docker-compose.prod.yml`
runs a single backend replica, so this does not bite today; it would need a shared
store (Redis, or the database) before scaling the backend horizontally.

---

## 3. File-upload validation (scope §61) — not applicable, and here is why

Scope §61 lists "file upload validation" as a mandatory security item. This system has
**no file upload capability anywhere**, verified directly against the codebase rather
than assumed:

- Zero `MultipartFile` references in `backend/src/main/java`.
- No multipart configuration (`spring.servlet.multipart.*`) anywhere in
  `application.properties`.
- `Student` has no `profileImage` column (scope §12 mentions one; it was never built).
- `Company` has no `logo` column (scope §33 mentions one; it was never built).
- Scope §49 describes broader file management; the only file-shaped thing this system
  actually produces is a **server-generated resume PDF download**
  (`GET /api/resumes/{id}/pdf`, built in Phase 9) — the backend renders the PDF from
  data already in the database and streams it out. Nothing is ever uploaded *into*
  the system by a user.

There is therefore no file-upload validation to configure, and no upload endpoint for
a production deployment to secure or rate-limit. If a future phase adds real uploads
(profile photos, company logos, resume attachments), this section — and a multipart
size/type/virus-scan policy — needs to be written then, not before.

---

## 4. Judge0 — still unresolved (honesty note, G10 / D1 / D2)

**Live code execution does not work on this machine, and this deployment
configuration does not fix that.** The coding module (Phase 7) is fully built against
the real Judge0 API contract — submission creation, polling, verdict mapping, hidden
test cases, contest scoring — but no Judge0 endpoint is reachable in this development
environment: Docker Desktop's LinuxKit VM is cgroup-v2-only, and Judge0 1.13.1's
bundled `isolate` sandbox requires cgroup v1, so every submission returns status 13
Internal Error. This was measured directly (see `docs/judge0-notes.md` for the full
transcript), not assumed.

`docker-compose.prod.yml` does not attempt to self-host Judge0 alongside the
production stack, because the same cgroup-v1 requirement applies to any container
host running the same kind of VM-backed Docker runtime. For a real production
deployment, `JUDGE0_URL` (and `JUDGE0_API_KEY` if hosted) must point at one of:

- **A hosted Judge0 instance** (e.g. RapidAPI's `judge0-ce`) — works today, config-only,
  but rate/volume limits on free tiers may not support a live contest (scope §31–32).
- **A bare-metal or VM host that is actually cgroup v1** (or an amd64 Linux host
  booted with `systemd.unified_cgroup_hierarchy=0`) running Judge0 directly, outside
  this compose file.

This decision (open decision D1/D2 in `PROJECT_PLAN.md`) was not made on this
project's behalf, and this deployment configuration does not claim it is. Do not read
`docker-compose.prod.yml`'s inclusion of `JUDGE0_URL`/`JUDGE0_API_KEY` as evidence
that a working Judge0 is one `docker compose up` away — it is a configuration slot,
not a resolved dependency.

---

## 5. HTTPS termination

Nothing in `docker-compose.prod.yml` terminates TLS — `frontend/nginx.conf` listens
on plain HTTP:80 by design, so it can sit behind whichever terminator the actual
deployment target provides (a managed load balancer, a platform's built-in TLS, a
separate reverse-proxy container/host with a cert from Let's Encrypt or a CA). This
keeps the container images themselves platform-agnostic — no certificate paths or
renewal logic baked in — at the cost of the deployer needing to add that layer
explicitly. Whatever terminates TLS should forward to the frontend container's
port 80 (or the `FRONTEND_PORT` it is published on) and set `X-Forwarded-Proto:
https`, which `frontend/nginx.conf` already passes through to the backend on its own
proxied requests.

Until HTTPS is in front of the stack, `Strict-Transport-Security` (part of the secure
HTTP headers added in this phase — see the README) has no effect: HSTS only pins a
browser to HTTPS *after* it has already been told to over an HTTPS connection, so it
cannot bootstrap trust on a plain-HTTP deployment. Deploying without item 7 in the
table above means that header is a no-op, not a mitigation.

---

## 6. Building and running the containers

```bash
# From the repository root.
cp .env.example .env
# then edit .env with REAL production values — everything in §2 above, at minimum.

docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f backend
```

Individual image builds (what this task actually ran to verify the Dockerfiles
build — see the README's Deployment section for the exact result):

```bash
docker build -t smartcampus-backend  -f backend/Dockerfile  backend
docker build -t smartcampus-frontend -f frontend/Dockerfile frontend
```

The backend image is a two-stage build (`maven:3.9-eclipse-temurin-21` →
`eclipse-temurin:21-jre-alpine`, non-root `smartcampus` user, no secret baked in —
every credential is read from the environment at container start, never passed as a
build arg). The frontend image is also two-stage (`node:22-alpine` → `nginx:1.27-alpine`)
and takes one build arg, `VITE_API_BASE_URL`, which defaults to `""` (relative) so the
browser calls same-origin `/api/...` and lets `nginx.conf`'s reverse proxy forward it
to the `backend` service — see the comments at the top of `frontend/Dockerfile` for
when you would want to override that default.

---

## 7. Environment variables reference

Full list and placeholders live in `.env.example` at the repository root (backend)
and `frontend/.env.example` (frontend). This is a pointer, not a duplicate — keep the
authoritative list in those files so it cannot drift out of sync with this document.
