# A3 — Itinerary aggregate, MarkupPolicy, ItineraryFactory

Load the `hexagonal-java` and `testing-conventions` skills. Read `docs/domain-model.md` §2 (aggregate root rationale), §3 (I1–I12), §5 (MarkupPolicy) and §7 (ItineraryFactory).

## Task

1. `MarkupPolicy` interface in `domain/policy` (or `domain/model`, be consistent with A1): `Price apply(Price basePrice)`.

2. `PercentageMarkupPolicy implements MarkupPolicy`: constructor takes `BigDecimal percentage` (e.g. `new BigDecimal("5")` for 5%). Rejects negative. `apply` = `base × (1 + pct/100)` rounded HALF_EVEN to scale 2 via `Price.multiply`.

3. `Itinerary` — the aggregate root. `final` class, private constructor, static factory only:
   - `static Itinerary from(Offer winning, MarkupPolicy markup)` — the only way to build one in the pipeline.
   - Fields: `ItineraryKey key` (derived, I12), `List<Segment> segments`, `Price basePrice`, `Price finalPrice` (derived, I10), `Provider source`, `Instant ingestedAt`.
   - Derived accessors: `origin()`, `destination()`, `connections()`, `totalDuration()`, `carriers()` (distinct, in order).
   - Enforce I1, I2, I3, I4, I5, I6, I7, I8, I9, I11 in the factory; throw `InvalidItineraryException` with a `RejectionReason` (create the enum now, as in §6, it will be reused by the validator in A4).
   - Connection constants `MIN_CONNECTION = 45 min`, `MAX_CONNECTION = 24 h`, `MAX_TOTAL_DURATION = 72 h` as `Duration` constants on `Itinerary` (single source for A4 too).
   - A second factory `static Itinerary rehydrate(ItineraryKey, List<Segment>, Price base, Price final, Provider, Instant)` for the persistence adapter: validates structure (I1–I9) but trusts the stored key and prices. Document why.
   - `equals/hashCode` on `key` only.

4. `ItineraryFactory` domain service: thin wrapper `Itinerary create(Offer winning)` holding the `MarkupPolicy`. Exists so the application layer depends on a service, not on a static method.

## Tests

- `PercentageMarkupPolicy`: `100.00`→`105.00`; `99.99`→`104.99`; `0.00`→`0.00`; `12.345`-style inputs are impossible because `Price` already has scale 2 — assert that; negative percentage rejected.
- `Itinerary`:
  - one test per invariant I1–I9 and I11 with a failing case (use `Fixtures` builders for multi-segment itineraries).
  - boundary cases: connection 44 min (reject), 45 min (accept), 24 h (accept), 24 h + 1 min (reject); total 72 h (accept), 72 h + 1 min (reject).
  - I10: `finalPrice` equals `markup.apply(basePrice)`.
  - I12: `key` equals `ItineraryKey.of(segments)`.
  - `origin()`/`destination()`/`connections()`/`carriers()`.
  - two itineraries from different providers with same segments are `equals`.
- `ItineraryFactory`: delegates and applies the policy.

## Acceptance

- `./mvnw -q -pl domain test` green.
- `domain-guardian` reports no CRITICAL findings and every invariant A1–I12 as `covered`.

## Closing

`/check-arch`, fix findings, `/log-ai-decision`, show diff summary.
