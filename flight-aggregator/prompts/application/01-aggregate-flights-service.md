# B1 — AggregateFlightsService

Load the `hexagonal-java` and `testing-conventions` skills. Read `docs/domain-model.md` §9 (pipeline and resilience rules). Work only in `application/`.

## Task

Implement `AggregateFlightsService implements AggregateFlightsUseCase` in `application/src/main/java/io/github/jerojasol/flights/application/`.

Constructor dependencies (all injected, no annotations):
- `List<FlightProviderPort> providers`
- `OfferValidator validator`
- `ItineraryDeduplicator deduplicator`
- `ItineraryFactory factory`
- `ItineraryRepositoryPort repository`
- `Executor executor` (fan-out executor; infrastructure will pass a virtual-thread executor)
- `Duration providerBudget` (overall wait budget for the fan-out, e.g. 3.5 s)
- `Clock clock`

Behaviour of `aggregate(SearchCriteria)`:
1. Fan-out: one `CompletableFuture<ProviderFetch>` per provider on `executor`. Each measures elapsed time and captures either the offer list or the `ProviderUnavailableException` (status from the exception) or any other `Exception` (→ `ERROR`). **Never** let an `Exception` escape the future. Do **not** catch `Throwable`/`Error`: a `java.lang.Error` (OOM, StackOverflow, linkage) is a JVM-level failure and must propagate; turning it into a provider outcome would hide it.
2. Wait for all with `orTimeout(providerBudget)`; a provider that has not answered by then gets outcome `TIMEOUT` with zero offers. Do not block indefinitely.
3. Validate all received offers with `validator.validateAll`; count rejections **per provider**.
4. Deduplicate the valid offers.
5. Build itineraries with `factory.create`.
6. `repository.upsertAll` → `persisted`.
7. Return `AggregationResult(persisted, outcomes, clock.instant())`. If every provider failed, `persisted = 0` and the result is still returned normally.

Keep the class free of logging frameworks (no SLF4J in `application`); return enough information in `AggregationResult` for infrastructure to log.

## Tests (`application/src/test`)

Hand-written fakes in `application/src/test/.../fakes/`: `StubProvider` (configurable offers / exception / delay), `InMemoryItineraryRepository`, plus reuse of real domain services (they are pure; no need to mock them).

Scenarios:
- all three OK with overlapping offers → persisted = distinct keys; outcomes all `OK`; `offersReceived` per provider correct.
- Beta throws `ProviderUnavailableException(ERROR)` → Alpha+Gamma persisted; Beta outcome `ERROR`, 0 received.
- Gamma sleeps longer than budget → outcome `TIMEOUT`; others persisted; total call time ≈ budget, not ≈ Gamma's delay (assert with a generous upper bound).
- all fail → persisted 0, `allFailed()` true, no exception.
- invalid offers from Alpha are counted in Alpha's `offersRejected` and not persisted.
- a provider throwing a `RuntimeException` (not domain) → `ERROR`, pipeline continues.
- providers run concurrently: with three providers each delaying 300 ms, the call completes in well under 900 ms.

Use a direct executor (`Runnable::run`) where determinism matters and a real cached-thread executor for the concurrency/timeout tests.

## Acceptance

- `./mvnw -q -pl application test` green.
- No Spring, no SLF4J, no Resilience4j imports in `application`.

## Closing

`/check-arch`, `/log-ai-decision`, show diff summary.
