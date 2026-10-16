-- =====================================================================
-- CampusPilot AI  |  07_state_check.sql
--
-- One query. One run. Returns six rows — one per check.
--
-- Confirms the database is in the expected state after 01-06 have been
-- applied. Doubles as documentation and as a regression check after any
-- future change.
--
-- Read-only. Nothing is created, modified, or deleted.
--
-- ALL SIX ROWS MUST READ 'OK'.
-- =====================================================================

WITH checks AS (

    -- 1. all three tables present
    SELECT 1 AS n, 'tables exist' AS check_name,
           (SELECT string_agg(tablename, ', ' ORDER BY tablename)
              FROM pg_tables
             WHERE schemaname = 'public'
               AND tablename IN ('profiles','subjects','attendance_records')) AS found,
           (SELECT count(*) FROM pg_tables
             WHERE schemaname = 'public'
               AND tablename IN ('profiles','subjects','attendance_records')) = 3 AS ok

    UNION ALL

    -- 2. both functions present with the expected signatures.
    --    A drift here is what caused "function does not exist" earlier.
    SELECT 2, 'functions exist',
           (SELECT string_agg(p.proname || '(' ||
                              pg_get_function_identity_arguments(p.oid) || ')',
                              ' | ' ORDER BY p.proname)
              FROM pg_proc p
              JOIN pg_namespace n ON n.oid = p.pronamespace
             WHERE n.nspname = 'public'
               AND p.proname IN ('attendance_health','subject_attendance_summary')),
           (SELECT count(*) FROM pg_proc p
              JOIN pg_namespace n ON n.oid = p.pronamespace
             WHERE n.nspname = 'public'
               AND p.proname IN ('attendance_health','subject_attendance_summary')) = 2

    UNION ALL

    -- 3. RLS switched on for every student-owned table
    SELECT 3, 'RLS enabled on all 3 tables',
           (SELECT string_agg(tablename, ', ' ORDER BY tablename)
              FROM pg_tables
             WHERE schemaname = 'public'
               AND tablename IN ('profiles','subjects','attendance_records')
               AND rowsecurity),
           (SELECT count(*) FROM pg_tables
             WHERE schemaname = 'public'
               AND tablename IN ('profiles','subjects','attendance_records')
               AND rowsecurity) = 3

    UNION ALL

    -- 4. owner-scoped policies actually attached
    SELECT 4, 'owner policies attached',
           (SELECT string_agg(tablename || '.' || policyname,
                             ' | ' ORDER BY tablename, policyname)
              FROM pg_policies
             WHERE schemaname = 'public'
               AND tablename IN ('profiles','subjects','attendance_records')),
           (SELECT count(*) FROM pg_policies
             WHERE schemaname = 'public'
               AND tablename IN ('profiles','subjects','attendance_records')) >= 4

    UNION ALL

    -- 5. the dashboard view survived
    SELECT 5, 'dashboard view exists',
           (SELECT string_agg(viewname, ', ')
              FROM pg_views
             WHERE schemaname = 'public'
               AND viewname = 'v_student_dashboard'),
           (SELECT count(*) FROM pg_views
             WHERE schemaname = 'public'
               AND viewname = 'v_student_dashboard') = 1

    UNION ALL

    -- 6. demo data present. Adjust the counts here if you reseed.
    SELECT 6, 'demo data loaded',
           (SELECT (SELECT count(*) FROM profiles) || ' profiles, '
                 || (SELECT count(*) FROM subjects) || ' subjects, '
                 || (SELECT count(*) FROM attendance_records) || ' attendance'),
           (SELECT count(*) FROM profiles)   = 1
           AND (SELECT count(*) FROM subjects) = 6
           AND (SELECT count(*) FROM attendance_records) = 100

)

SELECT
    check_name,
    COALESCE(found, '(none found)') AS found,
    CASE WHEN ok THEN 'OK' ELSE 'PROBLEM' END AS result
FROM checks
ORDER BY n;