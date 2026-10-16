-- =====================================================================
-- CampusPilot AI  |  Step 1  |  02_logic.sql
--
-- THIS IS THE IMPORTANT FILE.
--
-- Every number the app shows is computed HERE, in the database.
-- The browser never does arithmetic. It only displays what these
-- functions return. That is the whole point of this step.
--
-- If an interviewer asks "where is the calculation done?" the answer is
-- "inside Postgres, in an immutable function, and the client cannot
--  influence the result" — that is a real answer.
-- =====================================================================


-- ---------------------------------------------------------------------
-- attendance_health()
--
-- Given how many classes you attended, how many were held, what your
-- target is, and how many classes are coming up, decide whether you
-- are safe — and say exactly why.
--
-- Four cases, and the fourth is the one everybody forgets:
--
--   1. No records at all        -> return NULL pct, never divide by zero
--   2. On track                 -> "you may skip N classes"
--   3. Behind but recoverable   -> "you must attend Y of the next Z"
--   4. Already mathematically lost -> "no attendance pattern can fix this"
--
-- Case 4 is why this is worth a function. If a student needs 74% and has
-- 50/80 (62.5%), then even attending all 20 remaining classes only gets
-- them to 70/100 = 70%. No effort reaches 74%. The app must say so
-- instead of showing a hopeful number forever.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION attendance_health(
    p_attended  integer,
    p_total     integer,
    p_target    numeric DEFAULT 75.00,
    p_future    integer DEFAULT 0
)
RETURNS TABLE (
    pct            numeric,    -- current percentage, NULL if no data
    can_skip       integer,    -- classes you may still miss
    must_attend    integer,    -- classes you must attend, out of p_future
    recoverable    boolean,    -- can the target still be reached at all?
    status         text        -- NO_DATA | ON_TRACK | AT_RISK | CRITICAL | IMPOSSIBLE
)
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
    v_pct       numeric;
    v_skip      integer := 0;
    v_must      integer := 0;
    v_needed    numeric  := 0;   -- classes required out of the next p_future
    v_possible  boolean  := true;
    v_status    text;
BEGIN
    -- Guard: a target of 0 would divide by zero below.
    IF p_target IS NULL OR p_target <= 0 THEN
        p_target := 75.00;
    END IF;

    -- Guard: negative or impossible inputs.
    IF p_attended < 0 OR p_total < 0 OR p_future < 0 THEN
        RAISE EXCEPTION 'Counts cannot be negative (attended=%, total=%, future=%)',
            p_attended, p_total, p_future;
    END IF;

    -- An attendance record of 80 present out of 60 total is corrupt data.
    IF p_attended > p_total THEN
        RAISE EXCEPTION 'Attended (%) cannot exceed total (%)', p_attended, p_total;
    END IF;

    -- CASE 1 : nothing recorded yet.
    IF p_total = 0 THEN
        RETURN QUERY SELECT NULL::numeric, 0, 0, false, 'NO_DATA';
        RETURN;
    END IF;

    v_pct := round(p_attended * 100.0 / p_total, 2);

    -- How many MORE classes can I miss?
    --   Need  A / (T + x) >= G / 100
    --   => 100A >= G(T + x)
    --   =>  x <= (100A / G) - T
    v_skip := GREATEST(0, floor(100.0 * p_attended / p_target - p_total)::integer);

    -- How many must I attend, out of the next p_future classes?
    --   Attend y of them:  (A + y) / (T + F) >= G / 100
    --   => y >= (G(T + F) - 100A) / 100
    v_needed := (p_target * (p_total + p_future) - 100.0 * p_attended) / 100.0;
    v_must   := GREATEST(0, ceil(v_needed)::integer);

    -- Reachable only if attending every upcoming class is enough.
    v_possible := (v_needed <= p_future);

    -- Classify.
    IF NOT v_possible THEN
        v_status := 'IMPOSSIBLE';
    ELSIF v_pct >= p_target THEN
        v_status := 'ON_TRACK';
    ELSIF v_pct >= (p_target * 0.90) THEN
        v_status := 'AT_RISK';
    ELSE
        v_status := 'CRITICAL';
    END IF;

    RETURN QUERY SELECT v_pct, v_skip, v_must, v_possible, v_status;
END;
$$;

COMMENT ON FUNCTION attendance_health(integer, integer, numeric, integer)
    IS 'Returns current percentage, classes that may be skipped, classes that must be attended, whether the target is still reachable, and a status code. Handles zero-data and unrecoverable cases.';


-- ---------------------------------------------------------------------
-- subject_attendance_summary()
--
-- Rolls up every attendance record for one subject and feeds the rows
-- through attendance_health(). This is the function the backend calls.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION subject_attendance_summary(p_subject_id uuid)
RETURNS TABLE (
    subject_id      uuid,
    subject_name    text,
    target_attendance numeric,
    total_held      integer,
    attended        integer,
    pct             numeric,
    can_skip        integer,
    must_attend     integer,
    recoverable     boolean,
    status          text
)
LANGUAGE sql
STABLE
AS $$
    SELECT
        s.id,
        s.name,
        s.target_attendance,
        COALESCE(a.total_held, 0)::integer,
        COALESCE(a.attended, 0)::integer,
        h.pct,
        h.can_skip,
        h.must_attend,
        h.recoverable,
        h.status
    FROM subjects s
    -- ORDER MATTERS: the aggregate must be computed before attendance_health()
    -- can read from it, because LATERAL may only reference preceding FROM items.
    LEFT JOIN LATERAL (
        SELECT
            COUNT(*)                                   AS total_held,
            COUNT(*) FILTER (WHERE status = 'present') AS attended
        FROM attendance_records
        WHERE subject_id = s.id
    ) a ON true
    CROSS JOIN LATERAL attendance_health(
        COALESCE(a.attended, 0),
        COALESCE(a.total_held, 0),
        s.target_attendance,
        0            -- pass a non-zero value here to project forward
    ) h
    WHERE s.id = p_subject_id;
$$;

COMMENT ON FUNCTION subject_attendance_summary(uuid)
    IS 'Per-subject attendance rollup with health analysis. Replace the final 0 with p_future to project forward.';


-- ---------------------------------------------------------------------
-- v_student_dashboard : one query for the whole dashboard.
--
-- Shows CROSS JOIN LATERAL, which is worth understanding — it lets us
-- call a per-row function and attach its output to that same row.
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_student_dashboard AS
SELECT
    s.id            AS subject_id,
    s.name          AS subject_name,
    s.code          AS subject_code,
    s.semester      AS semester,
    s.target_attendance,
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
    -- ::integer is required: COUNT() returns bigint, and Postgres will not
    -- silently narrow bigint down to the function's integer parameters.
    COALESCE(a.attended, 0)::integer,
    COALESCE(a.total_held, 0)::integer,
    s.target_attendance,
    0
) h;

COMMENT ON VIEW v_student_dashboard IS 'Dashboard feed: subject, attendance counts, health metrics, status code.';


-- =====================================================================
-- DONE. Run 03_rls.sql next.
-- =====================================================================