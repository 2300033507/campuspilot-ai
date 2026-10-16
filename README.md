# CampusPilot AI

A student academic management platform. The interesting part is not a
dashboard — it is that **a student cannot mark themselves present**, and
that the attendance arithmetic lives in PostgreSQL rather than in the
browser.

**Live:** https://frontend-ashen-nu-28.vercel.app
**API:** https://campuspilot-api-ge7m.onrender.com
**Code:** https://github.com/2300033507/campuspilot-ai

| Demo login | Password |
|---|---|
| `demo@campuspilot.dev` (student) | `campus2026pass` |
| `faculty@campuspilot.dev` (faculty) | `faculty2026pass` |

> **The free backend sleeps after 15 minutes idle.** A cold visit can take
> 30–60 seconds while it wakes. Open the link shortly before showing it.

---

## What it does

Students see how many classes they can **safely miss**, not just their
percentage — and whether their target is still reachable at all.

```
Digital Electronics   40.0%   IMPOSSIBLE
  "You need 22 of the next 20 classes to reach 75%, but only 20
   remain. No attendance pattern can recover this."

DBMS                  90.0%   ON TRACK
  "You can safely miss 4 more classes and still hold 75%."
```

The first case is the point. A naive app shows a hopeful number forever;
this one says the target is unreachable.

---

## Architecture

```
React 19 + TypeScript   ──  Vercel
        │
        │  /api/*  (rewritten, so the frontend
        │            contains no backend URL)
        ▼
Spring Boot 4.1.1       ──  Render
        │
        ▼
Supabase Postgres 17    ──  data + RLS
```

Independent redeployment: change the backend and the frontend is untouched.
Verified — the built bundle contains no backend hostname.

---

## The three decisions worth defending

**1. No Java or TypeScript file does attendance arithmetic.**
Not in a service, not in a component, not in a helper. Every number was
computed by `attendance_health()` inside PostgreSQL and only read across
the wire. A formula duplicated in two languages eventually disagrees, and
nobody can tell which copy the user was looking at.

**2. A student cannot award themselves perfect attendance.**
`attendance_health()` is one SQL function; the rules around it are
enforced in `AttendanceService` **before any repository call**. A student
with a valid token calling the marking endpoint directly gets 403.

Verified on the live deployment:

```
student marks themselves present -> 403
student with no token            -> 401
student reads class roster       -> 403
faculty reads roster             -> 200
```

12 of the 27 tests exist solely for that. Every other test checks that
something *computes* correctly; these check that the product is not
meaningless if it breaks.

**3. Adding authentication touched one file.**
The app was built with an `X-Profile-Id` header first — openly insecure,
a deliberate placeholder so the attendance module could be worked on
before auth existed. Replacing it with verified JWT changed only
`CurrentProfileResolver`, because every service already took a `profileId`
as a parameter and never resolved identity itself.

Same property later let the role model change without touching services.

---

## The domain model

A subject is a **course offering**, not a student's copy:

```
subjects            an offering, owned by the teacher (faculty_id)
enrollments         which students sit in it
attendance_records  per (subject, student, date),
                    stamped with marked_by and marked_at
```

The first version had each student owning private subject rows, so two
students in one class shared nothing and a teacher marking attendance
for one would know nothing about the other. Migration V3 moved the live
data rather than dropping it.

`marked_by` exists so a disputed attendance mark is traceable rather
than arguable.

---

## Layout

```
db/            Step 1 SQL: schema, functions, RLS, tests, seed
backend/       Spring Boot API — 27 tests
frontend/      React + TypeScript — no test suite (see limitations)
Dockerfile     at repo root, so Render needs no configuration
```

---

## Migrations

Flyway, applied forward once each in order. `baseline-on-migrate` adopts
the Step 1 schema that was created by hand.

```
V2  password_hash
V3  faculty owns subjects; enrollments table; marked_by/marked_at;
    moves live data; drops subjects.profile_id
V4  role-aware RLS; v_attendance_health
```

**Never edit a migration that has run.** It will not re-run, so the edit
silently does nothing except on a fresh database. Write a new one.

---

## Run it locally

```powershell
# database
#   run db/01..03_schema_and_rls in the Supabase SQL editor, in order
#   db/04_verify.sql  -> 7 PASS
#   db/06_seed_demo.sql

cd backend
Copy-Item .env.example .env     # add Supabase values + a 32-byte JWT_SECRET
.\mvnw.cmd test                 # 27 tests
.\run-local.ps1

cd ..\frontend
npm install
npm run dev                     # http://localhost:5173
```

---

## Verification

| Layer | What was checked | How |
|---|---|---|
| SQL | 7 cases incl. zero-data and unrecoverable | `db/04_verify.sql`, 7/7 |
| SQL | schema state | `db/07_state_check.sql`, 6/6 |
| Java | 27 tests | `mvnw test` |
| Live | 403/401 rules hold in production | curl against Render |
| Live | site renders real data | browser |

`backend/RunSql.java` runs a migration against the real database in one
transaction and reports the first failing statement with its file offset.
Flyway reports only the error message, costing a 15-second restart per
attempt. `--dry-run` rolls back, so a migration can be validated without
touching state. It found three bugs in V3 in seconds.

---

## Bugs found and fixed

Every one diagnosed from a real error, not from reading documentation.

1. **A failed SQL batch rolled back everything** — multi-statement scripts
   are atomic, so a broken view silently removed the function created two
   statements earlier.
2. **`COUNT()` returns `bigint`** — Postgres will not narrow it to an
   `integer` parameter; the view failed with *function does not exist*
   until cast with `::integer`.
3. **`CREATE OR REPLACE VIEW` cannot add or reorder columns** — required
   `DROP VIEW` then `CREATE VIEW`.
4. **`Enable automatic RLS` masked a failure** — it enables RLS on every
   new table, so policies looked configured while zero existed.
5. **The `recoverable` flag was meaningless** — the view passed `0` for
   classes remaining, collapsing it to "am I already at target?", so every
   struggling student would have been shown `IMPOSSIBLE`.
6. **`Repository<DashboardRow, UUID>`** — Spring Data requires a
   repository's domain type to be a JPA entity; a projection is not one.
7. **A unit test caught an untrimmed-name bug** — the duplicate check ran
   before normalisation, so `"DBMS"` and `"DBMS "` both passed it.
8. **`jjwt-jackson` fails at runtime against Jackson 3** — compiles clean,
   dies on first use. Boot 4 moved to `tools.jackson`.
9. **A 0-byte parent POM in `~/.m2`** from an interrupted download.
10. **Spring Boot auto-created a default in-memory user** with a
    generated password, unused here and confusing.
11. **The Vite 8 template ships without React**, and without `jsx` or
    `strict` — which removes most of the reason to use TypeScript.
12. **Render's API silently dropped `dockerContext`/`dockerfilePath`** —
    accepted the service, returned both empty, build failed with no
    Dockerfile. Moved the Dockerfile to the repo root so the defaults work.

---

## Limitations, stated rather than hidden

**No frontend tests.** 27 backend tests, zero frontend. The asymmetry is
the obvious gap.

**Tokens are in `localStorage`**, readable by any script on the page. An
`httpOnly` cookie is the better answer and needs CSRF protection on the
backend. Named here so it can be discussed instead of discovered.

**A stateless token cannot be revoked before it expires.** A session can.
The TTL is 24h; production would use ~15 minutes plus a refresh-token
allowlist.

**Postgres RLS is bypassed by our own connection**, because Spring Boot
connects as the table owner. The role rules that actually matter are
enforced in Spring Security. RLS protects the public Data API surface —
do not claim it is what stops a student marking attendance, because it is
not.

**Faculty get no dashboard of their own.** They are redirected to their
class list, which is a stopgap. A real one needs an aggregate across the
classes they teach.

**The free backend sleeps after 15 minutes.** Fine for a demo, not for
anything real.

---

## Interview answers in one line each

| Question | Answer |
|---|---|
| Why PostgreSQL for the maths? | Client-side arithmetic isn't authoritative — the user controls the browser. |
| How do you stop a student marking themselves present? | Role check in the service before any repository call; the UI hiding a button is not the control. |
| What about object-level access? | `findByIdAndProfileId` returns empty for someone else's row, so a guessed id yields 404, not 403. |
| How did you test security? | 12 tests where the failure mode is a student faking attendance; the failure message would be "everyone passed". |
| Why not just check the role? | "Student" is not the negative of "faculty". Access is granted by naming the roles that have it. |
| How do you keep one formula? | One implementation in Postgres. The frontend chooses wording, never thresholds. |
| What was your best bug? | The `recoverable` flag that told every failing student their situation was impossible. |