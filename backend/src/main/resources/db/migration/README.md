# Flyway migrations

## Why this folder is currently empty

The Step 1 schema was applied by hand through the Supabase SQL Editor
before Flyway existed. Those files still live in `../../db/` and remain
the readable source of truth.

`application.yml` sets `baseline-on-migrate: true` with
`baseline-version: 1`, which tells Flyway: *"this database already has a
schema, adopt it, and manage everything from version 1 onward."* Without
that line, Flyway would try to `CREATE TABLE profiles` on a table that
already exists and the application would refuse to start.

## From now on

Every schema change goes here as a numbered file, oldest first:

```
V2__add_assignments_table.sql
V3__add_placement_attempts.sql
V4__create_assignments_index.sql
```

Flyway runs them in order exactly once each, inside a transaction, and
records them in its `flyway_schema_history` table. That table is how you
know what has been applied to any environment, which is exactly the
question you get asked when deploying.

## The rule that keeps this consistent

- **Never edit a migration that has already run.** It will not re-run, so
  the edit silently does nothing on every environment except a fresh
  database.
- To change something, write a *new* migration.
- Schema files in `../../db/` are never edited either, for the same
  reason. They are the historical record of how Step 1 was built.