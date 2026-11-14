# A5 — Ports and supporting types

Load the `hexagonal-java` skill. Read `docs/domain-model.md` §8 (ports) and §9 (pipeline, resilience rules). This step completes the domain's public surface so the application layer can be written against it.

## Task

### Supporting types (`domain/model` or `domain/query`, be consistent)

- `SearchCriteria(Airport origin, Airport destination, LocalDate departureDate)` — reject `origin == destination`.
- `ItineraryFilter(Optional<Airport> origin, Optional<Airport> destination, Optional<Price> maxPrice, Optional<Carrier> carrier)` with a static `none()` and a small builder or `with*` methods. `Optional` is allowed here only as a component of a query record returned/consumed by ports — note in a comment that this is the agreed exception to the "no Optional fields" rule, or model it with nullable + accessor returning Optional. Pick one and record the decision.
- `PageCursor(Price lastFinalPrice, long lastId)` — opaque to callers; encoding to/from string is **not** domain (it goes in the web adapter).
- `ItineraryPage(List<Itinerary> items, Optional<PageCursor> next)`.
- `ProviderStatus` enum: `OK, TIMEOUT, ERROR, CIRCUIT_OPEN`.
- `ProviderOutcome(ProviderStatus status, int offersReceived, int offersRejected, Duration elapsed)`.
- `AggregationResult(int persisted, Map<Provider, ProviderOutcome> outcomes, Instant aggregatedAt)` with helpers `allFailed()`, `anySucceeded()`.
- `Freshness` enum: `FRESH, STALE, REFRESHING, MISSING` — the read-path state (from the agreed flow). Add it to `domain-model.md` §8 first; the document currently lacks it.
- `ProviderUnavailableException extends DomainException` carrying `Provider` and `ProviderStatus`.

### Inbound ports (`domain/port/in`)

```java
public interface AggregateFlightsUseCase {
    AggregationResult aggregate(SearchCriteria criteria);
}

public interface SearchItinerariesUseCase {
    SearchResult search(SearchCriteria criteria, ItineraryFilter filter, Optional<PageCursor> cursor, int limit);
}
```
with `SearchResult(ItineraryPage page, Freshness freshness, Optional<Instant> aggregatedAt, Map<Provider, ProviderOutcome> lastOutcomes)`.

### Outbound ports (`domain/port/out`)

```java
public interface FlightProviderPort {
    Provider provider();
    List<Offer> fetch(SearchCriteria criteria);   // throws ProviderUnavailableException
}

public interface ItineraryRepositoryPort {
    int upsertAll(Collection<Itinerary> itineraries);   // returns rows written; cheaper-wins semantics on key conflict
    ItineraryPage findBy(SearchCriteria criteria, ItineraryFilter filter, Optional<PageCursor> cursor, int limit);
}

public interface FreshnessPort {
    Freshness status(SearchCriteria criteria);
    boolean tryLock(SearchCriteria criteria, Duration ttl);
    void release(SearchCriteria criteria);
    void markFresh(SearchCriteria criteria, AggregationResult result, Duration ttl);
    Optional<AggregationResult> lastResult(SearchCriteria criteria);
}
```

Plain interfaces, no annotations, domain types only. Update `domain-model.md` §8 so it matches exactly what you implement (it is the contract; keep them in sync).

## Tests

Light: `SearchCriteria` rejects same origin/destination; `AggregationResult.allFailed()/anySucceeded()`; `ItineraryFilter.none()` has all empty. Interfaces need no tests.

## Acceptance

- `./mvnw -q -pl domain test` green.
- `domain-model.md` §8 updated and consistent with code.
- `domain-guardian` has no findings on vocabulary (no `Flight`, `Trip`, `Route`…).

## Closing

`/check-arch`, `/log-ai-decision` (record the Optional-in-records decision and the Freshness addition), show diff summary. This closes Phase A.
