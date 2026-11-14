# AGENTS.md — Repository root

This repository contains the full stack for the OTA Flight Aggregation & Cache Optimization System. It is a monorepo of independent projects orchestrated by a single `docker-compose.yml` at this level.

## Projects

| Directory | What it is | Own `AGENTS.md` |
|---|---|---|
| `flight-aggregator/` | Core microservice (Java 21 / Spring Boot). Ingestion, deduplication, pricing, search API. | Yes — read it before touching that project. |
| `mock-providers/` | Standalone service simulating the three external GDS providers (Alpha/Beta/Gamma) with artificial latency and errors. The response formats are specified in `mock-providers/docs/provider-contracts.md`; the aggregator's adapters are written against that document. | Yes |
| `frontend/` | Search dashboard (React + Vite). | Yes |
| `docs/` | Repository-level deliverables: `SOLUTION.md`, `ai-log.md`. | — |

Each project is self-contained: its own build, its own tests, its own Dockerfile. **Projects never share source code.** `mock-providers` must not depend on `flight-aggregator` types; the providers are external systems and must look like it.

## Repository-level rules

- `docker compose up --build` from this directory must boot the whole stack with no manual steps. This is the first thing reviewers run. Never commit a change that breaks it.
- Service names in `docker-compose.yml` are the contract between projects: `flight-aggregator`, `mock-providers`, `frontend`, `postgres`, `redis`. Projects reference each other only by these hostnames and by environment variables injected from the compose file.
- Use healthchecks and `depends_on: condition: service_healthy`; no `sleep`-based startup scripts.
- Ports exposed to the host: `8080` (flight-aggregator), `8081` (mock-providers), `3000` (frontend). Postgres and Redis are internal only unless debugging.
- Environment configuration lives in `.env.example` (committed) and `.env` (ignored). The compose file reads from it.

## Deliverables

- `docs/SOLUTION.md`: architectural decisions, AI tooling used, data model and pagination justification, limitations and trade-offs.
- `docs/ai-log.md`: running log of AI-assisted work (date, intent, prompt summary, what was generated, what a human corrected). Agents append to it as work happens; do not reconstruct it at the end.

## Commands

```bash
docker compose up --build              # full stack
docker compose down -v                 # reset including volumes
docker compose logs -f flight-aggregator
```

For project-specific build and test commands, see each project's `AGENTS.md`.
