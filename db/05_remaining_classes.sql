-- =====================================================================
-- CampusPilot AI  |  Step 1  |  05_remaining_classes.sql
--
-- Fixes a real design bug in v_student_dashboard.
--
-- THE BUG
--   The view passed 0 for p_future ("classes still to come"), so
--   recoverable = (classes_needed <= 0). That made the flag mean only
--   "am I already at target?" — so every student below target would
--   have been shown IMPOSSIBLE, which is both useless and alarming.
--
-- THE FIX
--   Store an explicit estimate of how many classes remain this
--   semester. Now IMPOSSIBLE means something real: "even if you attend
--   every remaining class, the target cannot be reached."
--
-- WHY AN ESTIMATE IS HONEST
--   A university usually publishes a fixed number of teaching weeks.
--   Rather than guessing, we store the number and let the student edit
--   it. It is a configurable assumption, not a hidden one.
-- =====================================================================

ALTER TABLE subjects
    ADD COLUMN IF NOT EXISTS remaining_classes_estimate integer NOT NULL DEFAULT 20
    CHECK (remaining_classes_estimate BETWEEN 0 AND 200);

COMMENT ON COLUMN subjects.remaining_classes_estimate IS
    'Estimated classes remaining this semester. Drives the recoverable flag.';


-- Rebuild the view so it projects forward using the stored estimate.
--
-- NOTE: CREATE OR REPLACE VIEW cannot add, remove, or reorder columns --
-- it may only change the expression behind an existing column. We
-- inserted remaining_classes_estimate in the middle of the list, which
-- shifted every column after it and made Postgres complain
-- (42P16). Dropping and recreating is the correct fix.
DROP VIEW IF EXISTS v_student_dashboard;

CREATE VIEW v_student_dashboard AS
SELECT
    s.id            AS subject_id,
    s.name          AS subject_name,
    s.code          AS subject_code,
    s.semester      AS semester,
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
    (SELECT max(held_on) FROM attendance_records WHERE subject_id = s.id) AS last_class
FROM subjects s
LEFT JOIN LATERAL (
    SELECT
        COUNT(*)                                   AS total_held,
        COUNT(*) FILTER (WHERE status = 'present') AS attended
    FROM attendance_records
    WHERE subject_id = s.id
) a ON true
CROSS JOIN LATERAL attendance_health(
    COALESCE(a.attended, 0)::integer,
    COALESCE(a.total_held, 0)::integer,
    s.target_attendance,
    s.remaining_classes_estimate
) h;

COMMENT ON VIEW v_student_dashboard IS
    'Dashboard feed. Projects forward using remaining_classes_estimate, so recoverable means "can still reach target by attending everything left".';