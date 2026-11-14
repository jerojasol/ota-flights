# C3 — Beta controller (XML)

Read `docs/provider-contracts.md` → "Beta". Load the `contract-sync` skill. Work in `src/main/java/io/github/jerojasol/mockproviders/beta/`.

Beta is the legacy one. Its traps are the point; do not "fix" them.

## Task

### XML model (Beta's vocabulary, Jackson 3 XML — `tools.jackson.dataformat.xml`)
Mirror the contract exactly:
```
SearchResponse (root, namespace urn:beta-gds:search:v2)
├── Meta { RequestId, Currency }
└── Itineraries @count
    └── Itinerary @ref
        ├── Pricing → Total @amount @currency
        └── Legs → Leg @sequence
            ├── Flight → Airline @code, Number
            ├── Departure → Airport @code, LocalDateTime, TimeZone
            └── Arrival   → Airport @code, LocalDateTime, TimeZone
Fault (root) { Code, Message, Reference? }
```
Mutable POJOs with `@JacksonXmlProperty(isAttribute = true)` / `@JacksonXmlRootElement(namespace = …)` / `@JacksonXmlElementWrapper` as needed. Records are fine where they work.

### `BetaMapper` — the traps
- `LocalDateTime` = the instant converted to the **airport's local time**, formatted `yyyy-MM-dd'T'HH:mm:ss` with no offset. `TimeZone` = the IANA zone of that airport from a fixed map (`MAD→Europe/Madrid`, `JFK→America/New_York`, `LHR→Europe/London`, `CDG→Europe/Paris`, `AMS→Europe/Amsterdam`, `FRA→Europe/Berlin`, …). Unknown airport → `UTC`.
- `Number` = flight number **without** leading zeros.
- `Total/@amount` = string with two decimals.
- Legs are emitted **shuffled** (deterministically, from the same seeded random) so `@sequence` is required to reorder. At least one multi-leg itinerary per response must be out of order; make the mapper guarantee it when size ≥ 2 legs.
- `@ref` = `"BET-" + first 4 hex of sha256(canonicalId)`.
- `Meta/RequestId` = `"BET-" + yyyyMMdd + "-" + 4-digit counter`.

### `BetaController`
`GET /beta/search?origin=&destination=&departureDate=` → `SearchResponse`, `Content-Type: application/xml`.
- Validation errors → 400 with `<Fault><Code>INVALID_REQUEST</Code><Message>…</Message></Fault>`.
- Empty → `<Itineraries count="0"/>`.

### Fault writer for chaos
Package-private `BetaFaultWriter` producing `SYSTEM_ERROR` / `Inventory backend timed out` / `Reference`.

## Tests

`BetaControllerTest` (`@WebMvcTest`):
- 200, `application/xml`, root element `SearchResponse` with the namespace.
- XPath assertions: `//Itinerary[1]/Pricing/Total/@amount` matches `\d+\.\d{2}`; `//Leg[1]/Departure/LocalDateTime` has no `Z` and no offset; `//Leg/Flight/Number` has no leading zero.
- For a two-leg itinerary, the document order of `@sequence` is `2,1` at least once in the response (parse and check).
- missing `origin` → 400 `Fault/Code = INVALID_REQUEST`.
- empty → `Itineraries/@count = 0`.

`BetaMapperTest` (unit): `2026-11-20T07:35Z` at `MAD` → `LocalDateTime 2026-11-20T08:35:00`, `TimeZone Europe/Madrid`; number `117` for flight 117.

## Acceptance

- `./mvnw -q verify` green.
- `curl -s 'localhost:8081/beta/search?origin=MAD&destination=JFK&departureDate=2026-11-20' | xmllint --format - | head -40` matches the contract structure.

## Closing

`/log-ai-decision`, show diff summary.
