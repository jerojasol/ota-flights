# D7 — One end-to-end integration test that tells the whole story

Load the `testing-conventions` skill (§8). Everything else already exists; this test exercises the real wiring.

## Task

Create `infrastructure/src/test/java/.../EndToEndIT.java`:

- `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@ActiveProfiles("test")`.
- Testcontainers: PostgreSQL 16 and Redis, both `@ServiceConnection`.
- One WireMock server serving `/alpha/**`, `/beta/**`, `/gamma/**`; `providers.*.base-url` pointed at it via `@DynamicPropertySource`.
- `RestTestClient` (Boot 4) or `TestRestTemplate` for calls.

Scenario, in order, as a single `@Test` with clear step comments (or `@TestMethodOrder` if you prefer separate methods):

1. **Cold miss** — all three providers return their two-itinerary fixtures (with overlap across providers). `GET /api/itineraries?origin=MAD&destination=JFK&date=…&limit=1` → 200, `freshness=FRESH`, `providers.*.status=OK`, exactly the deduplicated count is available (check via paging), items sorted by `finalPrice`, `finalPrice = basePrice × 1.05`.
2. **Keyset paging** — follow `nextCursor` until empty; collect all ids; assert no duplicates and total equals distinct `ItineraryKey`s across the fixtures.
3. **Filter** — `maxPrice` below the cheapest → empty page, still `FRESH`, no new provider calls (assert WireMock request count unchanged).
4. **Provider failure during refresh** — expire freshness (shorten TTL in test profile to ~2 s and wait with Awaitility, or delete the `fresh:*` key through `StringRedisTemplate`). Make Beta return 500. Search again → `freshness=STALE`, data still served from the previous aggregation; wait for the background refresh; search again → `FRESH`, `providers.BETA.status=ERROR`, Alpha/Gamma `OK`, and Beta-only itineraries still present (upsert never deletes).
5. **Cheaper wins across runs** — change Alpha's fixture to a cheaper price for an overlapping itinerary, force a refresh, assert the itinerary's `finalPrice` dropped and `source=ALPHA`.
6. **Manual aggregation** — `POST /api/admin/aggregations` → 200 with outcomes.

Keep the whole test under ~30 s. If it gets slow, reduce fixture sizes, not assertions.

## Acceptance

- `./mvnw -q -pl infrastructure verify -Dit.test=EndToEndIT` green.
- The test reads like documentation; a reviewer should understand the system by reading it top to bottom.

## Closing

`/log-ai-decision`, show diff summary. This closes Phase D.
