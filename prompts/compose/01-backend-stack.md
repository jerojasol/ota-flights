# F1 — Root Docker Compose: database, cache, mock providers, aggregator

Run from the repository root (`ota-flights/`). Read `AGENTS.md` (root) — "Repository-level rules" is the contract for service names, ports and environment handling. Read both `application.yaml` files (`flight-aggregator/infrastructure/src/main/resources/`, `mock-providers/src/main/resources/`): they are the source of truth for environment variable names; the lists below were correct when this prompt was written, so check them.

Precondition: `flight-aggregator/Dockerfile` exists (step D6) and `mock-providers/Dockerfile` exists. If the aggregator's Dockerfile is missing, stop and say so.

The frontend is **not** part of this step. It is added in F2, after the frontend project exists.

## Task

### 1. `docker-compose.yml` at the repository root

Four services, with exactly these names (other projects reference them as hostnames):

| Service | Image / build | Host port | Healthcheck | Depends on (healthy) |
|---|---|---|---|---|
| `postgres` | `postgres:16-alpine` | none | `pg_isready -U $POSTGRES_USER -d $POSTGRES_DB` | — |
| `redis` | `redis:7-alpine` | none | `redis-cli ping` | — |
| `mock-providers` | build `./mock-providers` | `8081` | the one in its Dockerfile | — |
| `flight-aggregator` | build `./flight-aggregator` | `8080` | `/actuator/health/liveness` | `postgres`, `redis`, `mock-providers` |

Rules:
- `depends_on` with `condition: service_healthy` everywhere. No `sleep`, no wait-for scripts.
- The aggregator's healthcheck must hit `/actuator/health/liveness`, **not** `/actuator/health`. The aggregate endpoint includes Redis, and the service is designed to keep serving from Postgres when Redis is down; a Redis outage must not mark the container unhealthy. If the Dockerfile's `HEALTHCHECK` already does this, do not repeat it in compose; if it does not, fix the Dockerfile.
- Give the aggregator a `start_period` long enough for Flyway on a cold volume (30–40 s), and `retries` so a slow first boot does not fail the stack.
- Postgres data in a named volume (`postgres-data`). Redis needs no volume: it only holds freshness markers and locks, and losing them just means the next search re-aggregates.
- Postgres and Redis publish no host ports. Add a commented-out `ports:` block for each, labelled "debugging only".
- One user-defined network is enough; the default one is fine if you add nothing custom.
- `restart: unless-stopped` on the two application services only.

### 2. Environment

Aggregator (container → value):
```
DB_URL=jdbc:postgresql://postgres:5432/${POSTGRES_DB}
DB_USERNAME=${POSTGRES_USER}
DB_PASSWORD=${POSTGRES_PASSWORD}
REDIS_HOST=redis
REDIS_PORT=6379
PROVIDER_ALPHA_URL=http://mock-providers:8081/alpha
PROVIDER_BETA_URL=http://mock-providers:8081/beta
PROVIDER_GAMMA_URL=http://mock-providers:8081/gamma
```
plus every tunable the aggregator's `application.yaml` exposes (markup, freshness TTL, lock TTL, reference currency, provider timeout/budget, warm routes if D6 added them), each passed through as `${NAME:-default}` with the same default as the yaml.

Mock providers: `MOCK_SEED`, `MOCK_INVENTORY_SIZE`, `MOCK_OVERLAP_RATIO`, `MOCK_INVALID_RATIO`, `MOCK_CHAOS_ENABLED`, `MOCK_LATENCY_MIN`, `MOCK_LATENCY_MAX`, `MOCK_ERROR_RATE`, same pattern.

Chaos stays **on** by default (latency 100 ms – 5 s, 10% errors): the brief requires the providers to misbehave, and the reviewer should see partial results without configuring anything.

Create `.env.example` (committed) listing every variable with its default and a one-line comment, grouped by service. `.env` is already git-ignored. The stack must boot with **no** `.env` file present: every variable has a default in the compose file.

### 3. `.dockerignore` files

Check both projects have one that excludes `target/`, `.idea/`, `*.iml`, `.git`, `prompts/`, `docs/`. Create or fix as needed. A missing one makes the build context include `target/` and slows every build.

### 4. `scripts/smoke.sh` at the repository root

Bash, `set -euo pipefail`, needs only `curl` and `jq`. Arguments: base URL (default `http://localhost:8080`), date (default `2026-11-20`).

1. Wait up to 120 s for `GET /actuator/health/liveness` to report `UP`.
2. `GET /api/itineraries?origin=MAD&destination=JFK&date=<date>&limit=5`. Assert HTTP 200, `items` non-empty, `nextCursor` non-null, and at least one provider with `status == "OK"`. With chaos on, a single run can have every provider fail: retry the search up to 3 times before failing, and print the provider statuses of each attempt.
3. Follow `nextCursor` once. Assert 200 and that no `id` repeats from the first page.
4. Assert prices: for the first item, `finalPrice.amount` equals `basePrice.amount × 1.05` rounded to 2 decimals (read the markup from `MARKUP_PERCENTAGE`, default 5).
5. `GET /api/itineraries?origin=MAD` (missing parameters) → 400 with `application/problem+json`.
6. Print `PASS`/`FAIL` per check; exit non-zero on any failure.

### 5. Root `AGENTS.md`

Update "Commands" with the smoke script and with how to turn chaos off for a quiet run (`MOCK_CHAOS_ENABLED=false docker compose up --build`). Do not restructure the file.

## Acceptance

Run all of these and report the actual output, not a summary of what should happen:

```bash
docker compose config --quiet                       # valid file
docker compose down -v && docker compose up --build -d
docker compose ps                                   # all four healthy
./scripts/smoke.sh                                  # all PASS
docker compose stop redis && sleep 5
curl -s localhost:8080/actuator/health/liveness     # still UP
curl -s 'localhost:8080/api/itineraries?origin=MAD&destination=JFK&date=2026-11-20&limit=1' | jq .freshness   # "STALE", HTTP 200
docker compose ps flight-aggregator                 # still healthy, not restarting
docker compose start redis
docker compose down && docker compose up -d         # second boot on the existing volume: Flyway applies nothing, stack healthy
```

Also confirm the stack boots after `mv .env .env.bak` (if a `.env` exists), then restore it.

If the aggregator receives zero offers from a provider that reports `OK`, that is a contract mismatch between the mock and the aggregator's mapper. Do not patch around it in compose: report which provider and show one raw response next to the fixture the mapper was tested with.

## Closing

Do not commit. Show me `git status --short` and the outputs above, then run `/log-ai-decision`.
