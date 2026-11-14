# AGENTS.md — flight-aggregator

This file is the constitution of the `flight-aggregator` microservice. Every agent and contributor reads it before touching code in this project. If a rule here conflicts with a convenience, the rule wins. Repository-level rules (compose, ports, deliverables) live in `../AGENTS.md`.

## 1. What this service does

Ingests flight itineraries from three external GDS providers — **Alpha** (JSON), **Beta** (XML), **Gamma** (CSV stream) — validates them, deduplicates identical itineraries, keeps the cheapest, applies a 5% OTA markup, persists them, and exposes a search API with keyset pagination.

The providers are external systems. In this repository they are simulated by the sibling `mock-providers` project, but this service knows nothing about that: it only sees base URLs injected via configuration.

Target scale: 100,000 – 1,000,000 itineraries per day.

## 2. Stack

- Java 21 (LTS), Spring Boot **4.1.2** (Spring Framework 7, Jackson 3), Maven multi-module
- Spring Boot 4 starters are modular: use `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `spring-boot-starter-data-redis`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`. Do not copy starter names from 3.x poms without checking.
- HTTP client for provider adapters: `RestClient`. Not `RestTemplate` (maintenance mode), not `WebClient` (this service is not reactive).
- Virtual threads enabled (`spring.threads.virtual.enabled=true`) for provider fan-out.
- PostgreSQL 16 with Flyway migrations
- Redis for freshness markers, aggregation locks and hot-page caching
- Resilience4j (circuit breaker, time limiter, retry)
- JUnit 5, AssertJ, ArchUnit, WireMock, Testcontainers 2.x (artifacts are `testcontainers-postgresql`, `testcontainers-junit-jupiter`, `com.redis:testcontainers-redis`; the 1.x names `postgresql`/`junit-jupiter` do not resolve)
- Do not upgrade to Spring Boot 4.2.x for this delivery even if released; stay on 4.1.x patch releases.

## 3. Module layout and dependency rules

```
flight-aggregator/
├── pom.xml            Parent POM (inherits spring-boot-starter-parent for version/plugin management only; declares NO dependencies)
├── domain/            flight-aggregator-domain. Pure Java. NO framework dependencies. Only JUnit/AssertJ in test scope.
├── application/       flight-aggregator-application. Use case orchestration. Depends on domain only.
├── infrastructure/    flight-aggregator-infrastructure. Spring Boot app. Web, persistence, provider clients, cache, config. Only module with spring-boot-maven-plugin.
└── docs/              domain-model.md
```

Maven coordinates: `groupId` is `io.github.jerojasol`; module `artifactId`s carry the `flight-aggregator-` prefix. Third-party versions not managed by the Spring Boot BOM (Resilience4j, springdoc, WireMock, ArchUnit) are pinned in the parent `dependencyManagement`; child poms never declare versions.

Allowed dependency direction (enforced by Maven poms and ArchUnit):

```
infrastructure ──> application ──> domain
```

Violations are build failures, not warnings.

## 4. Domain rules (non-negotiable)

Read `docs/domain-model.md` before writing anything in `domain/`. It is the source of truth for names, invariants and business rules.

- **Forbidden imports in `domain/`**: `org.springframework.*`, `jakarta.*`, `javax.*`, `tools.jackson.*`, `com.fasterxml.*`, `lombok.*`, `org.hibernate.*`, `io.github.resilience4j.*`, `org.jspecify.*`, any HTTP, JSON, XML or CSV library.
- **Value Objects** are Java `record`s or `final` classes with private constructors and static factories. No setters. Validate in the constructor; throw a domain exception on invalid state.
- **Money** is `BigDecimal` with explicit scale and `RoundingMode.HALF_EVEN`. Never `double`/`float`.
- **Time** is `java.time.Instant` (UTC) inside the domain. Time zone conversion is the adapter's job.
- **Identity** of an itinerary for deduplication is `ItineraryKey`, never a database id.
- **Ports** live in `domain/port/in` (use cases) and `domain/port/out` (repositories, providers). They are plain interfaces.
- **No anemic models.** Behaviour that reads or mutates an aggregate's state belongs to the aggregate. Services only coordinate.
- **Use the Ubiquitous Language** (section 5). Do not introduce synonyms.

## 5. Ubiquitous Language

| Term | Meaning |
|---|---|
| **Provider** | An external GDS source: `ALPHA`, `BETA`, `GAMMA`. |
| **Itinerary** | A complete journey from origin to destination composed of one or more Segments, sold at a Price. The aggregate root. |
| **Segment** | A single flight operated by a Carrier: flight number, departure airport/time, arrival airport/time. |
| **Connection** | The gap between the arrival of segment N and the departure of segment N+1. |
| **Carrier** | An airline, identified by its IATA 2-letter code. |
| **Airport** | Identified by IATA 3-letter code. |
| **Price** | Amount + currency. |
| **Base Price** | The price as quoted by the provider. |
| **Final Price** | Base Price after applying the Markup Policy. |
| **Markup Policy** | Rule that derives Final Price from Base Price. Default: +5%. |
| **Itinerary Key** | Deterministic identity derived from carrier, ordered flight numbers, ordered departure times and connection airports. Two itineraries with the same key are *the same product* regardless of provider. |
| **Offer** | An Itinerary as returned by a specific Provider at a specific Base Price, before deduplication. |
| **Dead Leg** | A segment that is invalid on its own (same origin and destination, non-positive duration). |
| **Invalid Connection** | A connection that violates the connection rules in `domain-model.md`. |
| **Aggregation** | The process: fan-out to providers → collect Offers → validate → deduplicate → select cheapest → apply markup → persist. |
| **Provider Status** | Outcome of a provider call during an aggregation: `OK`, `TIMEOUT`, `ERROR`, `CIRCUIT_OPEN`. |

Do **not** use: `Flight` (ambiguous — say Segment or Itinerary), `Trip`, `Journey`, `Route`, `Fare`, `Ticket`.

## 6. Coding conventions

- Package root: `io.github.jerojasol.flights`. Module packages: `io.github.jerojasol.flights.domain`, `io.github.jerojasol.flights.application`, `io.github.jerojasol.flights.infrastructure`.
- Prefer `record` for immutable data. Prefer `sealed` interfaces for closed hierarchies (e.g. validation results).
- No Lombok. Java 21 makes it unnecessary.
- No `Optional` as field or parameter type; return type only. Agreed exception: the port parameters and query/result records listed in `docs/domain-model.md` §8 ("Optional convention").
- No `null` across module boundaries. Use `Optional` or empty collections.
- Constructor injection only. No field injection.
- Exceptions: domain exceptions extend `DomainException` (unchecked). Infrastructure maps them to RFC 7807 Problem Details.
- Mappers are explicit hand-written classes named `XxxMapper`. No MapStruct.
- JPA entities live only in `infrastructure/.../persistence` and are never exposed above that package.
- Provider adapters live in `infrastructure/.../provider/<alpha|beta|gamma>`, each with its own client, DTOs and mapper. Provider DTOs never leave their package.
- Configuration: `application.yml` with environment variable placeholders (`${PROVIDER_ALPHA_URL}`, `${DB_URL}`, …). No hardcoded hostnames.
- Logging: SLF4J, structured key=value messages, no PII.

## 7. Testing conventions

- Test class name: `<ClassUnderTest>Test`. Integration: `<Feature>IT`.
- Method names: `should_<expected>_when_<condition>`.
- AssertJ only; no Hamcrest, no JUnit `assertEquals`.
- `domain/` tests are pure unit tests. Zero Spring context.
- Every invariant listed in `docs/domain-model.md` has at least one test that proves it is enforced.
- Provider mappers are tested with real fixture files in `infrastructure/src/test/resources/fixtures/<provider>/`.
- Resilience behaviour (timeouts, 500s, circuit open, partial results) is tested with WireMock. Never depend on the `mock-providers` project in tests.
- Persistence and pagination are tested with Testcontainers PostgreSQL.
- ArchUnit test `ArchitectureTest` in `infrastructure` enforces section 3 and section 4 import rules.

## 8. Commands

```bash
./mvnw verify                                    # unit + integration (needs Docker)
./mvnw verify -DskipITs                          # unit only
./mvnw verify -Pperf                             # perf-tagged ITs only
./mvnw -pl domain test                           # domain tests only (fast)
./mvnw -pl infrastructure spring-boot:run        # run locally (expects Postgres/Redis/providers reachable)
```

## 9. Working agreements for agents

- Before creating a new domain type, check `docs/domain-model.md`. If it is not there, propose the change to the document first.
- After any change in `domain/` or `application/`, run `./mvnw -pl domain,application test` and the ArchUnit test.
- When generating non-trivial code or making a design decision with AI assistance, append an entry to `../docs/ai-log.md`.
- Never weaken a test to make it pass. Fix the code or raise the issue.
- Never add a dependency to `domain/pom.xml` beyond test scope.
- Do not edit `../docker-compose.yml` from this project's context unless the change is about this service's own container (env vars, healthcheck, image).
