# OTA Flights

Flight Aggregation & Cache Optimization System for an Online Travel Agency.

The system ingests flight itineraries from three external GDS providers with different legacy formats (JSON, XML, CSV), validates them, deduplicates identical itineraries keeping the cheapest offer, applies a 5% OTA markup, persists the result and exposes it through a search API with keyset pagination and a web dashboard.

Architecture, AI tooling, data model, pagination and limitations: **[`SOLUTION.md`](SOLUTION.md)**.

## Projects

| Directory | Description |
|---|---|
| [`flight-aggregator/`](flight-aggregator/) | Core microservice. Java 21, Spring Boot 4, hexagonal architecture (domain / application / infrastructure). PostgreSQL + Redis. |
| [`mock-providers/`](mock-providers/) | Simulated GDS providers (Alpha, Beta, Gamma) with artificial latency and random failures. Spring Boot MVC. |
| [`frontend/`](frontend/) | Search dashboard (React + Vite + TypeScript), served by nginx. |
| [`SOLUTION.md`](SOLUTION.md) | Architectural decisions, AI tooling, data model and pagination, limitations. |
| `docs/` | The [AI usage log](docs/ai-log.md). |

## Quick start

**Prerequisites:** Docker with Compose v2. Nothing else: Java, Maven and Node run inside the build containers.

```bash
docker compose up --build
```

The first build took about 2 minutes on an Apple Silicon Mac with the base images already pulled (`build --no-cache`: 120 s; `up` until every service was healthy: 33 s). Downloading the base images adds time on a fresh machine.

| What | URL |
|---|---|
| Dashboard | http://localhost:3000 |
| API docs (Swagger UI) | http://localhost:8080/swagger-ui.html |
| Mock providers | http://localhost:8081 (API only, e.g. `/admin/chaos`; no page at `/`) |

**First search:** in the dashboard, search `LIS → BOS` on any future date, then load more results. The first search of a route takes a few seconds (up to 6 s), because the providers are slow on purpose (100 ms – 5 s each) and are queried live. Later searches of the same route are served from Postgres straight away. Three routes are aggregated in the background at startup and every 10 minutes, for today and tomorrow: `MAD → JFK`, `MAD → LHR` and `BCN → JFK`. Searching one of those is immediate from the first time. Each result shows the provider that offered it; the panel above the results shows freshness and the status of each provider (OK, TIMEOUT, ERROR, CIRCUIT_OPEN). Out of the box, some calls fail on purpose (10% errors).

### See the resilience

Make about half of all provider calls fail:

```bash
curl -X PUT localhost:8081/admin/chaos -H 'Content-Type: application/json' -d '{"errorRate":0.5}'
```

Search a route and press "Refresh now" a few times. The usual outcome is a partial failure: the panel says how many providers answered and shows the others as ERROR or TIMEOUT, then CIRCUIT_OPEN once their circuit breakers trip. When all three fail in one refresh, nothing is stored: the dashboard says "No provider answered; showing stored results." and the stored results and provider statuses stay as they were. Restore with `-d '{"errorRate":0.1}'`; the breakers let calls through again after 30 s.

### Try the API directly

```bash
# First page: 5 itineraries, cheapest first, with freshness and the status of each provider
curl -s 'localhost:8080/api/itineraries?origin=LIS&destination=BOS&date=2026-12-10&limit=5' | jq .

# Next page: pass the nextCursor of the previous response
CURSOR=$(curl -s 'localhost:8080/api/itineraries?origin=LIS&destination=BOS&date=2026-12-10&limit=5' | jq -r .nextCursor)
curl -s -G 'localhost:8080/api/itineraries' \
  --data-urlencode 'origin=LIS' --data-urlencode 'destination=BOS' --data-urlencode 'date=2026-12-10' \
  --data-urlencode 'limit=5' --data-urlencode "cursor=$CURSOR" | jq '.items[].finalPrice'

# Optional filters: maxPrice (in EUR) and carrier
curl -s 'localhost:8080/api/itineraries?origin=LIS&destination=BOS&date=2026-12-10&maxPrice=400&carrier=IB' | jq '.items | length'

# Force a new aggregation of a route (409 if one is already running)
curl -s -X POST localhost:8080/api/admin/aggregations -H 'Content-Type: application/json' \
  -d '{"origin":"LIS","destination":"BOS","date":"2026-12-10"}' | jq .
```

All parameters and responses are documented in Swagger UI.

### Look inside

PostgreSQL and Redis publish no host port; reach them through their containers.

```bash
# Stored itineraries per route and date: one row per itinerary, already deduplicated
docker compose exec postgres psql -U flights -d flights -c \
  "SELECT origin, destination, departure_date, count(*), min(final_price), max(final_price) FROM itineraries GROUP BY 1,2,3 ORDER BY 1,2,3;"

# What Redis holds: fresh:, seen: and lock: keys per route and date. No itineraries and no cached pages.
docker compose exec redis redis-cli KEYS '*'
docker compose exec redis redis-cli GET seen:LIS:BOS:2026-12-10
```

### Other ways to run

```bash
MOCK_CHAOS_ENABLED=false docker compose up --build   # quiet run: no artificial latency or errors
docker compose down -v                               # stop and reset, including the database
FRONTEND_PORT=3001 AGGREGATOR_PORT=9080 docker compose up -d   # if a port is busy (also MOCK_PROVIDERS_PORT)
./scripts/smoke.sh                                   # end-to-end check against a running stack (curl + jq)
```

Every setting has a default; [`.env.example`](.env.example) lists them all. Copy it to `.env` to override.

### Tests

Running the tests locally, unlike running the stack, needs JDK 21, Node 24 (pinned in `frontend/.tool-versions`) and, for the aggregator's integration tests, a running Docker daemon. The stack itself does not need to be up.

```bash
cd flight-aggregator && ./mvnw verify   # unit + integration tests (Testcontainers); add -DskipITs to run without Docker
cd mock-providers && ./mvnw verify
cd frontend && npm ci && npm test
```

Per-project details are in each project's `AGENTS.md`.

## Documentation

- [`AGENTS.md`](AGENTS.md) — repository conventions and inter-project contract
- [`flight-aggregator/docs/domain-model.md`](flight-aggregator/docs/domain-model.md) — core domain model, invariants and ports
- [`mock-providers/docs/provider-contracts.md`](mock-providers/docs/provider-contracts.md) — request/response contracts of the three simulated providers
- [`docs/ai-log.md`](docs/ai-log.md) — log of the AI-assisted work, step by step
- Prompts used for each step: [`prompts/`](prompts/), [`flight-aggregator/prompts/`](flight-aggregator/prompts/), [`mock-providers/prompts/`](mock-providers/prompts/), [`frontend/prompts/`](frontend/prompts/)
