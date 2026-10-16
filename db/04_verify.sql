-- =====================================================================
-- CampusPilot AI  |  Step 1  |  04_verify.sql
--
-- A test suite that checks itself and prints PASS or FAIL.
--
-- This is what "reliable" means in practice. Not "no bugs" — it means
-- when you change the function next month, this query tells you whether
-- you broke something.
--
-- HOW TO RUN: paste the whole thing and run once.
-- All seven rows must read PASS. Nothing is stored; this is pure check.
-- =====================================================================

WITH cases (label, attended, total, target, future,
            exp_pct, exp_skip, exp_must, exp_status) AS (
    VALUES
      -- no records at all: must not divide by zero
      ('no data',            0::int,  0::int, 75::numeric,  0::int,
                            NULL::numeric,  0,  0, 'NO_DATA'),

      -- sitting exactly on 75%: zero skip allowance, needs 8 of next 10
      ('exactly on target', 60, 80, 75, 10,
                            75.00,  0,  8, 'ON_TRACK'),

      -- 62.5% with 20 classes left: even perfect attendance cannot reach 75%
      ('unrecoverable',     50, 80, 75, 20,
                            62.50,  0, 25, 'IMPOSSIBLE'),

      -- 87.5%: ahead, may skip 13
      ('ahead of target',   70, 80, 75, 10,
                            87.50, 13,  0, 'ON_TRACK'),

      -- 77.78%: may skip 3, but must still attend 2 of the next 5
      ('slightly over',     70, 90, 75,  5,
                            77.78,  3,  2, 'ON_TRACK'),

      -- 73.68% and falling behind
      ('at risk',           70, 95, 75, 10,
                            73.68,  0,  9, 'AT_RISK'),

      -- 60% but 100 classes remain, so recovery is still possible
      ('critical',          60, 100, 75, 100,
                            60.00,  0, 90, 'CRITICAL')
),

actual AS (
    SELECT
        c.label,
        c.exp_pct, c.exp_skip, c.exp_must, c.exp_status,
        h.pct, h.can_skip, h.must_attend, h.status, h.recoverable
    FROM cases c
    CROSS JOIN LATERAL attendance_health(c.attended, c.total, c.target, c.future) h
)

SELECT
    label,
    exp_pct                                   AS expected_pct,
    pct                                       AS actual_pct,
    exp_skip                                  AS expected_skip,
    can_skip                                  AS actual_skip,
    exp_must                                  AS expected_must,
    must_attend                               AS actual_must,
    exp_status                                AS expected_status,
    status                                    AS actual_status,
    CASE WHEN pct        IS NOT DISTINCT FROM exp_pct
          AND can_skip        = exp_skip
          AND must_attend     = exp_must
          AND status          = exp_status
         THEN 'PASS' ELSE 'FAIL' END          AS result
FROM actual
ORDER BY result, label;


-- =====================================================================
-- All PASS expected.
--
-- If 'unrecoverable' ever reads PASS with recoverable = true, the
-- IMPOSSIBLE branch is broken — that is the branch that matters most,
-- because it is the one that stops the app lying to a failing student.
-- =====================================================================