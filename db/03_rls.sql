-- =====================================================================
-- CampusPilot AI  |  Step 1  |  03_rls.sql
--
-- Row Level Security: the database itself decides who may read and write
-- each row, regardless of what the frontend asks for.
--
-- Why this matters: "hiding the delete button in React" is not security.
-- Anyone can open DevTools and call the API directly. These policies
-- cannot be bypassed from the browser at all.
-- =====================================================================

ALTER TABLE profiles          ENABLE ROW LEVEL SECURITY;
ALTER TABLE subjects          ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance_records ENABLE ROW LEVEL SECURITY;


-- ---------------------------------------------------------------------
-- profiles
-- ---------------------------------------------------------------------
DROP POLICY IF EXISTS profiles_select_own ON profiles;
CREATE POLICY profiles_select_own ON profiles
    FOR SELECT
    USING (auth_user_id = auth.uid());

DROP POLICY IF EXISTS profiles_insert_own ON profiles;
CREATE POLICY profiles_insert_own ON profiles
    FOR INSERT
    WITH CHECK (auth_user_id = auth.uid());

DROP POLICY IF EXISTS profiles_update_own ON profiles;
CREATE POLICY profiles_update_own ON profiles
    FOR UPDATE
    USING (auth_user_id = auth.uid())
    WITH CHECK (auth_user_id = auth.uid());


-- ---------------------------------------------------------------------
-- subjects : you may only touch subjects belonging to your profile
-- ---------------------------------------------------------------------
DROP POLICY IF EXISTS subjects_own ON subjects;
CREATE POLICY subjects_own ON subjects
    FOR ALL
    USING (
        profile_id IN (
            SELECT id FROM profiles WHERE auth_user_id = auth.uid()
        )
    )
    WITH CHECK (
        profile_id IN (
            SELECT id FROM profiles WHERE auth_user_id = auth.uid()
        )
    );


-- ---------------------------------------------------------------------
-- attendance_records : reachable only through a subject you own
-- ---------------------------------------------------------------------
DROP POLICY IF EXISTS attendance_own ON attendance_records;
CREATE POLICY attendance_own ON attendance_records
    FOR ALL
    USING (
        subject_id IN (
            SELECT s.id
            FROM subjects s
            JOIN profiles p ON p.id = s.profile_id
            WHERE p.auth_user_id = auth.uid()
        )
    )
    WITH CHECK (
        subject_id IN (
            SELECT s.id
            FROM subjects s
            JOIN profiles p ON p.id = s.profile_id
            WHERE p.auth_user_id = auth.uid()
        )
    );


-- ---------------------------------------------------------------------
-- Views run with the definer's rights by default, which would BYPASS the
-- policies above. This makes the view run as the calling user instead.
-- ---------------------------------------------------------------------
ALTER VIEW v_student_dashboard SET (security_invoker = true);


-- =====================================================================
-- Verification — run these and read the Expected column.
-- =====================================================================
--
-- 1. Confirm RLS is on. Expect three 'Row Level Security enabled'.
--
--  SELECT tablename, rowsecurity FROM pg_tables WHERE schemaname='public'
--   AND tablename IN ('profiles','subjects','attendance_records');
--
-- 2. Confirm attendance_health handles the edge cases.
--    Run each separately:
--
--  SELECT * FROM attendance_health(0, 0, 75, 0);
--    Expected: pct NULL, status NO_DATA
--
--  SELECT * FROM attendance_health(60, 80, 75, 10);
--    Expected: pct 75.00, can_skip 0, must_attend 8, recoverable true, ON_TRACK
--
--  SELECT * FROM attendance_health(50, 80, 75, 20);
--    Expected: pct 62.50, must_attend 25, recoverable FALSE, IMPOSSIBLE
--
--  SELECT * FROM attendance_health(70, 80, 75, 10);
--    Expected: pct 87.50, can_skip 13, must_attend 0, ON_TRACK
--
--  SELECT * FROM attendance_health(70, 90, 75, 5);
--    Expected: pct 77.78, can_skip 3, must_attend 2, ON_TRACK
--
-- 3. Confirm the bad-data guard fires.
--
--  SELECT * FROM attendance_health(80, 60, 75, 0);
--    Expected: ERROR - "Attended cannot exceed total"
--
-- 4. Confirm constraints reject bad data.
--
--  INSERT INTO subjects (profile_id, name, semester, target_attendance)
--  VALUES (gen_random_uuid(), '', 1, 75);
--    Expected: ERROR - violates check constraint on name
--
-- 5. Confirm duplicates are impossible.
--
--  -- add a subject, then try adding the same name twice for that profile
--    Expected: ERROR - violates unique constraint
-- =====================================================================