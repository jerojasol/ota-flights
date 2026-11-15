# E1 — 1M-row seed and keyset vs offset measurement

This produces the numbers for `../docs/SOLUTION.md` §4.3. Read `infrastructure/src/main/resources/db/migration/V1__create_itineraries.sql` and `prompts/phase-d-infrastructure/02-persistence.md` (perf IT) first.

## Task

### 1. Seed script — `flight-aggregator/scripts/seed-1m.sql`
Pure SQL, idempotent (`TRUNCATE` first, behind a comment warning). Generate 1,000,000 rows with `generate_series`:
- ~50 routes drawn from a fixed list of 15 airports, 30 departure dates, random-but-deterministic prices (`setseed(0.42)`), 1–3 segments as JSONB built with `jsonb_build_object`, `itinerary_key` = `encode(sha256(...), 'hex')` of a deterministic string so the unique constraint holds.
- Run `ANALYZE itineraries` at the end.

### 2. Runner — `flight-aggregator/scripts/measure-pagination.sh`
Uses `psql` against the compose Postgres (`DB_URL` env). Runs, each 5 times and prints median:
- **Keyset, page 1**: route filter + `ORDER BY final_price, id LIMIT 20`.
- **Keyset, deep page**: same with a cursor taken from row ~900,000 of that route's ordering.
- **Offset, page 1**: `LIMIT 20 OFFSET 0`.
- **Offset, deep page**: `LIMIT 20 OFFSET 900000` (or the max available for the route; use the global ordering without route filter if routes are too small — say so in the output).
Prints `EXPLAIN (ANALYZE, BUFFERS)` summary lines (plan node used, actual time, shared hits) for the two deep-page queries.

### 3. Record
Append the results as a Markdown table to `../docs/SOLUTION.md` §4.3 under a "Measurements" heading with the machine description (CPU, Docker memory) and the exact commands. Keep the raw `EXPLAIN` output in `flight-aggregator/scripts/results/` (gitignored? — no, commit it; it is evidence).

## Acceptance

- `docker compose up -d postgres` + `psql -f scripts/seed-1m.sql` completes in a few minutes.
- `scripts/measure-pagination.sh` prints the table; keyset deep page uses `ix_itineraries_search` with an index-scan node; offset deep page shows a large `rows removed`/high actual time.

## Closing

`/log-ai-decision`, show diff summary.
