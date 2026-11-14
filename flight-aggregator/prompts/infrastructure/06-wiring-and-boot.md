# D6 — Wiring, main class, properties, scheduler, Dockerfile

Load the `hexagonal-java` skill (§5). Read `AGENTS.md` §2, §6 and `../AGENTS.md` (compose contract: hostnames, ports, env vars).

## Task

### `FlightAggregatorApplication`
`@SpringBootApplication` in `io.github.jerojasol.flights.infrastructure`. Component scan is limited to `infrastructure` by default (same package) — good; domain and application are wired explicitly.

### `UseCaseConfiguration`
Beans:
- `OfferValidator`, `ItineraryDeduplicator`, `PercentageMarkupPolicy` (from `aggregation.markup-percentage`), `ItineraryFactory`.
- `Executor providerExecutor` — `Executors.newVirtualThreadPerTaskExecutor()` wrapped so Spring closes it.
- `Executor refreshExecutor` — bounded (e.g. 4 threads) so background refreshes cannot stampede providers.
- `AggregateFlightsUseCase` → `AggregateFlightsService(List<FlightProviderPort>, …, providerBudget)`.
- `SearchItinerariesUseCase` → `SearchItinerariesService(…)`.
- `Clock.systemUTC()`.

### `AggregationProperties` (`@ConfigurationProperties("aggregation")`)
`markupPercentage`, `referenceCurrency`, `freshnessTtl`, `lockTtl`, `providerBudget`, `warmRoutes` (list of `MAD-JFK` style strings), `warmIntervalMinutes`.

Validate at startup (fail fast with a clear message):
- `lockTtl >= 2 × providerBudget` — the lock must comfortably outlive an aggregation (fan-out budget + validation + upsert + markFresh). The owner-token release protects correctness if it does not, but the stampede protection would be weakened.
- `freshnessTtl > lockTtl`.
- `markupPercentage >= 0`.

### `WarmRoutesScheduler` (`infrastructure/scheduling`)
`@Scheduled(fixedDelayString = "${aggregation.warm-interval:PT10M}")` iterating `warmRoutes` for today and tomorrow, calling `AggregateFlightsUseCase` behind the `FreshnessPort` lock so it never races a user-triggered aggregation. Disabled when the list is empty. This is the "scheduled ingestion" trigger that produces the daily volume.

### `application.yaml`
Extend the existing file:
```yaml
aggregation:
  markup-percentage: ${MARKUP_PERCENTAGE:5}
  reference-currency: ${REFERENCE_CURRENCY:EUR}
  freshness-ttl: ${FRESHNESS_TTL:10m}
  lock-ttl: ${LOCK_TTL:30s}
  provider-budget: ${PROVIDER_BUDGET:3500ms}
  warm-routes: ${WARM_ROUTES:MAD-JFK,MAD-LHR,BCN-JFK}
  warm-interval: ${WARM_INTERVAL:PT10M}
```
Add `application-test.yaml` with chaos-free defaults for ITs. Add `logging.level.io.github.jerojasol: INFO` and a structured log line in a `ProviderOutcomeLogger` (infrastructure) that logs each `AggregationResult`'s outcomes — logging lives here, not in `application`.

### Actuator
Expose `health,info,metrics`; add a `HealthIndicator` per provider circuit breaker state (`UP` when closed/half-open, `DEGRADED` detail when open; overall health stays `UP` so compose does not restart the service because a provider is down).

**Redis must not decide container health.** Spring Boot adds a Redis health indicator to `/actuator/health` by default, so a Redis outage would report `DOWN` there. The service is designed to keep serving searches from Postgres while Redis is down (`RedisFreshnessAdapter` fails closed: `status` returns `STALE`, `tryLock` returns empty), so the container healthcheck must use the liveness probe, `/actuator/health/liveness` (probes are already enabled in `application.yaml`). Keep Redis visible in `/actuator/health` for operators; just do not point the healthcheck at it. Postgres stays in readiness: without it the service cannot serve anything.

### Refreshing while Redis is down
With Redis unreachable nothing acquires the aggregation lock, so nothing refreshes data: not searches, not `WarmRoutesScheduler`, and not `POST /api/admin/aggregations`, which goes through `RefreshItinerariesUseCase` (lock → aggregate → markFresh → release) and answers 409 when the lock cannot be taken. This is deliberate: the system fails closed everywhere rather than keeping one path that reaches providers without coordination. State it in `docs/SOLUTION.md` §6: during a Redis outage searches serve what is in Postgres and no refresh is possible until Redis is back.

Wire `RefreshItinerariesUseCase` in `UseCaseConfiguration`, and make `WarmRoutesScheduler` call it too instead of handling the lock itself.

`OfferValidator` and `SearchRequestMapper` must be built from the same `aggregation.reference-currency` property; do not introduce a second one.

### `Dockerfile` (multi-stage)
```
FROM eclipse-temurin:21-jdk AS build      -> ./mvnw -q -DskipTests package
FROM eclipse-temurin:21-jre               -> copy infrastructure/target/*.jar, non-root user, EXPOSE 8080
HEALTHCHECK via curl on /actuator/health/liveness (not /actuator/health: see "Actuator")
```
Build context is `flight-aggregator/`. Use layered jar extraction if it is quick; otherwise plain jar.

### Smoke test
`FlightAggregatorApplicationIT` (`@SpringBootTest` + Postgres + Redis containers, providers pointed at a WireMock with the three fixtures): context loads, `/actuator/health` is `UP`, one `GET /api/itineraries` returns 200. Then stop the Redis container and assert: `/actuator/health/liveness` is still `UP`, `GET /api/itineraries` still returns 200 with `freshness=STALE`, and `POST /api/admin/aggregations` returns 409 without calling any provider.

## Acceptance

- `./mvnw -q verify` green with Docker.
- `./mvnw -q -pl infrastructure spring-boot:run` starts against local Postgres/Redis (or fails fast with a clear message if they are absent — no hanging).
- `docker build -t flight-aggregator .` succeeds from `flight-aggregator/`.

## Closing

`/check-arch`, `/test-layer all`, `/log-ai-decision`, show diff summary.

## Added before execution: gaps the wiring must also cover

Found by checking this prompt against the code and the D5 review notes. Each item is part of D6.

1. **Redis client timeouts.** Lettuce waits 60 s per command by default. With Redis unreachable without a TCP reset (a network partition), every search would block for a minute before `RedisFreshnessAdapter` degrades. Set `spring.data.redis.timeout: 500ms` and `spring.data.redis.connect-timeout: 500ms`. The smoke test asserts a search with Redis stopped answers within 2 s.
2. **Postgres fails fast.** Hikari waits 30 s by default before failing startup. Set `spring.datasource.hikari.connection-timeout: 5s` so `spring-boot:run` without Postgres fails in seconds with a clear message, as the acceptance requires.
3. **`providers.timeout` must be shorter than `aggregation.provider-budget`.** Otherwise the application's budget always cuts a slow provider off first: it is reported TIMEOUT by the fan-out, the adapter's TimeLimiter never fires, and its circuit breaker never records the slowness. Validate at startup, with a clear message.
4. **The refresh executor needs a bounded queue, not only bounded threads.** `Executors.newFixedThreadPool(4)` has an unbounded queue, so refreshes would pile up instead of being refused. Use a `ThreadPoolExecutor` with 4 threads, a bounded queue (e.g. 64) and `AbortPolicy`: `SearchItinerariesService` already releases the lock when a task is rejected. Shut it down when the context closes.
5. **One `Clock` and one set of TTLs.** `ItineraryFactory` and `AggregateFlightsService` share the single `Clock` bean, so `ingestedAt` and `aggregatedAt` come from one source. `SearchItinerariesService` and `RefreshItinerariesService` get the same `freshness-ttl` and `lock-ttl`. Register `AggregationProperties` in one configuration class only.
6. **Exactly one adapter per Provider.** `AggregateFlightsService` already rejects duplicates; also fail fast if a Provider has no adapter, so a missing bean is a startup error, not a provider silently absent from every outcome.
7. **Scheduler details.** `@EnableScheduling` is required. `RefreshItinerariesUseCase` propagates aggregation failures, so the scheduler catches per route and logs, and one failing route never stops the others. Dates "today and tomorrow" come from the shared `Clock` (UTC). Validate the `warm-routes` format (`AAA-BBB`) at startup. The `test` profile sets `warm-routes` empty so no IT ever calls providers on a timer.
8. **`warmIntervalMinutes` vs `warm-interval: PT10M`.** The yaml uses a Duration; bind it as `Duration warmInterval`, not a minutes integer.
9. **`ProviderOutcomeLogger` as a decorator.** Wrap the `AggregateFlightsUseCase` bean in infrastructure, so every aggregation (search, refresh, scheduler) is logged once and `application` stays free of logging.
10. **Health groups and details.**
    - The provider health indicators always report `UP`, with the circuit state as a detail (`degraded: true` when open), so overall health never goes DOWN because a provider is down.
    - `readiness` includes `db`; `liveness` stays as it is (no Redis).
    - `show-details: always`, so operators see the breakers. There is no authentication in this delivery; see `SOLUTION.md` §6.
11. **ArchUnit rule 7 and the breaker health indicators.** Classes in `provider.<name>` may only be accessed from that package or `config`. The indicators live in `infrastructure.provider` and are built in `config`; adapters expose their circuit state through a type in `infrastructure.provider`.
12. **Graceful shutdown.** `server.shutdown: graceful`, so in-flight searches finish and executors close cleanly on `docker compose down`.

Out of scope here, flagged: the repository has **no root `docker-compose.yml`**, although `../AGENTS.md` requires `docker compose up --build` to boot the whole stack. This project's AGENTS.md forbids editing compose from here except for this service's own container, so creating it is a separate, repository-level step.
