-- 1,000,000-row seed for the keyset vs. offset measurement (SOLUTION.md §4.3, repository root).
--
-- WARNING: this TRUNCATEs the itineraries table. Run it only against a disposable database
-- (e.g. the compose Postgres), never against data you want to keep.
--
-- Shape: 200,000 rows on one synthetic deep route (ZZA-ZZB, 2026-12-01); the other 800,000 over
-- 50 origin/destination pairs of 15 airports x 30 departure dates (~533 rows per pair and date),
-- 1-3 segments, deterministic prices (setseed) and deterministic itinerary keys (sha256 of the row number).
--
--   docker compose up -d postgres
--   docker exec -i ota-flights-postgres-1 psql -U flights -d flights < flight-aggregator/scripts/seed-1m.sql

\timing on

TRUNCATE itineraries RESTART IDENTITY;

SELECT setseed(0.42);

WITH airports AS (
    SELECT ARRAY['MAD','BCN','LHR','CDG','FRA','AMS','FCO','LIS','MUC','ZRH','VIE','DUB','CPH','OSL','ARN'] AS a
),
pairs AS (
    -- (i, i+k mod 15) for k = 1..4 gives 60 distinct pairs; keep the first 50.
    SELECT row_number() OVER (ORDER BY k, i) - 1 AS route_no,
           (SELECT a[i + 1] FROM airports) AS origin,
           (SELECT a[((i + k) % 15) + 1] FROM airports) AS destination
    FROM generate_series(1, 4) k, generate_series(0, 14) i
),
carriers AS (
    SELECT ARRAY['IB','VY','BA','AF','LH','KL','AZ','TP','LX','OS','EI','SK','UX'] AS c
),
base AS (
    -- Rows 0..199,999 form one synthetic deep route (ZZA-ZZB, 2026-12-01; artificial codes, not real airports)
    -- so that keyset and offset can be compared at depth on the same filtered, indexed query.
    SELECT g,
           CASE WHEN g < 200000 THEN 'ZZA' ELSE p.origin END      AS origin,
           CASE WHEN g < 200000 THEN 'ZZB' ELSE p.destination END AS destination,
           CASE WHEN g < 200000 THEN DATE '2026-12-01'
                ELSE DATE '2026-12-01' + ((g / 50) % 30)::int END AS departure_date,
           1 + (g % 3)                               AS n_segments,
           (SELECT c[1 + (g % 13)] FROM carriers)     AS carrier,
           round((30 + random() * 970)::numeric, 2)   AS base_price,
           (g % 1440)                                 AS dep_minute
    FROM generate_series(0, 999999) g
    JOIN pairs p ON p.route_no = g % 50
)
INSERT INTO itineraries (itinerary_key, origin, destination, departure_date, first_carrier, carriers,
                         base_price, final_price, currency, source, segments, ingested_at)
SELECT encode(sha256(convert_to('seed-1m|' || g, 'UTF8')), 'hex'),
       origin,
       destination,
       departure_date,
       carrier,
       ARRAY[carrier]::char(2)[],
       base_price,
       round(base_price * 1.05, 2),
       'EUR',
       (ARRAY['ALPHA','BETA','GAMMA'])[1 + (g % 3)],
       (SELECT jsonb_agg(jsonb_build_object(
                   'carrier', carrier,
                   'flightNumber', carrier || (100 + (g % 900) + s)::text,
                   'origin', CASE WHEN s = 1 THEN origin ELSE 'HUB' || s END,
                   'destination', CASE WHEN s = n_segments THEN destination ELSE 'HUB' || (s + 1) END,
                   'departure', (departure_date + make_interval(mins => dep_minute + (s - 1) * 180))::timestamp,
                   'arrival',   (departure_date + make_interval(mins => dep_minute + (s - 1) * 180 + 120))::timestamp)
                 ORDER BY s)
        FROM generate_series(1, n_segments) s),
       now()
FROM base;

ANALYZE itineraries;

SELECT count(*) AS rows, count(DISTINCT (origin, destination)) AS pairs,
       count(DISTINCT departure_date) AS dates,
       count(*) FILTER (WHERE origin = 'ZZA' AND destination = 'ZZB') AS deep_route_rows
FROM itineraries;
