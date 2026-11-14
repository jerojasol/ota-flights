# OTA Flights

Flight Aggregation & Cache Optimization System for an Online Travel Agency.

The system ingests flight itineraries from three external GDS providers with different legacy formats (JSON, XML, CSV), validates them, deduplicates identical itineraries keeping the cheapest offer, applies a 5% OTA markup, persists the result and exposes it through a search API with keyset pagination and a web dashboard.

## Projects

| Directory | Description |
|---|---|
| [`flight-aggregator/`](flight-aggregator/) | Core microservice. Java 21, Spring Boot 4, hexagonal architecture (domain / application / infrastructure). PostgreSQL + Redis. |
| [`mock-providers/`](mock-providers/) | Simulated GDS providers (Alpha, Beta, Gamma) with artificial latency and random failures. Spring Boot MVC. |
| `frontend/` | Search dashboard (React + Vite + TypeScript). See `frontend/AGENTS.md`. |
| `docs/` | `SOLUTION.md` and AI usage log. *(pending)* |

## Running

```bash
docker compose up --build
```

Boots the full stack: aggregator on `:8080`, mock providers on `:8081`, frontend on `:3000`. *(compose file pending)*

Per-project build and test instructions are in each project's `AGENTS.md`.

## Documentation

- [`AGENTS.md`](AGENTS.md) — repository conventions and inter-project contract
- [`flight-aggregator/docs/domain-model.md`](flight-aggregator/docs/domain-model.md) — core domain model, invariants and ports
- [`mock-providers/docs/provider-contracts.md`](mock-providers/docs/provider-contracts.md) — request/response contracts of the three simulated providers
