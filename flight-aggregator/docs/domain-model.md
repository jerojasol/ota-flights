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

The consistency boundary is the whole journey: validity depends on the relationship between segments, and price belongs to the journey, not to individual segments. Segments are never addressed independently, so they are Value Objects inside the aggregate.

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

A violation of S1–S3 or I1–I9 does not throw inside the aggregation pipeline: the Offer is **rejected** with a typed reason (see section 6). Direct construction of an invalid `Itinerary` or `Segment` outside the pipeline throws `InvalidItineraryException` / `InvalidSegmentException`.

Exceptions: every type throws its own unchecked `DomainException` subtype: `InvalidAirportException` (A1), `InvalidCarrierException` (C1), `InvalidFlightNumberException` (F1), `InvalidPriceException` (P1–P4, including cross-currency arithmetic), `InvalidSegmentException` (S1–S3), `InvalidOfferException` (null provider/price, null or empty segments), `InvalidItineraryKeyException` (empty segments, malformed stored key), `InvalidItineraryException` (I1–I12).

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

Complexity requirement: O(n) in the number of offers (hash-based grouping). No pairwise comparison.

## 6. Validation outcomes

Validation is total (never throws) inside the pipeline and returns a sealed result:

```
sealed interface OfferValidation
  record Valid(Offer offer)
  record Rejected(Offer offer, RejectionReason reason)

enum RejectionReason
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

## 7. Domain Services

| Service | Responsibility | Inputs → Outputs |
|---|---|---|
| `OfferValidator` | Apply S1–S3, I1–I9 to an Offer | `Offer → OfferValidation` |
| `ItineraryDeduplicator` | Apply DeduplicationPolicy | `Collection<Offer> → Collection<Offer>` (one per key) |
| `ItineraryFactory` | Build an `Itinerary` from a winning Offer + MarkupPolicy | `(Offer, MarkupPolicy) → Itinerary` |

All three are stateless, framework-free, and composed by the application-layer `AggregateFlightsService`.

## 8. Ports

### Inbound (use cases, `domain/port/in`)
```
AggregateFlightsUseCase
  AggregationResult aggregate(SearchCriteria criteria)

SearchItinerariesUseCase
  ItineraryPage search(ItineraryFilter filter, PageCursor cursor, int limit)
```

### Outbound (`domain/port/out`)
```
FlightProviderPort
  Provider provider()
  List<Offer> fetch(SearchCriteria criteria)   // throws ProviderUnavailableException

ItineraryRepositoryPort
  void upsertAll(Collection<Itinerary> itineraries)   // keep cheaper on key conflict
  ItineraryPage findBy(ItineraryFilter filter, PageCursor cursor, int limit)
```

Supporting types:
```
SearchCriteria   (Airport origin, Airport destination, LocalDate departureDate)
ItineraryFilter  (Optional<Airport> origin, Optional<Airport> destination,
                  Optional<Price> maxPrice, Optional<Carrier> carrier)
PageCursor       (opaque; encodes last finalPrice + last id)   — see SOLUTION.md for keyset rationale
ItineraryPage    (List<Itinerary> items, Optional<PageCursor> next)
AggregationResult(int persisted, Map<Provider, ProviderOutcome> outcomes)
ProviderOutcome  (ProviderStatus status, int offersReceived, int offersRejected, Duration elapsed)
```

## 9. Aggregation pipeline (application layer)

```
SearchCriteria
   │
   ├─ fan-out ──> FlightProviderPort(ALPHA) ─┐
   ├─ fan-out ──> FlightProviderPort(BETA)  ─┼─> List<Offer> + per-provider outcome
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
- Timeouts and circuit breaker configuration belong to infrastructure. The application layer only sees `ProviderUnavailableException` with a `ProviderStatus`.

## 10. Out of scope (explicit)

- Currency conversion: all providers are assumed to quote in the same currency per search. Mixed currencies within one aggregation are rejected at the adapter level.
- Cabin class, baggage, fare rules.
- Fuzzy deduplication (near-identical itineraries with slightly different times).
- Booking, availability decrement, seat maps.
- Multi-city or open-jaw itineraries (origin/destination pairs only).

## 11. Glossary cross-reference

Terms used here are defined in `AGENTS.md` section 5. If a term appears in code that is not in either document, it is a modelling bug.
