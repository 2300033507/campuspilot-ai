# CampusPilot AI

Cloud-first academic management platform for students.

**Current status: Step 1 (database) verified. Step 2 (Spring Boot API) built and tested. Step 3 (authentication) complete — 20/20 tests, verified live.**

```
campuspilotai/
├── db/                Step 1 SQL — schema, functions, RLS, tests, seed
└── backend/           Step 2 — Spring Boot 4.1.1 / Java 21 REST API
```

---

## Step 2 — the API

| Component | State |
|---|---|
| Spring Boot 4.1.1, Java 21, Maven wrapper | building |
| JPA entities for all three tables | done |
| `SubjectController` — full CRUD, ownership-scoped | done |
| `AttendanceController` — mark, list, delete | done |
| `DashboardController` — reads `v_student_dashboard` | done |
| Bean Validation on every request DTO | done |
| `GlobalExceptionHandler` — one error shape for all failures | done |
| Flyway with `baseline-on-migrate` | done |
| `SubjectServiceTest` — 5 tests including access control | 5/5 pass |
| Secrets read from environment variables, never committed | done |
| Verified end to end against live Supabase | done |
| Authentication | **not started** — `X-Profile-Id` header placeholder |

### Verified live

```
GET    /api/dashboard          -> 6 subjects, all 5 status codes
GET    /api/dashboard/summary  -> {"IMPOSSIBLE":1,"CRITICAL":1,"AT_RISK":1,"ON_TRACK":2,"NO_DATA":1}
GET    /api/subjects           -> 6 subjects
POST   /api/subjects           -> 201
POST   duplicate name          -> 409 "You already have a subject named..."
POST   attendance             -> 201
POST   same date twice         -> 409 "already recorded for this subject"
POST   future date             -> 400 "Cannot record attendance for a future class"
POST   semester 99, code lower -> 400 with all 3 field errors at once
GET    no header               -> 404 with the standard error shape
```

All responses share one error shape. No stack traces or SQL text leak to
the client.

### The central rule of this step

**No Java file in this project does attendance arithmetic.** There is no
division, no `floor`, no percentage anywhere outside PostgreSQL. Every
number came from `attendance_health()` before it reached the API.

That matters because a formula duplicated in Java and SQL will eventually
disagree, and nobody would know which copy the student was looking at.

### Endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | public | create account, returns token |
| POST | `/api/auth/login` | public | returns token |
| GET | `/api/dashboard` | token | all subjects with computed health |
| GET | `/api/dashboard/summary` | token | counts per status |
| GET | `/api/subjects` | token | list |
| POST | `/api/subjects` | token | create |
| PUT | `/api/subjects/{id}` | token | update |
| DELETE | `/api/subjects/{id}` | token | delete (cascades attendance) |
| GET | `/api/subjects/{id}/attendance` | token | list records |
| POST | `/api/subjects/{id}/attendance` | token | mark a class |
| DELETE | `/api/subjects/{id}/attendance/{recordId}` | token | remove a record |
| GET | `/actuator/health` | public | liveness |

### Running it

```powershell
cd backend
Copy-Item .env.example .env     # then fill in Supabase values + JWT_SECRET
.\run-local.ps1
```

Needs the **session pooler** host from Supabase plus a `JWT_SECRET` of at
least 32 bytes. Then:

```powershell
# get a token
$login = Invoke-RestMethod -Method POST `
  -Uri http://localhost:8080/api/auth/login `
  -ContentType "application/json" `
  -Body (@{ email="demo@campuspilot.dev"; password="campus2026pass" } | ConvertTo-Json)

# call the API with it
Invoke-RestMethod -Uri http://localhost:8080/api/dashboard `
  -Headers @{ Authorization = "Bearer $($login.token)" }
```

---

## Step 1 — what exists

| Component | State |
|---|---|
| Schema — `profiles`, `subjects`, `attendance_records` | verified |
| Constraints, foreign keys, indexes | verified |
| `attendance_health()` — attendance maths, `IMMUTABLE` | verified, 7/7 tests pass |
| `subject_attendance_summary()` | verified |
| `v_student_dashboard` — full dashboard feed | verified |
| Row Level Security on all 3 tables | verified, 5 policies |
| `security_invoker` on the view | verified |
| Demo data, all 5 status codes | verified |
| Spring Boot API | not started |
| React UI | not started |
| Auth wiring (`auth_user_id` → `auth.users`) | not started |

---

## Files

| File | Purpose | Re-runnable |
|---|---|---|
| `01_schema.sql` | tables, constraints, indexes | yes |
| `02_logic.sql` | `attendance_health()`, summary function | yes |
| `03_rls.sql` | RLS policies, `security_invoker` | yes |
| `04_verify.sql` | 7-case test suite for the maths | yes, read-only |
| `05_remaining_classes.sql` | migration: adds remaining-classes estimate | yes |
| `06_seed_demo.sql` | demo student with 6 subjects | yes |
| `07_state_check.sql` | single-query health check, 6 checks | yes, read-only |
| `verify_math.mjs` | independent JS check of the maths | `node db/verify_math.mjs` |

Run them **in numeric order.** All are safe to re-run.

---

## Why the calculation lives in the database

The tempting version puts the percentage in a React `useMemo`. That is
wrong, because it runs on a machine the student controls — open DevTools,
change the number.

Here the browser only *displays* what `attendance_health()` returns. It
cannot influence the result.

---

## The calculation

| Question | Formula |
|---|---|
| Current percentage | `attended / total × 100` |
| Classes still skippable | `floor(100 × attended / target − total)` |
| Classes that must be attended | `ceil((target × (total + future) − 100 × attended) / 100)` |
| Is the target still reachable? | only if attending **every** remaining class suffices |

The last line is the part usually missed. A student at 40% with 20 classes
left needs 22 to recover — more than exist. The app reports `IMPOSSIBLE`
instead of showing a hopeful number forever.

---

## Step 3 — authentication

| Component | State |
|---|---|
| `POST /api/auth/register` | done |
| `POST /api/auth/login` | done |
| JWT issue + verify (jjwt, HS256) | done |
| BCrypt password hashing, cost 10 | done |
| Stateless Spring Security, all routes authenticated | done |
| `V2__add_password_hash.sql` Flyway migration | applied to live DB |
| 15 auth tests (forged, tampered, garbage, wrong-secret, short-secret) | 15/15 pass |
| Secret from env var, app refuses to start without it | done |
| Refresh tokens / revocation | **not done** — stated limitation below |

### The swap cost one file

Authentication was added *after* the attendance module worked. Changing
identity from "whatever `X-Profile-Id` says" (openly insecure) to a
verified JWT touched **only `CurrentProfileResolver`**.

That was not luck. Every service already took `profileId` as a parameter
and never resolved identity itself: the controller decides who the caller
is, then passes the answer down. Controllers are the only place that
needs to know how authentication works.

### Verified live

```
GET  /api/dashboard   no token        -> 401 "Authentication required..."
GET  /api/dashboard   garbage token   -> 401, IDENTICAL message
GET  /api/dashboard   X-Profile-Id    -> 401  (old header is now dead)
POST /api/auth/register               -> 201 + token, expiresIn=86400
POST /api/auth/register duplicate     -> 409
POST /api/auth/register short pw      -> 400 "at least 10 characters"
POST /api/auth/login  wrong password  -> 401 "Email or password is incorrect"
POST /api/auth/login  unknown email   -> 401, IDENTICAL message
POST /api/auth/login  correct         -> 200 + token
GET  /api/dashboard   with token      -> 200, all 6 subjects
```

The two "identical message" pairs are deliberate and tested. Distinguishing
*"no such email"* from *"wrong password"* turns the login form into a tool
for discovering which addresses have accounts.

### Stated limitation

A stateless token cannot be revoked before it expires. A session can. That
is the real trade-off of JWT, and the honest fix is a refresh-token
allowlist plus a short access-token TTL. The TTL here is 24h; a production
system would use ~15 minutes plus refresh.

### Demo account

`demo@campuspilot.dev` / `campus2026pass` — pre-seeded in Step 1 with all
six subjects. The password hash was set with pgcrypto's `crypt()`, which
emits the same `$2a$` format Spring's BCrypt encoder produces.

---

Each of these was diagnosed from a real error, not guessed at.

1. **A failed SQL batch rolled back everything.** Multi-statement scripts
   are atomic, so a broken view silently removed the function created two
   statements earlier. Nothing partially applied.
2. **`COUNT()` returns `bigint`.** Postgres will not narrow it to the
   function's `integer` parameters, so the view failed with *function does
   not exist* until cast with `::integer`.
3. **`CREATE OR REPLACE VIEW` cannot add or reorder columns** — only
   change the expression behind an existing one. Required `DROP VIEW`
   then `CREATE VIEW`.
4. **`Enable automatic RLS` at project creation** enables RLS on every new
   table via an event trigger. This masked the fact that the policy script
   had never run — RLS looked enabled, but zero policies existed.
5. **The dashboard view passed `0` for classes remaining**, so the
   `recoverable` flag collapsed to "am I already at target?" and every
   struggling student would have been shown `IMPOSSIBLE`. Fixed by storing
   an explicit `remaining_classes_estimate`.
6. **`Repository<DashboardRow, UUID>`** — Spring Data requires a
   repository's domain type to be a JPA entity. An interface projection
   is not one, so startup failed with *Not a managed type*. The generic
   became `Subject`; projections are still allowed as return types.
7. **A unit test caught a real bug**: the duplicate-name check ran on the
   *untrimmed* input, so `"DBMS"` and `"DBMS "` both passed the
   pre-check. Now normalised before comparing.
8. **Initializr's `.RELEASE` suffix isn't a real artifact version.**
   `spring-boot-starter-parent:4.1.1.RELEASE` does not exist on Maven
   Central; the published version is `4.1.1`.
9. **Spring Boot 4 ships Jackson 3**, which moved to the `tools.jackson`
   namespace. `com.fasterxml.jackson.databind.ObjectMapper` no longer
   resolves. The annotations package is the exception and stays on
   `com.fasterxml.jackson`.
10. **`jjwt-jackson` is incompatible with Jackson 3.** It threw
    `JacksonSerializer Unable to get public no-arg constructor` at runtime
    — a compile-time-clean, test-time-fail combination. Switching to
    `jjwt-gson` removed the Jackson dependency entirely.
11. **A 0-byte parent POM in `~/.m2`.** `jjwt-root-0.12.6.pom` had been
    left empty by an interrupted download, so JJWT resolved with broken
    metadata. Purging the directory and pinning the version to one with
    intact artifacts fixed it.
12. **Spring Boot auto-created a default in-memory user** with a
    generated password, because no `UserDetailsService` bean exists.
    Unused here, but confusing; excluded explicitly.

---

## Rules for changing the schema

- **Schema files build. Migration files change.**
- Never edit `01_schema.sql` after it has been applied. Write a new
  numbered file instead, the way `05_remaining_classes.sql` does.
- `IF NOT EXISTS` is used for re-runnability, but it silently skips a
  table that exists with the wrong shape. That is exactly why migrations
  exist.

---

## Not yet built

- **React frontend** — nothing is viewable in a browser yet
- **GitHub repository** — no version control, so no history yet
- Refresh tokens / token revocation
- Faculty and admin roles (schema supports them, unused)
- Assignments, marks, document library, AI study assistant, placement module
- Deployment (Vercel frontend + Render backend)

## Honest assessment

What exists is genuinely solid and verifiable: a database with real
constraints, RLS, and tested calculations; a Java API with ownership
scoped on every query; and authentication that an attacker cannot bypass
by guessing a header.

What it is not is a product. There is no interface anyone can look at,
and recruiters cannot see any of this without running it. Those are the
next two pieces, in that order — the frontend makes it demonstrable, and
GitHub makes it visible.