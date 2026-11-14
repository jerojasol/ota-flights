# AGENTS.md — mock-providers

This file is the constitution of the `mock-providers` service. Read it before touching code in this project. Repository-level rules (compose, ports, deliverables) live in `../AGENTS.md`.

## 1. What this service is

A standalone Spring Boot application that **simulates three external GDS providers** so the `flight-aggregator` service has something realistic to integrate with:

| Provider | Path prefix | Response format | Personality |
|---|---|---|---|
| **Alpha** | `/alpha` | `application/json`, flat list | Modern, clean, ISO-8601 UTC timestamps |
| **Beta** | `/beta` | `application/xml`, deeply nested | Legacy, attributes + nested elements, local times with IANA zone |
| **Gamma** | `/gamma` | `text/csv`, streamed | Batch-oriented, compact timestamps, segments packed in one field |

It also injects **chaos**: random latency (100 ms – 5 s) and a configurable rate of HTTP 500 responses, so the aggregator's resilience is exercised for real over HTTP.

This service exists to be *called*, never to be *trusted*. It is intentionally inconsistent across providers, and it deliberately produces some invalid itineraries.

## 2. What this service is NOT

- **Not part of the aggregator.** It shares no code, no Maven parent, no domain types with `flight-aggregator`. The three providers have their own vocabulary (see `docs/provider-contracts.md`); the aggregator's Ubiquitous Language (`Itinerary`, `Segment`, `Offer`…) must **never** appear here.
- **Not a test double for unit tests.** The aggregator tests resilience with WireMock and parses fixtures from files. This service is for `docker compose up`, manual exploration and live demos.
- **Not a faithful GDS.** No booking, no availability, no auth. Just search responses.

## 3. Stack

- Java 21, Spring Boot 4.1.2, Maven single module
- `spring-boot-starter-webmvc` (blocking MVC is enough; virtual threads enabled)
- `jackson-dataformat-xml` (Jackson 3, `tools.jackson.*`) for Beta
- Actuator for the compose healthcheck
- No database, no cache, no external calls

## 4. Code layout

```
src/main/java/io/github/jerojasol/mockproviders/
├── MockProvidersApplication.java
├── alpha/        AlphaController + Alpha-specific response records
├── beta/         BetaController + Beta XML response classes
├── gamma/        GammaController (streams CSV via StreamingResponseBody)
├── chaos/        ChaosFilter (latency + error injection), ChaosProperties
├── generator/    InventoryGenerator (deterministic, seeded), shared internal model
└── config/       MockProperties, bean wiring
```

Rules:
- Each provider package owns its response types. Alpha's records, Beta's XML classes and Gamma's CSV line formatter are **three different representations** of the generator's internal model. Do not share DTOs across providers; the whole point is that they differ.
- `generator/` is the only place that knows about all three. It produces an internal `GeneratedItinerary` that each controller translates into its own format.
- `chaos/` is a servlet `Filter`, applied to `/alpha/**`, `/beta/**`, `/gamma/**` but **not** to `/actuator/**`. Health must always be fast and green or compose will think the service is down.

## 5. Behavioural requirements

### Determinism
- All generation is driven by `mock.seed` plus the query parameters `(origin, destination, date)`. The same query on the same seed returns the same inventory, forever. This makes the aggregator's results reproducible across restarts.
- Chaos randomness (latency, errors) uses a **separate** `Random` so that toggling chaos does not change the generated data.

### Overlap and invalidity (what makes the aggregator's job real)
- `mock.overlap-ratio` of itineraries appear in two or three providers with the **same flights and departure times but different prices**. This is what deduplication must catch.
- `mock.invalid-ratio` of itineraries are deliberately broken: a segment with equal origin and destination, an arrival before departure, a connection whose airports do not match, or a connection shorter than 20 minutes. The aggregator must reject them.
- Each provider also differs in **how** it expresses the same thing: Alpha in UTC, Beta in local time + zone, Gamma in `yyyyMMddHHmm` UTC. Prices: Alpha as decimal number, Beta as string with currency attribute, Gamma as integer cents.

### Chaos
- Latency is uniform between `latency-min` and `latency-max`, applied **before** the controller runs.
- Errors return HTTP 500 with a provider-flavoured body (Alpha: JSON error object; Beta: XML `<Fault>`; Gamma: plain text). Error responses count towards latency too.
- Chaos is **on by default** (compose profile) and can be disabled with `MOCK_CHAOS_ENABLED=false`.
- A runtime admin endpoint `PUT /admin/chaos` lets the error rate and latency range be changed without restart, for live demos.

### Query contract
All three providers accept the same query parameters, but the parameter names differ per provider on purpose. See `docs/provider-contracts.md`. Missing or malformed parameters return a provider-flavoured 400.

## 6. Coding conventions

- Package root: `io.github.jerojasol.mockproviders`.
- `record` for all response types where the serializer allows it; Beta's XML classes may need mutable POJOs for Jackson XML, that is acceptable here.
- No Lombok, no MapStruct.
- Constructor injection only.
- Configuration via `@ConfigurationProperties(prefix = "mock")`; no `@Value` sprinkled around.
- Logging: one structured line per request from the chaos filter: `provider=alpha latency_ms=2310 outcome=OK|ERROR`.

## 7. Testing conventions

- `<Class>Test` for unit tests, `<Feature>IT` for integration tests.
- `should_<expected>_when_<condition>`.
- AssertJ only.
- Required coverage:
  - Generator determinism: same seed + query → identical output.
  - Overlap: the configured ratio of itineraries is present in more than one provider.
  - Invalidity: the configured ratio of itineraries violates at least one rule.
  - Each controller returns the right `Content-Type` and a parseable body (`@WebMvcTest`).
  - Chaos filter: with `error-rate=1.0` every request is a 500; with `0.0` none is; latency bounds are respected.
- Chaos is **disabled** in tests unless the test is about chaos.

## 8. Commands

```bash
./mvnw clean verify                  # build + tests
./mvnw spring-boot:run               # run on :8081 with chaos on
MOCK_CHAOS_ENABLED=false ./mvnw spring-boot:run

curl 'localhost:8081/alpha/flights?from=MAD&to=JFK&date=2026-11-20'
curl 'localhost:8081/beta/search?origin=MAD&destination=JFK&departureDate=2026-11-20'
curl 'localhost:8081/gamma/export?o=MAD&d=JFK&dt=20261120'
```

## 9. Working agreements for agents

- Before changing a response format, update `docs/provider-contracts.md` first. That document is the contract the aggregator's mappers are written against; changing the code without the doc breaks the other project silently.
- Never import or copy types from `../flight-aggregator`. If you find yourself wanting to, you are modelling the provider from the consumer's point of view, which is backwards.
- Keep `/actuator/health` outside the chaos filter at all times.
- When adding a new kind of invalid itinerary, add it to the list in section 5 and to `docs/provider-contracts.md` so the aggregator team knows what to reject.
- Log AI-assisted work in `../docs/ai-log.md` as it happens.
