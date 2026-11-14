---
name: testing-conventions
description: What to test at each layer of flight-aggregator and how (pure unit tests for domain, fixture-driven mapper tests, WireMock for provider resilience, Testcontainers for persistence and keyset pagination, ArchUnit for structure). Use when writing or reviewing tests.
---

# Testing Conventions — flight-aggregator

Naming and tooling rules are in `AGENTS.md` §7. This skill covers *what* to test where and gives the recipes.

## Layer map

| Layer | Test type | Spring context | Tooling | Runs in |
|---|---|---|---|---|
| `domain` | unit | none | JUnit 5 + AssertJ | `mvn test` |
| `application` | unit | none | JUnit 5 + AssertJ + fakes (hand-written) or Mockito | `mvn test` |
| `infrastructure/provider/*` mappers | unit | none | fixtures from `src/test/resources/fixtures/<provider>/` | `mvn test` |
| `infrastructure/provider/*` adapters | integration | minimal | WireMock | `mvn verify` (`*IT`) |
| `infrastructure/persistence` | integration | slice | Testcontainers PostgreSQL + Flyway | `mvn verify` (`*IT`) |
| `infrastructure/cache` | integration | slice | Testcontainers Redis | `mvn verify` (`*IT`) |
| `infrastructure/web` | slice | `@WebMvcTest` | MockMvc, mocked use cases | `mvn test` |
| architecture | unit | none | ArchUnit | `mvn test` |
| end-to-end | integration | full | `@SpringBootTest` + Testcontainers + WireMock | `mvn verify` (`*IT`) |

## 1. Domain tests

One test class per type. One test per invariant, named after it so `domain-guardian` can map coverage:

```java
class SegmentTest {

    @Test
    void should_reject_segment_when_departure_equals_arrival_airport() { // S1
        assertThatThrownBy(() -> Segment.of(IB6251, MAD, t0, MAD, t0.plusHours(2)))
            .isInstanceOf(InvalidSegmentException.class);
    }

    @Test
    void should_reject_segment_when_arrival_not_after_departure() { // S2
        ...
    }
}
```

- Put the invariant ID in a trailing comment on the test method. The guardian greps for it.
- Use a `Fixtures` class in `domain/src/test` with named constants (`MAD`, `JFK`, `IB6251`, a `segment(...)` builder) so tests read like the domain document.
- Boundary tests for connections: 44 min (reject), 45 min (accept), 24 h (accept), 24 h + 1 min (reject).
- `ItineraryKey` tests: same segments different provider → equal; same flights different departure minute → not equal; arrival time changed → still equal.
- Dedup tests: cheapest wins; tie → `ALPHA < BETA < GAMMA`; result size equals distinct keys.
- Markup: `100.00` → `105.00`; `99.99` → `104.99` (HALF_EVEN check with a value that actually rounds).

## 2. Application tests

Hand-written fakes beat mocks here because the behaviour is stateful:

```java
final class InMemoryItineraryRepository implements ItineraryRepositoryPort { ... }
final class StubProvider implements FlightProviderPort {
    // configurable: returns offers, throws ProviderUnavailableException, or sleeps
}
final class InMemoryFreshness implements FreshnessPort { ... }
```

Must-have scenarios for `AggregateFlightsService`:
- all providers OK → offers merged, deduped, persisted; outcomes all `OK`.
- one provider throws → others persisted; that provider's outcome is `ERROR`/`TIMEOUT`; no exception escapes.
- all providers fail → zero persisted, all outcomes non-OK, still a normal result.
- invalid offers counted per provider in `offersRejected`.

Must-have for `SearchItinerariesService`:
- fresh → repository only, no aggregation triggered.
- stale with data → returns data immediately, triggers refresh (assert on a fake executor).
- cold miss → aggregation runs synchronously before the query.
- cursor present → never triggers aggregation.

Use a `Executor` that runs tasks inline (`Runnable::run`) for determinism; test the async path separately with a controllable executor.

## 3. Provider mapper tests (fixtures)

```
infrastructure/src/test/resources/fixtures/
├── alpha/
│   ├── two-itineraries.json
│   ├── empty.json
│   └── error-500.json
├── beta/
│   ├── two-itineraries.xml          legs out of order on purpose
│   ├── empty.xml
│   └── fault-500.xml
└── gamma/
    ├── two-itineraries.csv
    ├── empty.csv
    └── truncated-no-end-marker.csv
```

Fixtures mirror `../mock-providers/docs/provider-contracts.md` exactly. When that document changes, these files change in the same commit.

Each mapper test asserts the *normalisation*:
- Beta local time + zone → correct `Instant`.
- Beta `117` and Gamma `0117` → same `FlightNumber`.
- Gamma `51230` cents → `Price(512.30, EUR)`.
- Beta legs sorted by `@sequence`.

## 4. Provider adapter tests (WireMock)

```java
class BetaProviderAdapterIT {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @Test
    void should_report_timeout_when_provider_exceeds_budget() {
        wm.stubFor(get(urlPathEqualTo("/beta/search"))
            .willReturn(okXml(fixture("beta/two-itineraries.xml")).withFixedDelay(4_000)));
        // adapter configured with 1s TimeLimiter
        assertThatThrownBy(() -> adapter.fetch(criteria))
            .isInstanceOf(ProviderUnavailableException.class)
            .extracting("status").isEqualTo(ProviderStatus.TIMEOUT);
    }

    @Test void should_report_error_when_provider_returns_500() { ... }
    @Test void should_open_circuit_after_consecutive_failures() { ... CIRCUIT_OPEN ... }
    @Test void should_retry_once_on_500_then_succeed() { ... }
}
```

Build the adapter directly (no Spring) with a `RestClient` pointing at `wm.baseUrl()` and explicit Resilience4j configs with short timings. Fast and deterministic.

## 5. Persistence tests (Testcontainers)

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(PersistenceConfiguration.class)
@Testcontainers
class ItineraryRepositoryAdapterIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    ...
}
```

Flyway runs automatically against the container, so the schema under test is the real one.

Must-have:
- upsert inserts new keys.
- upsert with cheaper price updates; with more expensive price leaves row untouched.
- keyset page N+1 starts strictly after page N's last `(final_price, id)`; no duplicates, no gaps across 3 pages.
- filters (`origin`, `destination`, `maxPrice`, `carrier`) compose with the cursor.
- optional, tagged `@Tag("perf")`: seed 1M rows via SQL, assert `EXPLAIN` uses the composite index and page fetch stays under a budget.

## 6. Web slice tests

```java
@WebMvcTest(ItineraryController.class)
class ItineraryControllerTest {
    @MockitoBean SearchItinerariesUseCase search;
    ...
}
```

Test: parameter validation → 400 Problem Details; cursor decode; response shape including `freshness` and `providers`. Do not test business logic here.

## 7. ArchUnit

`infrastructure/src/test/java/.../ArchitectureTest.java`, rules listed in the `hexagonal-java` skill §9. Runs with `mvn test` (fast, no Docker).

## 8. End-to-end (one or two, not many)

`@SpringBootTest(webEnvironment = RANDOM_PORT)` + Postgres and Redis containers + WireMock for all three providers. Scenario: cold search → aggregation → page 1 → page 2 with cursor → Beta goes 500 → stale search still serves data. One test that tells the whole story is worth more than ten fragments.

## Anti-patterns to refuse

- `@SpringBootTest` for anything in `domain` or `application`.
- Mocking value objects.
- Asserting on log output.
- Sleeping in tests to "wait for async"; use controllable executors or Awaitility.
- Depending on the `mock-providers` project in any test.
- Weakening an assertion to make a test pass.
