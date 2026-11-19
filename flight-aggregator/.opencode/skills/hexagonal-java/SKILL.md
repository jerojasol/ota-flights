---
name: hexagonal-java
description: How to write ports, adapters, use cases and JPA↔domain mappers in flight-aggregator so the domain stays framework-free. Use when adding or changing anything in domain/, application/ or an adapter in infrastructure/.
---

# Hexagonal Java — flight-aggregator conventions

Read `AGENTS.md` §3–§5 and `docs/domain-model.md` before using this skill. This skill shows *how*; those documents say *what*.

## Dependency direction (never inverted)

```
infrastructure ──> application ──> domain
```

The domain defines the interfaces (ports). Infrastructure implements them. Application orchestrates them. Spring wires them — in infrastructure only.

## 1. Value object

```java
package io.github.jerojasol.flights.domain.model;

import io.github.jerojasol.flights.domain.exception.InvalidAirportException;

public record Airport(String code) {

    private static final java.util.regex.Pattern IATA = java.util.regex.Pattern.compile("[A-Z]{3}");

    public Airport {
        if (code == null || !IATA.matcher(code).matches()) {
            throw new InvalidAirportException(code);
        }
    }

    public static Airport of(String code) {
        return new Airport(code == null ? null : code.trim().toUpperCase());
    }
}
```

- Validation in the compact constructor. Normalisation (trim/uppercase) in a static factory, so the constructor stays strict.
- Domain exceptions extend `DomainException` (unchecked), live in `domain.exception`. Other domain types go in `domain.model`, `domain.service`, `domain.search` or `domain.aggregation` (see `docs/domain-model.md` §7 "Package layout").
- No Jackson, no JPA, no Bean Validation annotations.

## 2. Aggregate root with derived state

```java
public final class Itinerary {
    private final ItineraryKey key;         // derived, never injected (I12)
    private final List<Segment> segments;   // immutable copy
    private final Price basePrice;
    private final Price finalPrice;         // derived via MarkupPolicy (I10)
    private final Provider source;

    private Itinerary(...) { ... }

    public static Itinerary from(Offer winning, MarkupPolicy markup) {
        List<Segment> segs = List.copyOf(winning.segments());
        return new Itinerary(
            ItineraryKey.of(segs),
            segs,
            winning.basePrice(),
            markup.apply(winning.basePrice()),
            winning.provider());
    }

    public Airport origin()      { return segments.getFirst().departureAirport(); }
    public Airport destination() { return segments.getLast().arrivalAirport(); }
    // behaviour, not just getters:
    public Duration totalDuration() { ... }
    public int connections()        { return segments.size() - 1; }
}
```

Private constructor + static factory. The factory is the only way to build one, and it is where I10/I12 are guaranteed.

## 3. Port (domain)

```java
package io.github.jerojasol.flights.domain.port.out;

public interface FlightProviderPort {
    Provider provider();
    List<Offer> fetch(SearchCriteria criteria);   // throws ProviderUnavailableException
}
```

Plain interface. Domain types only. No `@Component`, no Spring types, no `CompletableFuture` in the signature — concurrency is the application layer's concern.

Inbound ports (`domain.port.in`) are the use-case interfaces the controllers call:

```java
public interface SearchItinerariesUseCase {
    ItineraryPage search(ItineraryFilter filter, Optional<PageCursor> cursor, int limit);
}
```

## 4. Use case (application)

```java
package io.github.jerojasol.flights.application;

public final class SearchItinerariesService implements SearchItinerariesUseCase {
    private final ItineraryRepositoryPort repository;
    private final FreshnessPort freshness;
    private final AggregateFlightsUseCase aggregate;

    public SearchItinerariesService(ItineraryRepositoryPort repository,
                                    FreshnessPort freshness,
                                    AggregateFlightsUseCase aggregate) { ... }

    @Override
    public ItineraryPage search(...) { ... }
}
```

- Plain class, constructor injection, no annotations. `final`.
- Pure JDK for concurrency (`CompletableFuture`, `Executor` passed in through the constructor).
- Unit-testable with fakes of the ports; no Spring context.

## 5. Wiring (infrastructure/config)

```java
@Configuration
class UseCaseConfiguration {

    @Bean
    SearchItinerariesUseCase searchItinerariesUseCase(ItineraryRepositoryPort repo,
                                                      FreshnessPort freshness,
                                                      AggregateFlightsUseCase aggregate) {
        return new SearchItinerariesService(repo, freshness, aggregate);
    }
}
```

This is the only place where application classes meet Spring. Keep one configuration class per concern (`UseCaseConfiguration`, `ProviderConfiguration`, `PersistenceConfiguration`, `CacheConfiguration`).

## 6. Outbound adapter: persistence

Two classes, never one:

```
infrastructure/persistence/
├── ItineraryJpaEntity.java        @Entity — JPA's view of the table
├── ItineraryJpaRepository.java    Spring Data interface (reads)
├── ItineraryJdbcUpserter.java     JdbcTemplate batch upsert (writes)
├── ItineraryPersistenceMapper.java  entity <-> domain
└── ItineraryRepositoryAdapter.java  implements ItineraryRepositoryPort
```

- `ItineraryJpaEntity` is package-private. It never leaves `persistence`.
- The adapter implements the domain port and delegates: JPA for keyset reads, `JdbcTemplate.batchUpdate` with `ON CONFLICT (itinerary_key) DO UPDATE SET base_price, final_price, currency, source, segments, ingested_at = EXCLUDED.*` for writes.
- **Do not add a `WHERE` to that `DO UPDATE`.** The conflict update is unconditional on purpose: the table holds the price the providers quote now, not the lowest ever seen. `ItineraryDeduplicator` has already picked the cheapest Offer across providers before the repository is called, so a guard such as `WHERE excluded.final_price < itineraries.final_price` could only compare against an earlier aggregation — and would freeze the row at a stale price that no provider honours. A currency guard is equally wrong: `OfferValidator` rejects anything outside the reference currency (O1), so a batch is single-currency, and the guard would strand rows in the old currency after a `REFERENCE_CURRENCY` change. See `domain-model.md` §8.
- The mapper is a hand-written class; `segments` go to/from JSONB via Jackson *inside the adapter*.

## 7. Outbound adapter: provider (ACL)

```
infrastructure/provider/beta/
├── BetaClient.java             RestClient call, returns BetaSearchResponse
├── BetaSearchResponse.java     Jackson XML DTOs — Beta's vocabulary (Leg, Pricing, Total…)
├── BetaOfferMapper.java        BetaSearchResponse -> List<Offer>, normalises time zones, flight numbers
└── BetaProviderAdapter.java    implements FlightProviderPort; wraps client+mapper in Resilience4j decorators
```

- Provider DTOs use the provider's names on purpose. The *mapper* is where translation to the Ubiquitous Language happens. Beta's `Leg` becomes the domain's `Segment` there and nowhere else.
- DTOs are package-private.
- Resilience4j decorators (`TimeLimiter`, `CircuitBreaker`, `Retry`) are applied in the adapter programmatically. On failure the adapter throws `ProviderUnavailableException(provider, ProviderStatus)`; it never returns `null` or a partial list.

## 8. Inbound adapter: web

```
infrastructure/web/
├── ItineraryController.java      depends ONLY on domain.port.in + web.dto
├── dto/                          request/response records (Jackson-friendly)
├── mapper/ItineraryResponseMapper.java
├── CursorCodec.java              Base64 <-> PageCursor
└── error/ApiExceptionHandler.java  DomainException -> ProblemDetail
```

Controller = translate in, call use case, translate out. If you are tempted to put an `if` with business meaning in a controller, it belongs in a use case.

## 9. ArchUnit rules that back all of this

Keep `ArchitectureTest` in `infrastructure/src/test` with at least:

- `domain..` depends only on `java..` and `domain..`
- `application..` depends only on `java..`, `domain..`, `application..`
- `..infrastructure.web..` does not depend on `..persistence..`, `..provider..`, `..cache..`
- Classes named `*Controller` depend only on `..domain.port.in..`, `..domain.model..`, `..domain.search..`, `..domain.aggregation..`, `..infrastructure.web..`, Spring, `java..`, `jakarta.validation..`
- `..persistence..` entities (`@Entity`) are not accessed from outside `..persistence..`
- Provider DTO packages are not accessed from outside their own adapter package

## Checklist before finishing a change

- [ ] New domain type exists in `docs/domain-model.md`; if not, update the doc first.
- [ ] `./mvnw -q -pl domain,application test` passes.
- [ ] `./mvnw -q -pl infrastructure test -Dtest=ArchitectureTest` passes.
- [ ] Run the `domain-guardian` subagent if you touched `domain/` or `application/`.
