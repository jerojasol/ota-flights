# A2 — Segment, Offer, ItineraryKey

Load the `hexagonal-java` and `testing-conventions` skills. Read `docs/domain-model.md` §2, §3 (Segment invariants S1–S3) and §4 (Itinerary Key). The value objects from step A1 already exist; reuse them and `Fixtures`.

## Task

1. `Segment` (record): `FlightNumber flightNumber`, `Airport departureAirport`, `Instant departureAt`, `Airport arrivalAirport`, `Instant arrivalAt`. Enforce S1–S3 in the constructor (throw `InvalidSegmentException`). Add `duration()`.
   - The carrier is derived from `flightNumber.carrier()`; do not store it twice.

2. `Provider` enum: `ALPHA, BETA, GAMMA` in that order (the order is the dedup tie-break, document it in a comment).

3. `Offer` (record): `Provider provider`, `List<Segment> segments`, `Price basePrice`. Defensive copy of the list. No validation of itinerary-level rules here (that is `OfferValidator`'s job in A4); only non-null and non-empty list.

4. `ItineraryKey` (record wrapping a `String` hash):
   - Static factory `ItineraryKey.of(List<Segment>)`.
   - Canonical form exactly as in §4: `IB6251@2026-11-20T07:35Z>MAD-JFK|IB4120@...`, departure truncated to minutes, flight number normalised (no leading zeros), segments in order.
   - SHA-256 of the canonical form, lowercase hex.
   - Expose `canonical()` for debugging/tests and `value()` for persistence.
   - No public constructor from a raw string except a `fromStored(String)` factory for the persistence adapter, clearly documented as not validating.

## Tests

- Segment: S1, S2, S3 each with a failing case; one valid case; `duration()`.
- ItineraryKey:
  - same segments, different providers/prices → equal keys.
  - same flights, departure differs by one minute → different keys.
  - same flights, arrival differs → equal keys (arrival excluded).
  - `IB117` vs `IB0117` → equal keys.
  - segment order swapped → different keys.
  - canonical string for a known two-segment itinerary matches the literal in `domain-model.md` §4.
- Offer: rejects null provider, null/empty segments, null price.

## Acceptance

- `./mvnw -q -pl domain test` green.
- No `java.security.MessageDigest` leaks into the public API; it is an implementation detail of `ItineraryKey`.

## Closing

`/check-arch`, fix findings, `/log-ai-decision`, show diff summary.
