---
name: contract-sync
description: Procedure for changing a simulated provider's request or response format (Alpha JSON, Beta XML, Gamma CSV) without silently breaking flight-aggregator's adapters. Use whenever touching a controller, response type or query parameter in mock-providers.
---

# Contract Sync — mock-providers

`docs/provider-contracts.md` is a contract between two independently built projects. The aggregator's mappers and test fixtures are written against it. The mock controllers implement it. If either side drifts, `docker compose up` still boots and nothing fails loudly: the aggregator just silently receives zero offers from that provider, or misparses times and prices.

This skill exists to prevent that.

## Order of operations

1. **Document first.** Edit `docs/provider-contracts.md`. Update the example payload, the conventions table and, if relevant, the "Cross-provider semantics" section.
2. **Code second.** Change the controller / response type / generator in `mock-providers`.
3. **Fixtures third.** Update the corresponding files in `../flight-aggregator/infrastructure/src/test/resources/fixtures/<provider>/` so they match the new example byte-for-byte in structure. Then update the aggregator's mapper and its tests.
4. **Same commit (or same PR).** Never land step 2 without steps 1 and 3.

If you are only working in `mock-providers` and cannot touch the aggregator, stop after step 1 and 2 and leave a clear note in the commit message and in `../docs/ai-log.md` that the aggregator fixtures for `<provider>` are now stale.

## Checklist per provider

### Alpha (JSON)
- Query params remain `from`, `to`, `date` (`yyyy-MM-dd`).
- Timestamps ISO-8601 UTC with `Z`.
- `price` is a JSON number, `currency` ISO 4217.
- `flightNo` digits only; carrier separate.
- Empty result is `[]`.

### Beta (XML)
- Query params remain `origin`, `destination`, `departureDate`.
- Root `SearchResponse` with namespace `urn:beta-gds:search:v2`.
- Local time in `LocalDateTime` + IANA zone in `TimeZone`, **no offset**.
- `Total/@amount` is a string; currency as attribute.
- `Number` without leading zeros.
- `Leg/@sequence` 1-based; legs may be emitted out of order (keep that behaviour; it is a feature).
- Faults use `<Fault>` with `Code`/`Message`.

### Gamma (CSV)
- Query params remain `o`, `d`, `dt` (`yyyyMMdd`).
- Header line, then one line per itinerary, then `#END`.
- Segments packed with `|`, fields with `;`, positional `carrier;flightNo;from;to;dep;arr`.
- Timestamps `yyyyMMddHHmm` UTC.
- `price_cents` integer.
- Streamed via `StreamingResponseBody`; never buffer the whole body.

## What must stay stable regardless of format

These are what the aggregator relies on for deduplication, so changing them is a cross-project decision, not a local one:

- The identity triple per segment: `(carrier, flightNo normalised without leading zeros, departure instant in UTC)` plus `from`/`to`.
- Overlapping itineraries across providers share **exactly** the same flights and departure instants and differ only in price.
- The five kinds of deliberately invalid itineraries listed in `AGENTS.md` §5 and `provider-contracts.md`.

## Verifying locally

```bash
./mvnw -q spring-boot:run &
curl -s 'localhost:8081/alpha/flights?from=MAD&to=JFK&date=2026-11-20' | head -c 600
curl -s 'localhost:8081/beta/search?origin=MAD&destination=JFK&departureDate=2026-11-20' | head -c 800
curl -s 'localhost:8081/gamma/export?o=MAD&d=JFK&dt=20261120' | head -5
```

Compare against the examples in the contract. Then run the aggregator's mapper tests:

```bash
(cd ../flight-aggregator && ./mvnw -q -pl infrastructure test -Dtest='*OfferMapperTest')
```

If those pass with the updated fixtures, the contract is in sync.

## Never do

- Import or reference any type from `../flight-aggregator`.
- Rename a query parameter "for consistency" across providers. The inconsistency is deliberate.
- Make Beta emit UTC or Gamma emit ISO timestamps. Those traps are the point of the exercise.
- Add the aggregator's vocabulary (`Itinerary`, `Segment`, `Offer`) to mock response types. Each provider keeps its own words.
