# D3 — Provider adapter (run once per provider: Alpha, Beta, Gamma)

Load the `hexagonal-java` (§7) and `testing-conventions` (§3–§4) skills. Read `../mock-providers/docs/provider-contracts.md` — the section for **`{PROVIDER}`** is the contract. Do not look at `mock-providers` source code; only the contract.

Replace `{PROVIDER}` below with `Alpha`, `Beta` or `Gamma` and `{provider}` with the lowercase form.

## Task

Everything in `infrastructure/.../provider/{provider}/`, package-private except the adapter.

### 1. Fixtures first
Create in `infrastructure/src/test/resources/fixtures/{provider}/`:
- `two-itineraries.<json|xml|csv>` — copy the example from the contract verbatim (for Beta, keep legs out of order; for Gamma include the `#END` line).
- `empty.<ext>` — the documented empty response.
- `error-500.<ext>` — the documented chaos error body.
- One extra fixture exercising the provider's trap: Beta with legs `sequence="2"` before `sequence="1"`; Gamma with a 4-digit zero-padded flight number and a two-segment packed field; Alpha with `flightNo` `"0117"`.
- `two-itineraries.*` and `empty.*` already exist: they are exported by `mock-providers/scripts/export-fixtures.sh`, the only sanctioned channel. Do not overwrite them; put the contract's verbatim example in `contract-example.<ext>`.
- `dead-legs.<ext>`: itineraries with a segment breaking S1–S2, plus one valid itinerary.
- A malformed-entries fixture: Alpha `null-entries.json` (a null itinerary, a null segment entry, one valid itinerary); Beta an itinerary missing a required element or attribute and one with an unknown IANA zone; Gamma a line with the wrong field count and one with non-numeric cents. Each also holds one valid itinerary.

### 2. Response DTOs — `{Provider}SearchResponse` and nested records/classes
Use the provider's vocabulary exactly (Beta: `Itineraries`, `Itinerary`, `Legs`, `Leg`, `Pricing`, `Total`…). Jackson 3 annotations (`tools.jackson.*`) are fine here; this is the ACL.
- **DTO fields hold raw strings.** Numbers, amounts, dates and timestamps are parsed in the mapper, per itinerary, never by the format parser (no `BigDecimal`, `int` or `Instant` fields in DTOs). Otherwise one unreadable value fails the whole body as `ERROR` instead of counting one unmappable itinerary. Jackson keeps a JSON number's original text when the target is `String`, so no precision is lost.
- Alpha: `List<AlphaItinerary>` via Jackson JSON.
- Beta: `XmlMapper` from `tools.jackson.dataformat.xml`; handle the namespace; mutable classes allowed if records fight with XML.
- Gamma: no DTO tree; a `GammaLineParser` that parses one CSV line into a small record and a streaming reader that yields lines until `#END`, failing with a clear exception if `#END` is missing. A stream that ends without `#END` is **truncated** (the connection dropped mid-body): that is a transport failure, so it stays retryable and must **not** be a `ProviderContractViolation`.

### 3. `{Provider}OfferMapper` — DTO → `List<Offer>`
This is where normalisation lives:
- Alpha: parse ISO `Z` instants; `flightNo` → `FlightNumber.of(carrier + flightNo)` (normalises zeros).
- Beta: `LocalDateTime` + IANA zone → `Instant`; sort legs by `@sequence`; `Total/@amount` string → `Price`.
- Gamma: `yyyyMMddHHmm` UTC → `Instant`; `price_cents` → `Price` (divide by 100, scale 2); split `|` then `;`.
- **One bad itinerary never fails the response.** An itinerary that cannot be turned into an `Offer` is skipped and counted: return the domain's `ProviderResponse(List<Offer> offers, int unmappable)` (`domain-model.md` §8). The application counts `unmappable` as received and rejected for that provider (§9); there is no per-reason breakdown.
- Nulls and structural problems are checked **explicitly**, before building anything: null entries, missing elements/attributes/fields, a Gamma line with the wrong field count. Never `catch (RuntimeException)`.
- Catch only `DomainException`, the format's own parse exception, and the mapper's own structural-check exception (Beta's private `MalformedEntry`): `DateTimeParseException` (Alpha), `DateTimeException` for timestamps and IANA zones (Beta), `NumberFormatException` for cents and `DateTimeParseException` for `yyyyMMddHHmm` (Gamma).
- **Four failure kinds, never mixed:**
  1. *One itinerary is bad* (dead leg, missing field, null entry, unreadable value, Gamma line with the wrong field count) → skipped and counted in `unmappable`. The response still succeeds.
  2. *The 2xx body as a whole breaks the contract* (unreadable JSON/XML, missing top-level element such as Beta's `Itineraries`, Gamma's header row missing or wrong) → throw `infrastructure.provider.ProviderContractViolation` from the parser or mapper. It becomes `ERROR`, is **not retried** (it would fail the same way), and the circuit breaker still records it.
  3. *Our request was rejected* (HTTP 4xx other than 408 and 429) → `infrastructure.provider.ProviderRequestRejected`, built by `ProviderRestClients.httpError`. It becomes `ERROR`, is **not retried**, and the circuit breaker **ignores** it: the provider is healthy, the fault is in our request.
  4. *Transport failure* (5xx, 408, 429, connection reset, read timeout, Gamma stream truncated before `#END` or before the header) → `ERROR`, retried.
- Connection rules are not checked in the mapper; `OfferValidator` does that after mapping.

### 4. `{Provider}Client`
`RestClient` built from a base URL; one method `fetch(SearchCriteria)` → raw DTO (or `InputStream` for Gamma). Query parameters per the contract (`from/to/date`, `origin/destination/departureDate`, `o/d/dt`). Map 5xx to `ProviderUnavailableException(provider, ERROR)`; 4xx to `ERROR` as well with the body in the message.

### 5. `{Provider}ProviderAdapter implements FlightProviderPort`
Wraps `client + mapper` with Resilience4j **programmatically**:
- `TimeLimiter` (per-provider timeout from config) → on timeout `ProviderUnavailableException(TIMEOUT)`.
- `CircuitBreaker` (sliding window 10, failure rate 50%, wait 30 s) → when open `ProviderUnavailableException(CIRCUIT_OPEN)`.
- `Retry` (max 2 attempts, only on transport `ERROR`s: not on `TIMEOUT`, `CIRCUIT_OPEN`, `ProviderContractViolation` or `ProviderRequestRejected`). This is already implemented in the shared `infrastructure.provider.ProviderResilience`; reuse it, together with `ProviderRestClients` for the HTTP client.
Order: `Retry(CircuitBreaker(TimeLimiter(call)))`. Use a dedicated `ScheduledExecutorService` or the shared virtual-thread executor for the time limiter.

### 6. Register in `ProviderConfiguration`
Bean per adapter; `@ConfigurationProperties("providers")` record with `alpha.baseUrl`, `beta.baseUrl`, `gamma.baseUrl`, `timeout`.

## Tests

- `{Provider}OfferMapperTest` (unit, fixtures): the two-itinerary fixture yields 2 offers with the expected `Instant`s, prices, `FlightNumber`s and segment order; the trap fixture is handled; the empty fixture yields 0; an itinerary with an invalid segment is skipped and counted; the malformed-entries fixture yields its one valid offer and counts every malformed entry as unmappable.
- `{Provider}ProviderAdapterIT` (WireMock, no Spring): success; 500 → `ERROR` after retry; fixed delay > timeout → `TIMEOUT` within budget; N consecutive failures → `CIRCUIT_OPEN` without hitting WireMock (assert request count); a 200 whose body breaks the contract → `ERROR` after **exactly one** request; for Gamma, a body without `#END` → `ERROR` after a retry (two requests), because truncation is a transport failure.

## Acceptance

- `./mvnw -q -pl infrastructure test` green (ArchUnit rule 7 confirms DTOs do not leak).
- `./mvnw -q -pl infrastructure verify -Dit.test={Provider}ProviderAdapterIT` green.

## Closing

`/check-arch`, `/log-ai-decision` (mention any contract ambiguity you found; if the contract was unclear, propose a wording fix for `provider-contracts.md` rather than guessing), show diff summary. Then repeat this prompt for the next provider.
