-- =====================================================================
-- CampusPilot AI  |  Step 1  |  06_seed_demo.sql
--
-- Demo data covering all five status codes at once, so the dashboard
-- shows every state the app can produce.
--
--   DBMS                  -> ON_TRACK    (90%,  may skip 4)
--   Operating Systems     -> ON_TRACK    (75%,  may skip 0)
--   Computer Networks     -> AT_RISK     (70%,  must attend 16 of next 20)
--   Discrete Mathematics  -> CRITICAL    (65%,  must attend 17 of next 20)
--   Digital Electronics   -> IMPOSSIBLE  (40%,  needs 22 but only 20 exist)
--   Artificial Intelligence-> NO_DATA     (no classes recorded yet)
--
-- Safe to run as many times as you like: it deletes the demo profile
-- first, and ON DELETE CASCADE removes its subjects and records.
-- =====================================================================

BEGIN;

-- One delete, because subjects and attendance_records both cascade.
DELETE FROM profiles WHERE full_name = 'Demo Student';


-- The person
INSERT INTO profiles (full_name, email, role)
VALUES ('Demo Student', 'demo@campuspilot.dev', 'student');


-- Subjects. remaining_classes_estimate drives the "recoverable" flag.
INSERT INTO subjects (profile_id, name, code, semester, target_attendance, remaining_classes_estimate)
SELECT p.id, v.name, v.code, 5, 75.00, v.remaining
FROM profiles p
CROSS JOIN (VALUES
    ('DBMS',                  'CS501', 20),
    ('Operating Systems',     'CS502', 20),
    ('Computer Networks',     'CS503', 20),
    ('Discrete Mathematics',  'MA504', 20),
    ('Digital Electronics',   'EC505', 20),
    ('Artificial Intelligence','CS506', 20)
) AS v(name, code, remaining)
WHERE p.full_name = 'Demo Student';


-- Attendance: 20 classes each, first N present.
-- generate_series avoids writing 100 rows by hand.
INSERT INTO attendance_records (subject_id, held_on, status)
SELECT
    s.id,
    (DATE '2026-08-03' + (g - 1))::date AS held_on,
    CASE WHEN g <= plan.present THEN 'present' ELSE 'absent' END::text
FROM subjects s
CROSS JOIN (VALUES
    ('DBMS',                 18),
    ('Operating Systems',    15),
    ('Computer Networks',    14),
    ('Discrete Mathematics', 13),
    ('Digital Electronics',   8)
) AS plan(subject_name, present)
CROSS JOIN generate_series(1, 20) AS g
WHERE s.name = plan.subject_name
  AND s.code IN ('CS501','CS502','CS503','MA504','EC505');

COMMIT;


-- =====================================================================
-- THE PAYOFF : all five states in one query.
-- Scroll right to see may_skip, must_take, recoverable and status.
-- =====================================================================
SELECT
    subject_name,
    total_held || ' held, ' || attended || ' attended'  AS record,
    COALESCE(pct::text, 'no data')                       AS pct,
    can_skip                                             AS may_skip,
    must_attend || ' of ' || remaining_classes_estimate AS must_take,
    recoverable                                          AS recoverable,
    status
FROM v_student_dashboard
ORDER BY
    CASE status
        WHEN 'IMPOSSIBLE' THEN 1
        WHEN 'CRITICAL'   THEN 2
        WHEN 'AT_RISK'    THEN 3
        WHEN 'ON_TRACK'   THEN 4
        ELSE 5
    END,
    subject_name;