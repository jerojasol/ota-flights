# C1 — Deterministic inventory generator

Read `AGENTS.md` §4–§5 and `docs/provider-contracts.md` ("Cross-provider semantics"). Load the `contract-sync` skill. Work in `src/main/java/io/github/jerojasol/mockproviders/generator/`.

## Task

### Internal model (generator-only; never exposed by controllers directly)
```java
record GeneratedSegment(String carrier, int flightNumber, String from, String to, Instant departureUtc, Instant arrivalUtc)
record GeneratedItinerary(String canonicalId, List<GeneratedSegment> segments, long priceCents, String currency, Set<ProviderId> offeredBy, InvalidKind invalid /* nullable */)
enum ProviderId { ALPHA, BETA, GAMMA }
enum InvalidKind { DEAD_LEG_SAME_AIRPORT, DEAD_LEG_NON_POSITIVE_DURATION, CONNECTION_AIRPORT_MISMATCH, CONNECTION_OVERLAP, CONNECTION_TOO_SHORT }
```

### `InventoryGenerator`
`List<GeneratedItinerary> generate(String origin, String destination, LocalDate date, ProviderId provider)`

Determinism: `new Random(Objects.hash(mock.seed, origin, destination, date))`. Same inputs → identical output across restarts and across providers (so overlap is computable).

Algorithm, per `(origin, destination, date)` (independent of provider):
1. Produce `inventory-size` base itineraries: 60% non-stop, 30% one connection via a hub from a fixed list (`LHR, CDG, AMS, FRA, MAD, JFK`), 10% two connections. Carriers from a fixed list (`IB, BA, AF, KL, LH, UX, DL, AA`). Flight numbers 1–9999. Departures spread across the day at 5-minute granularity; durations realistic (1–9 h per segment); connections 50 min – 5 h.
2. Mark `overlap-ratio` of them as offered by 2 or 3 providers (random subset); the rest by exactly one. Price per provider = base price × (1 ± up to 5%), rounded to cents, **different per provider**.
3. Mark `invalid-ratio` of them with one `InvalidKind` each, applied by mutating the segments accordingly (keep them in all providers that offer them).
4. Return only the itineraries whose `offeredBy` contains the requested provider, with that provider's price.

`canonicalId` = the identity string the contract defines (ordered `carrier+flightNo@departureUtcMinute>from-to|…`), useful for tests and for the aggregator team to debug.

### `MockProperties` (`@ConfigurationProperties("mock")`)
Bind `seed`, `inventorySize`, `overlapRatio`, `invalidRatio`, `chaos.*` (chaos used later). Validate ratios in `[0,1]`.

## Tests

`InventoryGeneratorTest`:
- same inputs twice → identical lists (deep equality).
- different date → different lists.
- fraction of itineraries present in ≥2 providers ≈ `overlapRatio` (±5 percentage points over a 2,000-size inventory).
- overlapping itineraries have identical segments across providers and different prices.
- fraction with `invalid != null` ≈ `invalidRatio`; each `InvalidKind` appears at least once at size 2,000.
- every valid itinerary satisfies: `from != to`, `arrival > departure`, connecting airports match, connection ≥ 50 min.
- `generate(..., ALPHA)` ∪ `BETA` ∪ `GAMMA` by `canonicalId` equals the base inventory.

## Acceptance

- `./mvnw -q verify` green.
- No type from `flight-aggregator` referenced (there is none on the classpath; also do not copy its names — `Itinerary`, `Segment`, `Offer` are banned here, hence `Generated*`).

## Closing

`/log-ai-decision`, show diff summary.
