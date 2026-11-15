# A4 — OfferValidator, ItineraryDeduplicator

Load the `hexagonal-java` and `testing-conventions` skills. Read `docs/domain-model.md` §5 (DeduplicationPolicy), §6 (validation outcomes) and §7 (domain services).

## Task

1. `OfferValidation` sealed interface in `domain/service` (or `domain/model`):
   ```
   sealed interface OfferValidation permits Valid, Rejected
   record Valid(Offer offer)
   record Rejected(Offer offer, RejectionReason reason)
   ```
   `RejectionReason` already exists from A3; make sure it has every value listed in §6.

2. `OfferValidator` domain service, stateless:
   - `OfferValidation validate(Offer offer)` — **total**: never throws. Checks S1–S3 per segment (segments already enforce these in their constructor, so document that the segment-level reasons can only occur if an adapter bypasses `Segment` validation — keep the branch anyway for completeness, or drop the segment-level reasons from the enum and document why; decide and record the decision in the ai-log).
   - Then checks I1, I2, I4, I5, I6, I7, I8, I9 using the constants from `Itinerary`.
   - Returns the **first** failing reason in the documented order.
   - Convenience: `ValidationSummary validateAll(Collection<Offer>)` returning `List<Offer> valid` and `Map<RejectionReason, Integer> rejectedByReason`.

3. `ItineraryDeduplicator` domain service, stateless:
   - `Collection<Offer> deduplicate(Collection<Offer> validOffers)`.
   - Groups by `ItineraryKey.of(offer.segments())` using a `HashMap` — O(n). No nested loops.
   - Per group keeps the lowest `basePrice`; tie → lowest `Provider` ordinal.
   - Returns offers in a deterministic order (by key string) so tests and persistence are reproducible.
   - Optionally returns a `DeduplicationStats(int input, int output)`; keep it simple.

## Tests

- `OfferValidator`:
  - one valid single-segment and one valid two-segment offer → `Valid`.
  - one failing case per I-reason listed in §6 → `Rejected` with the exact reason.
  - when two rules fail, the first in order wins (e.g. I4 before I5).
  - `validateAll` counts per reason correctly and never throws.
- `ItineraryDeduplicator`:
  - same itinerary from ALPHA (520) and BETA (512.30) → one offer, BETA's price.
  - equal prices from GAMMA and ALPHA → ALPHA wins.
  - three distinct itineraries → three offers.
  - 10,000 generated offers with 1,000 distinct keys complete in well under a second (sanity, not a benchmark; tag `@Tag("sanity")`).
  - input order does not affect output.

## Acceptance

- `./mvnw -q -pl domain test` green.
- `domain-guardian`: no CRITICAL, dedup recognised as O(n), validation recognised as total.

## Closing

`/check-arch`, fix findings, `/log-ai-decision` (record the decision about segment-level reasons), show diff summary.
