# Domain Model

Source of truth for the core domain. Any code in `domain/` must be traceable to something in this document. Changes to business rules start here, then propagate to code and tests.

## 1. Bounded Context

A single bounded context: **Flight Aggregation**. It owns the concepts of Offer, Itinerary, deduplication, pricing markup and validity. It does *not* own booking, payment, user accounts or provider contracts.

Upstream contexts (external): the three GDS Providers. Their models are translated at the boundary by Anti-Corruption Layers (the provider adapters). Provider vocabulary never enters the domain.

## 2. Aggregates, Entities and Value Objects

```
Itinerary (Aggregate Root)
 ├── ItineraryKey        VO  — identity for deduplication
 ├── Airport origin      VO
 ├── Airport destination VO
 ├── List<Segment>       VO  — ordered, 1..N
 ├── Price basePrice     VO
 ├── Price finalPrice    VO
 ├── Provider source     Enum — which provider supplied the winning price
 └── Instant ingestedAt

Segment (VO)
 ├── Carrier carrier
 ├── FlightNumber flightNumber
 ├── Airport departureAirport
 ├── Instant departureAt
 ├── Airport arrivalAirport
 └── Instant arrivalAt

Offer (VO) — pre-deduplication input
 ├── Provider provider
 ├── List<Segment> segments
 └── Price basePrice

Airport (VO)           — IATA 3-letter code, uppercase
Carrier (VO)           — IATA 2-letter code, uppercase
FlightNumber (VO)      — Carrier + 1..4 digit number (e.g. IB6251)
Price (VO)             — BigDecimal amount (scale 2) + Currency
ItineraryKey (VO)      — see section 4
Provider (Enum)        — ALPHA, BETA, GAMMA
ProviderStatus (Enum)  — OK, TIMEOUT, ERROR, CIRCUIT_OPEN
```

### Why `Itinerary` is the aggregate root

The consistency boundary is the whole Itinerary: validity depends on the relationship between segments, and price belongs to the Itinerary, not to individual segments. Segments are never addressed independently, so they are Value Objects inside the aggregate.

### Why `Offer` and `Itinerary` are different types

An Offer is what a provider says. An Itinerary is what the OTA sells. The transformation between them (validation, deduplication, markup) is the core of the domain, so the two sides are kept as distinct types to make the pipeline explicit and testable.

## 3. Invariants

Each invariant must be enforced in the constructor/factory of the owning type and covered by at least one unit test.

### Airport
- A1. Code is exactly 3 uppercase ASCII letters.

### Carrier
- C1. Code is exactly 2 uppercase ASCII alphanumerics.

### FlightNumber
- F1. Composed of a valid Carrier code followed by a number from 1 to 9999, written with 1 to 4 digits; leading zeros are not significant (`IB117` and `IB0117` are the same flight number).

### Price
- P1. Amount is non-negative.
- P2. Amount has scale 2 after construction (rounding HALF_EVEN).
- P3. Currency is a valid ISO 4217 code.
- P4. Arithmetic between prices of different currencies is rejected.

### Segment
- S1. `departureAirport != arrivalAirport` (otherwise it is a Dead Leg).
- S2. `arrivalAt` is strictly after `departureAt` (otherwise it is a Dead Leg).
- S3. Duration does not exceed 20 hours (sanity bound; longer values are treated as data errors).

### Offer
- O1. `basePrice.currency` equals the **reference currency** of the aggregation. The reference currency is a constructor argument of `OfferValidator` (infrastructure configures it; the domain never reads configuration). `Offer` itself accepts any currency: O1 is a pipeline rule, enforced by the validator, not by the `Offer` constructor.

### ProviderResponse
- R1. `offers` is non-null and defensively copied.
- R2. `unmappable` is not negative.

### ProviderOutcome
- PO1. `status` is non-null, `offersReceived` and `offersRejected` are not negative, and `elapsed` is non-null and not negative.

### Itinerary
- I1. Has at least one Segment.
- I2. Has at most 4 Segments (3 connections).
- I3. `origin` equals the first segment's `departureAirport`; `destination` equals the last segment's `arrivalAirport`.
- I4. `origin != destination`.
- I5. For every consecutive pair (N, N+1): `segment[N].arrivalAirport == segment[N+1].departureAirport`.
- I6. For every consecutive pair: `segment[N+1].departureAt > segment[N].arrivalAt`.
- I7. For every consecutive pair: connection time is at least **MIN_CONNECTION** (45 minutes).
- I8. For every consecutive pair: connection time is at most **MAX_CONNECTION** (24 hours).
- I9. Total elapsed time (first departure to last arrival) does not exceed 72 hours.
- I10. `finalPrice` is derived from `basePrice` via the Markup Policy; it is never set directly.
- I11. `finalPrice.currency == basePrice.currency`.
- I12. `key` is derived from segments; it is never supplied externally.

A violation of O1, S1–S3 or I1–I9 does not throw inside the aggregation pipeline: the Offer is **rejected** with a typed reason (see section 6). Direct construction of an invalid `Itinerary` or `Segment` outside the pipeline throws `InvalidItineraryException` / `InvalidSegmentException`.

Exceptions: every type throws its own unchecked `DomainException` subtype: `InvalidAirportException` (A1), `InvalidCarrierException` (C1), `InvalidFlightNumberException` (F1), `InvalidPriceException` (P1–P4, including cross-currency arithmetic), `InvalidSegmentException` (S1–S3), `InvalidOfferException` (null provider/price, null or empty segments, validator built without a reference currency), `InvalidItineraryKeyException` (empty segments, malformed stored key), `InvalidItineraryException` (I1–I12), `InvalidSearchException` (search criteria, filter, cursor, page, search result and lock token), `InvalidAggregationException` (provider outcome, aggregation result, unavailable-provider status).

### Rehydration

`Itinerary.rehydrate(key, segments, basePrice, finalPrice, provider, ingestedAt)` exists only for the persistence adapter. It re-checks structure (I1–I9) and currency (I11) but **trusts the stored key and prices**: I10 and I12 are guaranteed by `Itinerary.from` when the row is written, and re-deriving on read would silently reprice history if the markup percentage changed. This is the one deliberate exception to I10/I12.

`MAX_SEGMENTS` (4), `MIN_CONNECTION` (45 min), `MAX_CONNECTION` (24 h) and `MAX_TOTAL_DURATION` (72 h) are constants on `Itinerary`.

## 4. Itinerary Key (deduplication identity)

Two offers represent the same product when they have the same:

1. Ordered list of flight numbers (carrier + number)
2. Ordered list of departure instants (UTC, minute precision)
3. Ordered list of airports visited (origin, each connection, destination)

Deliberately **excluded** from the key: price, provider, arrival times (providers disagree on arrival rounding), aircraft type, cabin class (out of scope).

Representation: a SHA-256 hex string of the canonical form
```
IB6251@2026-11-20T07:35Z>MAD-JFK|IB4120@2026-11-20T14:10Z>JFK-MIA
```
Canonical form is built deterministically from the segments so that equality is structural. The key is a Value Object with `equals`/`hashCode` on the hash string.

## 5. Policies

### MarkupPolicy
Interface in the domain: `Price apply(Price basePrice)`.

Default implementation `PercentageMarkupPolicy(5%)`:
```
final = base × (1 + 0.05), rounded HALF_EVEN to scale 2
```
The percentage is a constructor argument so tests can use other values and infrastructure can configure it. The domain never reads configuration directly.

### DeduplicationPolicy
Given a collection of valid Offers:
1. Group by `ItineraryKey`.
2. In each group select the Offer with the lowest `basePrice`.
3. Tie-break: deterministic by `Provider` enum order (ALPHA < BETA < GAMMA) so results are reproducible.
4. Record which provider supplied the winning price (`Itinerary.source`).

Complexity requirement: grouping is O(n) in the number of offers (hash-based), with no pairwise comparison. The output is the k distinct survivors sorted by key string (O(k log k)), so it is reproducible. Same provider and price: the earlier final arrival wins. All offers share one currency: O1 is checked before deduplication (§10).

## 6. Validation outcomes

Validation is total (never throws) inside the pipeline and returns a sealed result:

```
sealed interface OfferValidation
  record Valid(Offer offer)
  record Rejected(Offer offer, RejectionReason reason)

enum RejectionReason
  CURRENCY_MISMATCH             (O1)
  DEAD_LEG_SAME_AIRPORT         (S1)
  DEAD_LEG_NON_POSITIVE_DURATION (S2)
  SEGMENT_DURATION_EXCEEDED     (S3)
  NO_SEGMENTS                   (I1)
  TOO_MANY_SEGMENTS             (I2)
  ORIGIN_EQUALS_DESTINATION     (I4)
  AIRPORT_MISMATCH_AT_CONNECTION (I5)
  CONNECTION_OVERLAP            (I6)
  CONNECTION_TOO_SHORT          (I7)
  CONNECTION_TOO_LONG           (I8)
  TOTAL_DURATION_EXCEEDED       (I9)
```

Rejected offers are counted and reported per provider in the aggregation result; they are not persisted.

Which component raises which reason:
- `OfferValidator` raises CURRENCY_MISMATCH, TOO_MANY_SEGMENTS, ORIGIN_EQUALS_DESTINATION, AIRPORT_MISMATCH_AT_CONNECTION, CONNECTION_OVERLAP, CONNECTION_TOO_SHORT, CONNECTION_TOO_LONG and TOTAL_DURATION_EXCEEDED.
- NO_SEGMENTS is never raised by the validator, because `Offer` rejects empty lists; it only surfaces as `InvalidItineraryException` from `Itinerary.rehydrate`.
- DEAD_LEG_SAME_AIRPORT, DEAD_LEG_NON_POSITIVE_DURATION and SEGMENT_DURATION_EXCEEDED are enforced by the `Segment` constructor, so an `Offer` can never hold such a segment. An adapter that fails to build a `Segment` skips that itinerary and counts it in `ProviderResponse.unmappable`; per-reason counts are not reported for unmappable itineraries.
- Order of checks (first failure wins): O1 in `OfferValidator`, then one shared implementation in `Itinerary`: I1, I2, I4, then for each consecutive pair in segment order I5, I6, I7, I8, and finally I9.
- `OfferValidator.validateAll` returns a `ValidationSummary(valid, rejectedByReason)`.

## 7. Domain Services

| Service | Responsibility | Inputs → Outputs |
|---|---|---|
| `OfferValidator` | Apply O1, then I2, I4–I9 to an Offer (S1–S3 are enforced by `Segment`, I1 by `Offer`). Holds the reference currency | `Offer → OfferValidation` |
| `ItineraryDeduplicator` | Apply DeduplicationPolicy | `Collection<Offer> → Collection<Offer>` (one per key) |
| `ItineraryFactory` | Build an `Itinerary` from a winning Offer + MarkupPolicy | `(Offer) → Itinerary` (holds the `MarkupPolicy` and a `Clock` for `ingestedAt`; `Itinerary.from(Offer, MarkupPolicy, Instant)` is the underlying factory) |

All three hold no mutable state, are framework-free, and composed by the application-layer `AggregateFlightsService`.

### Package layout

Under `io.github.jerojasol.flights.domain`:

| Package | Purpose | Types |
|---|---|---|
| `model` | What is sold, and the rules it carries | `Itinerary`, `Segment`, `Offer`, `ItineraryKey`, `Airport`, `Carrier`, `FlightNumber`, `Price`, `Provider`, `RejectionReason`, `MarkupPolicy`, `PercentageMarkupPolicy` |
| `service` | The three steps of the pipeline and their results | `OfferValidator`, `OfferValidation`, `ValidationSummary`, `ItineraryDeduplicator`, `ItineraryFactory` |
| `search` | What goes into and comes out of a search | `SearchCriteria`, `ItineraryFilter`, `PageCursor`, `ItineraryPage`, `SearchResult`, `Freshness`, `LockToken` |
| `aggregation` | The outcome of calling the providers | `AggregationResult`, `ProviderOutcome`, `ProviderResponse`, `ProviderStatus` |
| `exception` | `DomainException` and every subclass | `DomainException`, `InvalidAggregationException`, `InvalidAirportException`, `InvalidCarrierException`, `InvalidFlightNumberException`, `InvalidItineraryException`, `InvalidItineraryKeyException`, `InvalidOfferException`, `InvalidPriceException`, `InvalidSearchException`, `InvalidSegmentException`, `ProviderUnavailableException` |
| `port.in`, `port.out` | Ports (§8) | Use cases, repositories, providers |

Dependency direction (enforced by `ArchitectureTest`):

```
service      ──> model
search       ──> model, aggregation
aggregation  ──> model
model        ──> (none of the three above)
exception    <─> model, aggregation     (accepted: exceptions carry the types they report)
```

`MarkupPolicy`/`PercentageMarkupPolicy` and `RejectionReason` stay in `model` because `Itinerary` uses them directly. `Itinerary.findViolation` and `Itinerary.Violation` are `public`: `findViolation` is the single implementation of I1–I9, used by `Itinerary`'s own factories and by `service.OfferValidator`, which needs a result instead of an exception and now lives in another package.

## 8. Ports

Types below live in the packages listed in §7 "Package layout"; the ports live in `domain.port.in` and `domain.port.out`. Ports are plain interfaces over domain types.

### Inbound (use cases, `domain/port/in`)
```
AggregateFlightsUseCase
  AggregationResult aggregate(SearchCriteria criteria)

SearchItinerariesUseCase
  SearchResult search(SearchCriteria criteria, ItineraryFilter filter,
                      Optional<PageCursor> cursor, int limit)

RefreshItinerariesUseCase
  Optional<AggregationResult> refresh(SearchCriteria criteria)
```

`RefreshItinerariesUseCase` is the on-demand refresh (e.g. an operator's request). It follows the same lock and freshness rules as a search's cold miss: it aggregates only while holding the aggregation lock, marks the criteria fresh only if at least one Provider succeeded, and always releases the lock. It returns empty when the lock cannot be taken (another aggregation is running, or the freshness store is unavailable, see Freshness below); nothing is aggregated in that case.

### Outbound (`domain/port/out`)
```
FlightProviderPort
  Provider provider()
  ProviderResponse fetch(SearchCriteria criteria)   // throws ProviderUnavailableException

ItineraryRepositoryPort
  int upsertAll(Collection<Itinerary> itineraries)   // rows written; cheaper Final Price wins on key conflict
  ItineraryPage findBy(SearchCriteria criteria, ItineraryFilter filter,
                       Optional<PageCursor> cursor, int limit)

FreshnessPort
  Freshness status(SearchCriteria criteria)
  Optional<LockToken> tryLock(SearchCriteria criteria, Duration ttl)   // empty when another caller holds the lock
  void release(SearchCriteria criteria, LockToken token)             // no-op unless token is the current holder
  void markFresh(SearchCriteria criteria, AggregationResult result, Duration ttl)
  Optional<AggregationResult> lastResult(SearchCriteria criteria)
```

### Supporting types
```
SearchCriteria    (Airport origin, Airport destination, LocalDate departureDate)   — origin != destination
ItineraryFilter   (Optional<Airport> origin, Optional<Airport> destination,
                   Optional<Price> maxPrice, Optional<Carrier> carrier)
                  none(), withOrigin/withDestination/withMaxPrice/withCarrier (a null argument clears that component)
PageCursor        (Price lastFinalPrice, ItineraryKey lastKey)   — opaque to callers; string encoding is a web-adapter
                  concern. Pages are ordered by (final price, key); the key is unique, so the position is unambiguous.
                  See SOLUTION.md for keyset rationale
ItineraryPage     (List<Itinerary> items, Optional<PageCursor> next)   — ItineraryPage.empty()
SearchResult      (ItineraryPage page, Freshness freshness, Optional<Instant> aggregatedAt,
                   Map<Provider, ProviderOutcome> lastOutcomes)
AggregationResult (int persisted, Map<Provider, ProviderOutcome> outcomes, Instant aggregatedAt)
                  allFailed(): no outcome is OK;  anySucceeded(): at least one outcome is OK
ProviderOutcome   (ProviderStatus status, int offersReceived, int offersRejected, Duration elapsed)   — succeeded() == (status is OK)
ProviderResponse  (List<Offer> offers, int unmappable)   — what one provider call delivered. `unmappable` counts the
                  provider's itineraries the adapter could not turn into an Offer: a Segment broke S1–S3, or a field
                  was malformed or missing (A1, C1, F1, P1, P3, null entries, unparseable timestamps or amounts).
                  One unmappable itinerary never fails the provider's response; there is no per-reason breakdown. Invariants R1, R2 (§3).
                  ProviderResponse.of(offers) has unmappable = 0
ProviderStatus    Enum — OK, TIMEOUT, ERROR, CIRCUIT_OPEN
Freshness         Enum — FRESH, STALE, REFRESHING, MISSING
LockToken         (String value)   — non-blank; identifies one holder of the aggregation lock
ProviderUnavailableException (Provider provider, ProviderStatus status)   — status is never OK
```

`SearchResult` is a plain carrier: the relation between `freshness`, `aggregatedAt` and `lastOutcomes` is decided by the application layer, not enforced by the type. `limit` is validated by the application layer (the domain only passes it through).

### Freshness (read-path state)

State of the data for one `SearchCriteria`, as seen by `SearchItinerariesUseCase`:

| Value | Meaning | Read-path behaviour |
|---|---|---|
| `FRESH` | Aggregated recently, within the freshness TTL | Serve from the repository only |
| `STALE` | Data exists but the TTL expired, no refresh running | Serve existing data, trigger a background refresh |
| `REFRESHING` | A refresh is already running (lock held) | Serve existing data, do not start another |
| `MISSING` | Never aggregated for this criteria | Aggregate synchronously, then query |

If the freshness store is unavailable, `status` reports `STALE` and `tryLock` is empty (fail closed): searches serve the repository, nothing is aggregated or refreshed, and a never-aggregated route returns an empty page. This is a deliberate exception to the table above; see `SOLUTION.md` §6 at the repository root.

A request that carries a `PageCursor` never triggers aggregation (pages must stay consistent with page 1).

### Aggregation lock ownership

The aggregation lock for one `SearchCriteria` expires after its TTL so a crashed holder cannot block refreshes forever. An aggregation can therefore outlive its lock, and another caller can acquire it in the meantime. To keep the first caller from deleting the second caller's lock, `tryLock` returns a `LockToken` that identifies the holder, and `release` only removes the lock when the token still matches (compare-and-delete in the adapter). Releasing an expired or foreign lock is a silent no-op.

An aggregation in which every Provider failed is not marked fresh, so the next search retries; `lastOutcomes` prefers the aggregation just performed over a stored result.

### Optional convention

`AGENTS.md` §6 says "no `Optional` as field or parameter type". The ports and query/result records in this section are the agreed exception: `Optional` is used for `PageCursor` parameters and for optional components of `ItineraryFilter`, `ItineraryPage` and `SearchResult`, because they are boundary carriers where absence is part of the contract and a nullable alternative would leak `null` across module boundaries (also forbidden). Compact constructors reject a `null` `Optional`. Everywhere else the rule stands.

## 9. Aggregation pipeline (application layer)

```
SearchCriteria
   │
   ├─ fan-out ──> FlightProviderPort(ALPHA) ─┐
   ├─ fan-out ──> FlightProviderPort(BETA)  ─┼─> ProviderResponse + per-provider outcome
   └─ fan-out ──> FlightProviderPort(GAMMA) ─┘
                                   │
                           OfferValidator  (reject invalid, count reasons)
                                   │
                        ItineraryDeduplicator  (cheapest per key)
                                   │
                          ItineraryFactory  (apply MarkupPolicy)
                                   │
                     ItineraryRepositoryPort.upsertAll
                                   │
                           AggregationResult
```

Resilience rules the application layer must honour:
- A provider failure (timeout, error, circuit open) produces an `outcome` for that provider and an empty offer list. It never aborts the pipeline.
- The aggregation succeeds if at least one provider returned OK. If all three fail, the result has zero persisted itineraries and all outcomes non-OK; this is still a normal result, not an exception.
- Itineraries an adapter could not map (`ProviderResponse.unmappable`) count as received and rejected for that provider: `offersReceived = offers + unmappable`, `offersRejected = OfferValidator rejections + unmappable`. Dead legs are therefore counted per provider even though they never become an Offer.
- Timeouts and circuit breaker configuration belong to infrastructure. The application layer only sees `ProviderUnavailableException` with a `ProviderStatus`.

## 10. Out of scope (explicit)

- Currency conversion: an aggregation has one reference currency. An Offer quoted in any other currency is rejected by `OfferValidator` with CURRENCY_MISMATCH (O1) and counted against its provider, so the `ItineraryDeduplicator` never compares prices of different currencies.
- Cabin class, baggage, fare rules.
- Fuzzy deduplication (near-identical itineraries with slightly different times).
- Booking, availability decrement, seat maps.
- Multi-city or open-jaw itineraries (origin/destination pairs only).

## 11. Glossary cross-reference

Terms used here are defined in `AGENTS.md` section 5. If a term appears in code that is not in either document, it is a modelling bug.
