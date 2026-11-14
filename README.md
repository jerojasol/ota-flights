# OTA Flights

Flight Aggregation & Cache Optimization System for an Online Travel Agency.

The system ingests flight itineraries from three external GDS providers with different legacy formats (JSON, XML, CSV), validates them, deduplicates identical itineraries keeping the cheapest offer, applies a 5% OTA markup, persists the result and exposes it through a search API with keyset pagination and a web dashboard.

## Projects

| Directory | Description |
|---|---|
| [`flight-aggregator/`](flight-aggregator/) | Core microservice. Java 21, Spring Boot 4, hexagonal architecture (domain / application / infrastructure). PostgreSQL + Redis. |
| [`mock-providers/`](mock-providers/) | Simulated GDS providers (Alpha, Beta, Gamma) with artificial latency and random failures. Spring Boot MVC. |
| `frontend/` | Search dashboard (React + Vite + TypeScript). See `frontend/AGENTS.md`. |
| `docs/` | [`SOLUTION.md`](docs/SOLUTION.md) and the [AI usage log](docs/ai-log.md). |

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

**First search:** in the dashboard, search `MAD → JFK` on any future date, then load more results. The first search of a route takes a few seconds (up to 6 s), because the providers are slow on purpose (100 ms – 5 s each) and are queried live. Later searches of the same route are served from Postgres straight away. Each result shows the provider that offered it; the panel above the results shows freshness and the status of each provider (OK, TIMEOUT, ERROR, CIRCUIT_OPEN). Out of the box, some calls fail on purpose (10% errors).

### See the resilience

Make about half of all provider calls fail:

```bash
curl -X PUT localhost:8081/admin/chaos -H 'Content-Type: application/json' -d '{"errorRate":0.5}'
```

Search a route and press "Refresh now" a few times. The usual outcome is a partial failure: the panel says how many providers answered and shows the others as ERROR or TIMEOUT, then CIRCUIT_OPEN once their circuit breakers trip. When all three fail in one refresh, nothing is stored: the dashboard says "No provider answered; showing stored results." and the stored results and provider statuses stay as they were. Restore with `-d '{"errorRate":0.1}'`; the breakers let calls through again after 30 s.

### Other ways to run

```bash
MOCK_CHAOS_ENABLED=false docker compose up --build   # quiet run: no artificial latency or errors
docker compose down -v                               # stop and reset, including the database
FRONTEND_PORT=3001 AGGREGATOR_PORT=9080 docker compose up -d   # if a port is busy (also MOCK_PROVIDERS_PORT)
./scripts/smoke.sh                                   # end-to-end check against a running stack (curl + jq)
```

Every setting has a default; [`.env.example`](.env.example) lists them all. Copy it to `.env` to override.

### Tests

```bash
cd flight-aggregator && ./mvnw verify   # unit + integration tests; the integration tests need Docker (Testcontainers)
cd mock-providers && ./mvnw verify
cd frontend && npm ci && npm test
```

Per-project details are in each project's `AGENTS.md`.

## Documentation

- [`AGENTS.md`](AGENTS.md) — repository conventions and inter-project contract
- [`flight-aggregator/docs/domain-model.md`](flight-aggregator/docs/domain-model.md) — core domain model, invariants and ports
- [`mock-providers/docs/provider-contracts.md`](mock-providers/docs/provider-contracts.md) — request/response contracts of the three simulated providers
