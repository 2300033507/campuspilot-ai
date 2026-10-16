-- =====================================================================
-- CampusPilot AI  |  Step 1  |  01_schema.sql
--
-- PURPOSE : Only two tables matter right now — subjects and attendance.
--           Everything else (assignments, marks, placements) waits.
--
-- HOW TO RUN:
--   Supabase dashboard -> SQL Editor -> New query -> paste -> Run
--   Run these files IN ORDER:  01 -> 02 -> 03
--
-- NOTE ON auth_user_id:
--   It is NULLABLE on purpose. That lets you insert realistic test data
--   today, before building any login screen. Week 2 we add the foreign
--   key to auth.users and make it NOT NULL.
-- =====================================================================

-- ---------------------------------------------------------------------
-- profiles : one row per person
-- ---------------------------------------------------------------------
-- IF NOT EXISTS makes this file safe to re-run. Without it, a second
-- run errors with "relation already exists" and, because Postgres wraps
-- the batch in one transaction, nothing in the file would apply.
--
-- Tradeoff: it silently skips a table that already exists with the WRONG
-- shape. For schema bootstrap that is the right default. If you ever need
-- to change a table's structure, write a numbered migration instead of
-- editing 01 — see 05_remaining_classes.sql for that pattern.
CREATE TABLE IF NOT EXISTS profiles (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    auth_user_id  uuid UNIQUE,           -- links to Supabase Auth later
    full_name     text NOT NULL
                  CHECK (length(trim(full_name)) > 0),   -- no empty names
    email         text UNIQUE,
    role          text NOT NULL DEFAULT 'student'
                  CHECK (role IN ('student', 'faculty')), -- only 2 for v1
    created_at    timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE  profiles            IS 'People who use the app. Mirrors one-to-one with Supabase auth.users.';
COMMENT ON COLUMN profiles.auth_user_id IS 'Supabase auth UUID. NULL until we wire up authentication.';


-- ---------------------------------------------------------------------
-- subjects : a subject a student is enrolled in
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS subjects (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id        uuid NOT NULL
                      REFERENCES profiles(id) ON DELETE CASCADE,
    name              text NOT NULL
                      CHECK (length(trim(name)) > 0),
    code              text
                      CHECK (code IS NULL OR code = upper(code)),  -- force uppercase
    semester          integer NOT NULL
                      CHECK (semester BETWEEN 1 AND 10),
    target_attendance numeric(5,2) NOT NULL DEFAULT 75.00
                      CHECK (target_attendance BETWEEN 0 AND 100),
    created_at        timestamptz NOT NULL DEFAULT now(),

    -- a person cannot enrol in the same subject twice
    CONSTRAINT subjects_profile_name_unique UNIQUE (profile_id, name)
);

-- Every query filters or joins on profile_id, so index it.
CREATE INDEX IF NOT EXISTS idx_subjects_profile ON subjects(profile_id);

COMMENT ON COLUMN subjects.target_attendance IS 'Required percentage, e.g. 75. Feeds attendance_health().';


-- ---------------------------------------------------------------------
-- attendance_records : one row = one class that was actually held
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attendance_records (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id  uuid NOT NULL
                REFERENCES subjects(id) ON DELETE CASCADE,
    held_on     date NOT NULL,
    status      text NOT NULL
                CHECK (status IN ('present', 'absent')),  -- no typos allowed
    note        text,
    created_at  timestamptz NOT NULL DEFAULT now(),

    -- prevents marking the same day's class twice
    CONSTRAINT attendance_subject_date_unique UNIQUE (subject_id, held_on)
);

-- Reporting queries group by subject and sort by date.
CREATE INDEX IF NOT EXISTS idx_attendance_subject_date ON attendance_records(subject_id, held_on DESC);

COMMENT ON TABLE attendance_records IS 'One row per class held. Absent counts toward total_held — that is what makes attendance drop.';


-- =====================================================================
-- DONE. Run 02_logic.sql next.
--
-- Sanity check (should return 0 rows):
--   SELECT table_name FROM information_schema.tables
--   WHERE table_schema='public'
--     AND table_name NOT IN ('profiles','subjects','attendance_records');
-- =====================================================================