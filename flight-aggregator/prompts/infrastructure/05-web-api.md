# D5 — Web API: controllers, DTOs, cursor codec, Problem Details

Load the `hexagonal-java` (§8) and `testing-conventions` (§6) skills. Read `AGENTS.md` §6 (web rules). Everything in `infrastructure/.../web/`.

## Task

### Endpoints

```
GET  /api/itineraries
     ?origin=MAD&destination=JFK&date=2026-11-20
     &maxPrice=800&carrier=IB&cursor=<opaque>&limit=20

POST /api/admin/aggregations
     body: { "origin": "MAD", "destination": "JFK", "date": "2026-11-20" }
```

### `ItinerarySearchRequest` (record bound from query params)
Bean Validation: `@NotBlank @Pattern("[A-Z]{3}")` on origin/destination (apply uppercase before validation via a custom binder or accept lowercase and normalise — decide, record), `@NotNull` date, `@Positive` maxPrice, `@Pattern("[A-Z0-9]{2}")` carrier, `@Min(1) @Max(100)` limit default 20.

### Response records (`web/dto`)
```
ItineraryPageResponse(List<ItineraryResponse> items, String nextCursor, String freshness,
                      Instant aggregatedAt, Map<String, ProviderOutcomeResponse> providers)
ItineraryResponse(String id, String origin, String destination, List<SegmentResponse> segments,
                  PriceResponse basePrice, PriceResponse finalPrice, String source,
                  int connections, String totalDuration /* ISO-8601 */)
SegmentResponse(String carrier, String flightNumber, String from, String to, Instant departureAt, Instant arrivalAt)
PriceResponse(BigDecimal amount, String currency)
ProviderOutcomeResponse(String status, int received, int rejected, long elapsedMs)
AggregationResponse(int persisted, Instant aggregatedAt, Map<String, ProviderOutcomeResponse> providers)
```
`id` = the `ItineraryKey` value (stable, provider-independent).

### `CursorCodec`
`PageCursor` ↔ URL-safe Base64 of `"<price>|<id>"`. Invalid cursor → `InvalidCursorException` → 400.

### `ItineraryResponseMapper`
Domain → DTO. Hand-written.

### `ItineraryController`, `AggregationController`
Inject only `SearchItinerariesUseCase` / `AggregateFlightsUseCase`. Translate, call, translate. No business `if`s.

### `ApiExceptionHandler` (`@RestControllerAdvice`)
- `MethodArgumentNotValidException`, `ConstraintViolationException`, `InvalidCursorException` → 400 `ProblemDetail` with field errors in `errors` extension.
- `DomainException` → 422.
- anything else → 500 with a correlation id, no stack trace in body.

### OpenAPI
springdoc is on the classpath; add `@Operation`/`@Schema` summaries so `/swagger-ui.html` is useful. Keep it light.

## Tests

`ItineraryControllerTest` (`@WebMvcTest`, `@MockitoBean` use cases):
- happy path returns 200 with the full shape; `nextCursor` present when the page has a next.
- lowercase `origin=mad` behaves per your decision (normalised or 400).
- missing `date` → 400 Problem Details with `errors.date`.
- bad cursor → 400.
- `limit=500` → 400 (validation, not clamping; the application clamps as a second line).
- use case throws `DomainException` → 422.

`CursorCodecTest`: round-trip; tampered string rejected.

`AggregationControllerTest`: 202 or 200 (decide; 200 with the result is simpler for the demo) with outcomes map.

## Acceptance

- `./mvnw -q -pl infrastructure test` green; ArchUnit rules 4 and 5 green (controllers see only inbound ports).

## Closing

`/check-arch`, `/log-ai-decision`, show diff summary.
