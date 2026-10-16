-- =====================================================================
-- V2__add_password_hash.sql
--
-- First real Flyway migration. The Step 1 schema was applied by hand and
-- adopted via baseline-on-migrate at version 1, so this runs as V2.
--
-- WHY A MIGRATION AND NOT AN EDIT TO 01_schema.sql
--   01 has already run against this database. Editing it would do
--   nothing here, and would create a schema that only exists on a fresh
--   install. Migrations are the append-only record of change.
--
-- NULLABLE ON PURPOSE
--   The demo profile created in Step 1 has no password and must keep
--   working until one is set. New registrations will always supply one,
--   enforced by a NOT NULL check on the auth service path.
--
-- HOW THE HASH IS COMPARED
--   Spring Security's BCryptPasswordEncoder produces the standard
--   modular crypt format ($2a$...), which is identical to what
--   PostgreSQL's pgcrypto extension produces with crypt(). That means we
--   can set a password for the existing demo profile directly in SQL:
--
--     UPDATE profiles
--        SET password_hash = crypt('yourpassword', gen_salt('bf', 10))
--      WHERE full_name = 'Demo Student';
-- =====================================================================

ALTER TABLE profiles
    ADD COLUMN IF NOT EXISTS password_hash text;

COMMENT ON COLUMN profiles.password_hash IS
    'BCrypt hash. NULL means the account has never set a password and cannot log in via the API.';