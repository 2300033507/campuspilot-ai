-- =====================================================================
-- V5__campus_map_locations.sql
--
-- Adds campus wayfinding WITHOUT touching the attendance model.
--
-- WHY OPENSTREETMAP AND NOT GOOGLE MAPS
--   The Google Maps JavaScript API costs roughly $7 per 1,000 loads and
--   requires a billing card even to stay on the free tier. OpenStreetMap
--   and OSRM are genuinely free, need no key, and need no card. That
--   matters for a student project: no surprise invoice, and no card
--   details sitting in a repository.
--
-- WHAT THIS ADDS
--   One table of campus landmarks. Everything else is rendering and
--   routing on the client, plus a thin proxy to OSRM.
--
-- ATTENDANCE IS UNAFFECTED
--   Nothing here reads or writes attendance. This is an additive change.
-- =====================================================================


CREATE TABLE IF NOT EXISTS campus_locations (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name        text NOT NULL
                CHECK (length(trim(name)) > 0),
    description text,

    -- Fixed vocabulary rather than free text, so the client can group
    -- and filter reliably without guessing at spelling.
    category    text NOT NULL DEFAULT 'other'
                CHECK (category IN (
                    'academic',    -- labs, lecture halls, departments
                    'residential', -- hostels, dormitories
                    'food',        -- canteens, mess
                    'sport',       -- grounds, gym
                    'admin',       -- registrar, office
                    'service',     -- library, medical, transport
                    'other'
                )),

    building    text,
    latitude    numeric(10, 7) NOT NULL
                CHECK (latitude  BETWEEN  -90 AND  90),
    longitude   numeric(10, 7) NOT NULL
                CHECK (longitude BETWEEN -180 AND 180),
    created_at  timestamptz NOT NULL DEFAULT now()
);

-- The map loads every location at once, sorted for stable marker order.
CREATE INDEX IF NOT EXISTS idx_campus_locations_category ON campus_locations(category);


COMMENT ON TABLE campus_locations IS
    'Campus landmarks shown on the navigation map. Seeded with DEMO data — replace with real coordinates.';
COMMENT ON COLUMN campus_locations.latitude IS
    'WGS84 decimal degrees, which is what OpenStreetMap and OSRM both expect.';


-- ---------------------------------------------------------------------
-- Demo landmarks.
--
-- THESE ARE PLACEHOLDERS around KL University, Guntur. They exist so the
-- feature is demonstrable immediately. Replace them with real coordinates
-- before treating any of this as useful on the ground.
--
-- To replace them all:
--   DELETE FROM campus_locations;
--   INSERT INTO campus_locations (name, category, building, latitude, longitude)
--   VALUES ('Main Library', 'service', 'Block A', 16.306900, 80.436500);
-- ---------------------------------------------------------------------
INSERT INTO campus_locations (name, description, category, building, latitude, longitude) VALUES
    ('Main Library',          'Central library and reading halls',        'service',     'Block A',    16.306900, 80.436500),
    ('Science Lab',           'Physics and chemistry laboratories',     'academic',    'Block C',    16.307400, 80.437100),
    ('Cafeteria',             'Main canteen',                            'food',        'Central',    16.306500, 80.436900),
    ('Boys Hostel',           'Residential block',                      'residential', 'North Wing', 16.307800, 80.436200),
    ('Girls Hostel',          'Residential block',                      'residential', 'South Wing', 16.306200, 80.436100),
    ('Sports Complex',        'Track, courts and indoor stadium',        'sport',       'East Block', 16.307100, 80.438000),
    ('Admin Block',           'Registrar and examination office',        'admin',       'Block D',    16.306700, 80.436300),
    ('Lecture Hall Complex',  'Seminar and lecture rooms',               'academic',    'Block B',    16.307200, 80.436700)
ON CONFLICT DO NOTHING;