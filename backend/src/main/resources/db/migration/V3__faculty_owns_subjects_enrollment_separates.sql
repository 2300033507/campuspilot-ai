-- =====================================================================
-- V3__faculty_owns_subjects_enrollment_separates.sql
--
-- WHY THIS MIGRATION EXISTS
--   The original model let a student record their OWN attendance, and
--   gave every student a private copy of every subject. Both are wrong:
--
--   1. Attendance is an observation. A student declaring themselves
--      present makes the record worthless.
--   2. "DBMS" for two students was two unrelated rows, so a teacher
--      marking a class for one student would know nothing about the
--      other. A subject is a COURSE OFFERING, not a personal record.
--
-- THE NEW MODEL
--   subjects            a course offering, owned by the faculty who
--                       teaches it (faculty_id)
--   enrollments         which students sit in that offering
--   attendance_records  one row per (subject, student, date), stamped
--                       with who marked it and when
--
-- This migration also MOVES existing data rather than dropping it, so
-- the demo dataset survives the change.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. A faculty account to own the migrated subjects.
-- ---------------------------------------------------------------------
INSERT INTO profiles (full_name, email, role, password_hash)
SELECT 'Dr. Anita Rao', 'faculty@campuspilot.dev', 'faculty',
       crypt('faculty2026pass', gen_salt('bf', 10))
WHERE NOT EXISTS (
    SELECT 1 FROM profiles WHERE email = 'faculty@campuspilot.dev'
);


-- ---------------------------------------------------------------------
-- 2. Subjects belong to faculty now.
-- ---------------------------------------------------------------------
ALTER TABLE subjects ADD COLUMN IF NOT EXISTS faculty_id uuid
    REFERENCES profiles(id) ON DELETE SET NULL;


-- ---------------------------------------------------------------------
-- 3. Enrollments: the many-to-many that was missing.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS enrollments (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id  uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    subject_id  uuid NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    enrolled_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT enrollments_unique UNIQUE (profile_id, subject_id)
);

CREATE INDEX IF NOT EXISTS idx_enrollments_profile ON enrollments(profile_id);
CREATE INDEX IF NOT EXISTS idx_enrollments_subject ON enrollments(subject_id);


-- ---------------------------------------------------------------------
-- 4. Attendance becomes per student, and records who marked it.
-- ---------------------------------------------------------------------
ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS profile_id uuid
    REFERENCES profiles(id) ON DELETE CASCADE;
ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS marked_by uuid
    REFERENCES profiles(id) ON DELETE SET NULL;
ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS marked_at timestamptz NOT NULL DEFAULT now();

-- The old uniqueness was (subject, date), which allowed two students to
-- share a date but would now wrongly block a faculty roll call.
ALTER TABLE attendance_records
    DROP CONSTRAINT IF EXISTS attendance_subject_date_unique;
ALTER TABLE attendance_records
    ADD CONSTRAINT attendance_subject_student_date_unique
    UNIQUE (subject_id, profile_id, held_on);


-- ---------------------------------------------------------------------
-- 5. MOVE THE DATA.
--    One shared subject per (name, code, semester). Each old row becomes
--    an enrollment, and its attendance records follow.
-- ---------------------------------------------------------------------

-- Relax this first. A shared course offering has no single owner, so the
-- new rows legitimately have no profile_id — and the NOT NULL constraint
-- would reject them. The column is dropped entirely further down, once
-- the old rows that still need its value have been read.
ALTER TABLE subjects ALTER COLUMN profile_id DROP NOT NULL;

-- Snapshot the OLD rows BEFORE inserting anything.
--
-- Building this mapping afterwards would be wrong: once the shared
-- offerings exist, a join on (name, code, semester) matches both the old
-- personal rows and the new shared ones, and the new rows have no
-- profile_id. That silently produces enrollments for nobody.
CREATE TEMP TABLE old_subjects AS
SELECT id, profile_id, name, code, semester,
       target_attendance, remaining_classes_estimate
FROM   subjects;

CREATE TEMP TABLE distinct_courses AS
SELECT DISTINCT name, code, semester, target_attendance,
                remaining_classes_estimate
FROM   old_subjects;

ALTER TABLE distinct_courses ADD COLUMN new_id uuid;

INSERT INTO subjects (faculty_id, name, code, semester,
                      target_attendance, remaining_classes_estimate)
SELECT f.id, d.name, d.code, d.semester,
       d.target_attendance, d.remaining_classes_estimate
FROM   distinct_courses d
CROSS  JOIN (SELECT id FROM profiles WHERE email = 'faculty@campuspilot.dev'
             LIMIT 1) f;

-- Resolve each course's new id. faculty_id is used to pick out the rows
-- just inserted, so an offering with the same name and no teacher is not
-- matched by accident.
UPDATE distinct_courses d
SET    new_id = s.id
FROM   subjects s
WHERE  s.name = d.name
AND    s.code IS NOT DISTINCT FROM d.code
AND    s.semester = d.semester
AND    s.faculty_id IS NOT NULL;

-- Map old personal subject row -> shared course offering.
CREATE TEMP TABLE subject_map AS
SELECT o.id AS old_id, o.profile_id AS student_id, d.new_id
FROM   old_subjects o
JOIN   distinct_courses d
  ON   d.name = o.name
  AND   d.code IS NOT DISTINCT FROM o.code
  AND   d.semester = o.semester;

-- Each old subject becomes an enrollment.
INSERT INTO enrollments (profile_id, subject_id)
SELECT m.student_id, m.new_id
FROM   subject_map m
ON CONFLICT DO NOTHING;

-- Stamp existing attendance with its student and its marker.
UPDATE attendance_records ar
SET    profile_id = m.student_id,
       marked_by  = f.id
FROM   subject_map m
CROSS  JOIN (SELECT id FROM profiles WHERE email = 'faculty@campuspilot.dev'
             LIMIT 1) f
WHERE  ar.subject_id = m.old_id;

UPDATE attendance_records ar
SET    subject_id = m.new_id
FROM   subject_map m
WHERE  ar.subject_id = m.old_id;

-- The old personal rows are now redundant.
DELETE FROM subjects WHERE id IN (SELECT old_id FROM subject_map);

DROP TABLE subject_map;
DROP TABLE distinct_courses;
DROP TABLE old_subjects;

-- Now mandatory, since every row has been backfilled.
ALTER TABLE attendance_records
    ALTER COLUMN profile_id SET NOT NULL;

-- Ownership moved from the student to the teacher, so profile_id no
-- longer has a meaning. Dropping it beats leaving it nullable: a column
-- that should always be NULL is a trap for the next developer.
--
-- The V1-era policies from 03_rls.sql still reference this column, and
-- PostgreSQL refuses to drop a column anything depends on — so they go
-- first. (An earlier assumption here was that policy bodies are only
-- validated when executed. They are not: the dependency is real and is
-- checked at DROP time.) V4 writes the replacement policies for the new
-- model.
DROP POLICY IF EXISTS subjects_own ON subjects;
DROP POLICY IF EXISTS attendance_own ON attendance_records;

ALTER TABLE subjects DROP COLUMN IF EXISTS profile_id;
ALTER TABLE subjects DROP CONSTRAINT IF EXISTS subjects_profile_name_unique;

COMMENT ON COLUMN subjects.faculty_id IS
    'The faculty member teaching this offering. NULL means unassigned.';
COMMENT ON COLUMN attendance_records.marked_by IS
    'Who recorded this. Makes a disputed attendance traceable instead of arguable.';
COMMENT ON COLUMN attendance_records.marked_at IS
    'When it was recorded, so a late correction is visible.';


-- ---------------------------------------------------------------------
-- 6. Roster query. Faculty can see only students in the subjects they
--    actually teach — relationship-scoped, not role-scoped.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION subject_roster(p_subject_id uuid)
RETURNS TABLE (
    profile_id uuid,
    full_name  text,
    email      text,
    pct        numeric,
    can_skip   integer,
    must_attend integer,
    recoverable boolean,
    status     text
)
LANGUAGE sql
STABLE
AS $$
    SELECT
        p.id,
        p.full_name,
        p.email,
        h.pct, h.can_skip, h.must_attend, h.recoverable, h.status
    FROM enrollments e
    JOIN profiles p ON p.id = e.profile_id
    -- ORDER MATTERS: attendance_health() reads s.target_attendance, and a
    -- LATERAL join may only reference FROM items that come before it.
    JOIN subjects s ON s.id = e.subject_id
    CROSS JOIN LATERAL (
        SELECT
            COUNT(ar.id)                                     AS total_held,
            COUNT(ar.id) FILTER (WHERE ar.status = 'present') AS attended
        FROM attendance_records ar
        WHERE ar.subject_id = e.subject_id
          AND ar.profile_id = e.profile_id
    ) a
    CROSS JOIN LATERAL attendance_health(
        a.attended::integer, a.total_held::integer,
        s.target_attendance, s.remaining_classes_estimate
    ) h
    WHERE e.subject_id = p_subject_id
    ORDER BY
        CASE h.status
            WHEN 'IMPOSSIBLE' THEN 1
            WHEN 'CRITICAL'   THEN 2
            WHEN 'AT_RISK'    THEN 3
            WHEN 'ON_TRACK'   THEN 4
            ELSE 5
        END,
        p.full_name;
$$;