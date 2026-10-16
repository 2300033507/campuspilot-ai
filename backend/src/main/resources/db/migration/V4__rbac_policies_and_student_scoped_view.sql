-- =====================================================================
-- V4__rbac_policies_and_student_scoped_view.sql
--
-- Two jobs:
--   1. Replace the RLS policies to match the new model, and add rules
--      that reflect WHO MAY WRITE. A student reading their own attendance
--      is fine; a student writing it is not.
--   2. Rebuild the dashboard view so it is scoped per student.
--
-- NOTE ON WHO ENFORCES THIS
--   Spring Boot connects as the table owner, so PostgreSQL RLS is
--   BYPASSED for the API. The real enforcement is Spring Security plus
--   scoped queries in the service layer. These policies are still
--   correct and still applied to any direct Data API access with the
--   anon key — but do not claim to the interviewer that RLS is what
--   stops a student from marking their own attendance, because it is not.
--   Say: "RLS protects the public API surface; Spring Security enforces
--   the role rules for our service connection."
-- =====================================================================


-- ---------------------------------------------------------------------
-- enrollments: a student sees their own; faculty sees who is in their
-- own subjects.
-- ---------------------------------------------------------------------
ALTER TABLE enrollments ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS enrollments_student_own ON enrollments;
CREATE POLICY enrollments_student_own ON enrollments
    FOR SELECT
    USING (
        profile_id IN (SELECT id FROM profiles WHERE auth_user_id = auth.uid())
        OR EXISTS (
            SELECT 1 FROM subjects s
            JOIN profiles f ON f.id = s.faculty_id
            WHERE s.id = enrollments.subject_id
              AND f.auth_user_id = auth.uid()
        )
    );


-- ---------------------------------------------------------------------
-- attendance_records: students READ their own. Only faculty of the
-- subject (or an admin) may WRITE.
-- ---------------------------------------------------------------------
ALTER TABLE attendance_records ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS attendance_student_read ON attendance_records;
CREATE POLICY attendance_student_read ON attendance_records
    FOR SELECT
    USING (
        -- A student reads their own records.
        profile_id IN (SELECT id FROM profiles WHERE auth_user_id = auth.uid())
        -- A teacher reads records for any subject they teach.
        OR EXISTS (
            SELECT 1
            FROM subjects s
            JOIN profiles f ON f.id = s.faculty_id
            WHERE s.id = attendance_records.subject_id
              AND f.auth_user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS attendance_faculty_write ON attendance_records;
CREATE POLICY attendance_faculty_write ON attendance_records
    FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM subjects s
            JOIN profiles f ON f.id = s.faculty_id
            WHERE s.id = attendance_records.subject_id
              AND f.auth_user_id = auth.uid()
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM subjects s
            JOIN profiles f ON f.id = s.faculty_id
            WHERE s.id = attendance_records.subject_id
              AND f.auth_user_id = auth.uid()
        )
    );


-- ---------------------------------------------------------------------
-- subjects: students see subjects they are enrolled in. They cannot
-- create or alter them.
-- ---------------------------------------------------------------------
ALTER TABLE subjects ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS subjects_student_read ON subjects;
CREATE POLICY subjects_student_read ON subjects
    FOR SELECT
    USING (
        id IN (SELECT subject_id FROM enrollments
                WHERE profile_id IN (
                    SELECT id FROM profiles WHERE auth_user_id = auth.uid()))
        OR EXISTS (
            SELECT 1 FROM subjects s2
            JOIN profiles f ON f.id = s2.faculty_id
            WHERE s2.id = subjects.id AND f.auth_user_id = auth.uid()
        )
    );


-- ---------------------------------------------------------------------
-- Dashboard view, now carrying profile_id so one query serves any
-- student. Replaces v_student_dashboard.
-- ---------------------------------------------------------------------
DROP VIEW IF EXISTS v_student_dashboard;
DROP VIEW IF EXISTS v_attendance_health;

CREATE VIEW v_attendance_health AS
SELECT
    e.profile_id,
    s.id            AS subject_id,
    s.name          AS subject_name,
    s.code          AS subject_code,
    s.semester      AS semester,
    s.faculty_id,
    s.target_attendance,
    s.remaining_classes_estimate,
    COALESCE(a.total_held, 0) AS total_held,
    COALESCE(a.attended, 0)   AS attended,
    COALESCE(a.total_held, 0) - COALESCE(a.attended, 0) AS missed,
    h.pct,
    h.can_skip,
    h.must_attend,
    h.recoverable,
    h.status,
    (SELECT max(ar.held_on) FROM attendance_records ar
      WHERE ar.subject_id = s.id AND ar.profile_id = e.profile_id) AS last_class
FROM enrollments e
JOIN subjects s ON s.id = e.subject_id
LEFT JOIN LATERAL (
    SELECT
        COUNT(*)                                   AS total_held,
        COUNT(*) FILTER (WHERE ar.status = 'present') AS attended
    FROM attendance_records ar
    WHERE ar.subject_id = s.id
      AND ar.profile_id = e.profile_id
) a ON true
CROSS JOIN LATERAL attendance_health(
    COALESCE(a.attended, 0)::integer,
    COALESCE(a.total_held, 0)::integer,
    s.target_attendance,
    s.remaining_classes_estimate
) h;

-- Runs as the CALLER, so RLS above applies. Without this the view would
-- run with elevated rights and ignore every policy.
ALTER VIEW v_attendance_health SET (security_invoker = true);

COMMENT ON VIEW v_attendance_health IS
    'Per-student, per-subject attendance health. Carries profile_id so one query serves any student; the API always filters by the caller.';