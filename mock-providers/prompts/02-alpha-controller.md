# C2 — Alpha controller (JSON)

Read `docs/provider-contracts.md` → "Alpha". Load the `contract-sync` skill. Work in `src/main/java/io/github/jerojasol/mockproviders/alpha/`.

## Task

### Response records (Alpha's own vocabulary)
```java
record AlphaItinerary(String id, BigDecimal price, String currency, List<AlphaSegment> segments)
record AlphaSegment(String carrier, String flightNo, String from, String to, Instant dep, Instant arr)
record AlphaError(String error, String message, String ref)
```
- `price` serialises as a JSON number with exactly two decimals (configure `BigDecimal` scale, not a string).
- `dep`/`arr` serialise as ISO-8601 with `Z` (Jackson 3 default for `Instant` with timestamps disabled — verify and set `spring.jackson.serialization.write-dates-as-timestamps=false` if needed).
- `flightNo` is **digits only**, as emitted by the generator (no zero padding).
- `id` = `"ALP-" + first 6 hex of sha256(canonicalId)`.

### `AlphaController`
`GET /alpha/flights?from=&to=&date=` → `List<AlphaItinerary>`, `Content-Type: application/json`.
- Validate `from`/`to` are 3 uppercase letters and `date` is `yyyy-MM-dd`; otherwise 400 with `AlphaError("BAD_REQUEST", message, null)`.
- Empty inventory → `[]`.
- Map from `InventoryGenerator.generate(from, to, date, ALPHA)` via a package-private `AlphaMapper`.

### Error body for chaos
Expose a package-private `AlphaErrorWriter` (or a `@ControllerAdvice` scoped to this package) producing `AlphaError("INTERNAL", "Upstream inventory unavailable", ref)` as JSON — the chaos filter (step C5) will call it.

## Tests

`AlphaControllerTest` (`@WebMvcTest(AlphaController.class)` with a real or stubbed generator):
- 200, `application/json`, body is an array; one element has the exact field names from the contract (assert with JSONPath, including `segments[0].dep` ending in `Z`).
- `price` appears as a number with two decimals (assert on the raw JSON string, e.g. `"price":512.30`).
- missing `date` → 400 with `error: BAD_REQUEST`.
- `from=mad` (lowercase) → 400 (Alpha is strict; do not normalise — the aggregator must send uppercase).
- empty inventory → `[]`.

## Acceptance

- `./mvnw -q verify` green.
- `curl 'localhost:8081/alpha/flights?from=MAD&to=JFK&date=2026-11-20' | jq '.[0]'` matches the contract's shape field for field.

## Closing

`/log-ai-decision`, show diff summary.
