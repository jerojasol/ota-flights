# SOLUTION.md

> Status: skeleton. Sections are filled in as the implementation progresses. See `docs/ai-log.md` for the chronological record.

## 1. Overview

_One paragraph: what the system does, the three projects, how to run it._

## 2. Architectural decisions

### 2.1 Hexagonal architecture and module boundaries
_Why Maven multi-module with a framework-free `domain`; how the dependency direction is enforced (poms + ArchUnit); why a single `infrastructure` module with package-level in/out separation instead of one module per adapter._

### 2.2 Read path and write path
_Inventory model vs. live meta-search; the two flows; stale-while-revalidate; roles of PostgreSQL and Redis; why only the first page is cached._

### 2.3 Resilience
_Fan-out with bounded budget; Resilience4j used programmatically (not via starter) and why; partial results and per-provider status exposed in the API._

### 2.4 Anti-corruption layers
_Three providers, three formats, three sets of conventions (timestamps, prices, flight numbers, ordering); how each adapter normalises into the domain `Offer`._

### 2.5 Technology choices
_Spring Boot 4.1.2 / Java 21 rationale; Flyway over `ddl-auto`; `RestClient`; virtual threads._

## 3. AI tools utilised

### 3.1 Approach
_Constitution-first: `AGENTS.md` per scope, `domain-model.md` as seed, then agents/skills/commands. Everything versioned under `.opencode/`._

### 3.2 Inventory

| Tool | Scope | Type | Purpose | What it actually caught / produced |
|---|---|---|---|---|
| `AGENTS.md` (root) | repo | instructions | Inter-project contract, compose, deliverables | |
| `AGENTS.md` (aggregator) | flight-aggregator | instructions | Stack, module rules, Ubiquitous Language, conventions | |
| `AGENTS.md` (mock-providers) | mock-providers | instructions | Provider personalities, chaos rules, isolation from aggregator | |
| `domain-guardian` | flight-aggregator | agent (read-only) | Enforce DDD/hexagonal rules beyond what ArchUnit can see | |
| `hexagonal-java` | flight-aggregator | skill | Port/adapter templates, JPA↔domain mapping, use-case shape | |
| `testing-conventions` | flight-aggregator | skill | What to test per layer, WireMock/Testcontainers recipes | |
| `/check-arch` | flight-aggregator | command | ArchUnit + guardian in one step | |
| `/test-layer` | flight-aggregator | command | Run tests for one layer | |
| `contract-sync` | mock-providers | skill | Change a provider format without silently breaking the aggregator | |
| `/log-ai-decision` | repo | command | Append to `ai-log.md` | |

### 3.3 Prompts and process
_Link to `docs/ai-log.md`. Summarise: what was delegated, what was reviewed, what was corrected (e.g. Testcontainers 2.x artifact names), what was decided by a human (connection bounds, module layout)._

## 4. Data modelling and pagination

### 4.1 Schema
_`itineraries` table, `itinerary_key` unique, `segments` JSONB, denormalised search columns, indexes. Upsert with `ON CONFLICT ... WHERE excluded.final_price < itineraries.final_price`._

### 4.2 Deduplication identity
_`ItineraryKey` canonical form; what is included and excluded and why._

### 4.3 Keyset vs. offset
_Opaque Base64 cursor; what keyset gives up (random page access, total count) and why that is acceptable for a flight search UI._

Pages are ordered by `(final_price, itinerary_key)`, which is the suffix of `ix_itineraries_search` after the equality prefix `(origin, destination, departure_date)`. `itinerary_key` is unique, so it breaks ties and makes the order total.

**What the numbers show.** A real route holds a few hundred rows for each origin, destination and date. At that size, keyset and offset are both fast on every page (under 3 ms). The difference only appears at depth: on a route with 200,000 rows, an offset page near the end costs 175 to 400 ms, while a keyset page costs the same as page 1.

**Why keyset anyway.** Keyset was chosen for three reasons:
- Its cost does not grow with page depth.
- Pages stay stable while background refreshes upsert rows. With offset, a cheaper itinerary that arrives between two page requests lands before the current position and pushes every later row back one place, so the next page repeats an itinerary that was already shown. Offset would skip an itinerary if a row before the current position disappeared; today the upsert only lowers prices and never deletes, so that case does not occur. A keyset cursor resumes after the last `(final_price, itinerary_key)` the client saw, so it does not repeat or skip because of rows inserted or moved before that point. The newly arrived cheaper itinerary is simply not shown in that pass.
- It needs no `COUNT(*)`.

#### Measurements

Machine: Apple M1 Pro, Docker Desktop with 4 CPUs and 7.7 GiB of memory, PostgreSQL 16 (compose image), warm cache.
Data: 1,000,000 rows from `flight-aggregator/scripts/seed-1m.sql`.
- 800,000 rows over 50 real origin/destination pairs (15 airports × 30 dates), about 533 rows per pair and date.
- One synthetic deep route `ZZA`–`ZZB` on 2026-12-01 with 200,000 rows. The codes are deliberately artificial. It exists only so that both techniques can be compared at depth with the same filtered query and the same index.

The seed takes under a minute.

```bash
docker compose up -d postgres
docker exec -i ota-flights-postgres-1 psql -U flights -d flights < flight-aggregator/scripts/seed-1m.sql
flight-aggregator/scripts/measure-pagination.sh      # or DB_URL=postgres://… with a local psql
```

Every query filters by origin, destination and date and orders by `final_price, itinerary_key` with `LIMIT 20`, which is the shape the API issues. "Time" is the median `EXPLAIN ANALYZE` execution time over 5 runs. "Buffers" counts the shared buffers (hit + read) of the top plan node. The plans are in `flight-aggregator/scripts/results/`.

| Route | Query | Time (ms) | Buffers | Plan |
|---|---|---|---|---|
| MAD–BCN (533 rows) | Keyset, page 1 | 0.22 | 23 | Index Scan `ix_itineraries_search` |
| | Keyset, row 513 | 0.23 | 23 | Index Scan `ix_itineraries_search` |
| | Offset, page 1 | 0.22 | 23 | Index Scan `ix_itineraries_search` |
| | Offset, row 513 | 2.70 | 546 | Index Scan `ix_itineraries_search` |
| ZZA–ZZB (200,000 rows) | Keyset, page 1 | 0.48 | 24 | Index Scan `ix_itineraries_search` |
| | Keyset, row 190,000 | 0.62 | 24 | Index Scan `ix_itineraries_search` |
| | Offset, page 1 | 0.44 | 24 | Index Scan `ix_itineraries_search` |
| | Offset, row 190,000 (planner's choice) | 399.6 | 16,489 | Bitmap Index Scan `ix_itineraries_search` → Bitmap Heap Scan + external sort |
| | Offset, row 190,000 (ordered index scan forced) | 174.9 | 193,370 | Index Scan `ix_itineraries_search` |

For a large `OFFSET`, the planner reads the route through the index as a bitmap and then sorts it. The forced row disables bitmap scans, sequential scans and sorts, so the comparison also covers offset walking the same ordered index as keyset. In both variants, offset reads and discards every preceding row: about 8,000 times as many buffers as keyset when it walks the same index.

Times vary between runs by up to about 2×, depending on cache state. The ratios do not change.

> **Note: an unfiltered query is not a fair comparison.** Without the route filter, `ORDER BY final_price, itinerary_key LIMIT 20 OFFSET 900000` takes about 1.9 s (Parallel Seq Scan + external merge sort of the whole table, about 129,000 buffers). No index serves that ordering without the equality prefix, so most of the time is the full-table sort, not offset itself. The API never issues this query, because origin, destination and date are required. It is kept in the script output and in `scripts/results/` for scale only.

## 5. Testing strategy

_Per layer: pure unit tests in `domain`; fixture-driven mapper tests; WireMock for latency/500/circuit-open; Testcontainers for persistence and pagination; ArchUnit for structure. What is deliberately not covered._

## 6. Limitations and trade-offs

- _Single currency per search; no FX._
  - Prices are never compared across currencies. A max-price filter or page cursor only matches rows in its own currency, and an upsert conflict in another currency writes nothing.
  - Known gap: if the reference currency changed between aggregations, new keys could be stored in a second currency on the same route. An unfiltered first page orders by raw amount, so the two currencies would be interleaved. Fixing it needs either one currency enforced per route or FX conversion.
  - Amounts always have scale 2 (domain invariant P2). Gamma's `price_cents` is converted with the currency's own minor units, so EUR and JPY are exact, but a 3-decimal currency such as KWD (`1234` → `1.234`) is rounded HALF_EVEN to 2 decimals (`1.23`). Pseudo-currencies without minor units (XXX, XAU) are dropped as unmappable. Supporting 3-decimal currencies would need P2 to follow the currency's minor units.
- _Exact-key deduplication only; no fuzzy matching._
  - Beta sends local time plus an IANA zone, without an offset. During the autumn daylight-saving overlap a local time happens twice, and the adapter uses the earlier offset. A departure in the repeated hour may therefore be mapped one hour early, get a different Itinerary Key from the same flight sent by Alpha or Gamma (both UTC), and not deduplicate against them. Local times inside a spring-forward gap do not exist and are dropped as unmappable. Fixing it would need Beta to send an offset.
- _On-demand synchronous aggregation on cold miss (bounded); no event pipeline._
  - A provider retry can start too late to finish inside the fan-out budget (defaults: 5.5 s provider timeout, 6 s budget). A 500 that arrives near the end of the first attempt starts a retry that the aggregation no longer waits for: the provider is reported as TIMEOUT rather than ERROR, the abandoned retry still calls it, and its offers arrive with the next refresh. Data stays correct; only the reported status is less precise. Accepted rather than tightening the defaults.
  - Provider timeout 5.5 s and fan-out budget 6 s, sized to the brief's provider latency (100 ms – 5 s, uniform in the mock). With the earlier 3 s timeout about 40% of calls that would have succeeded were cut off; together with the 10% error rate, nearly half of all calls failed and the circuit breakers (50% over 10 calls) opened within minutes on the default stack, so a new route could return zero items with every provider CIRCUIT_OPEN. Cost: the first search of a route never aggregated before can take up to 6 s. Routes already aggregated are served from Postgres without waiting for providers.
  - A refresh in which every provider fails stores nothing and is not reflected in the search response: `providers` and `aggregatedAt` describe the last aggregation that produced the data shown, not the last attempt, and freshness is unchanged. The failed outcome is visible only in the response of the refresh itself (`POST /api/admin/aggregations`) and in the logs; the dashboard says "No provider answered; showing stored results." and keeps its provider panel, titled "Last successful aggregation".
- _Redis outage: fail closed, serve from Postgres, no refresh._ While Redis is unreachable nothing can take the aggregation lock, so nothing refreshes data: not searches, not the warm-routes scheduler, and not a manual refresh (`POST /api/admin/aggregations` answers 409 Conflict). Searches serve whatever Postgres holds, marked STALE; a route never aggregated before returns an empty page. This is deliberate: every path fails closed rather than keeping one that reaches providers without coordination, so provider traffic stays where it was. No refresh is possible until Redis is back. The container stays healthy throughout, because the healthcheck is `/actuator/health/liveness`; `/actuator/health` reports Redis DOWN for operators. Lock release is compare-and-delete, so an expired holder cannot release another holder's lock.
- _No authentication._ This includes `POST /api/admin/aggregations` and the actuator endpoints; `/actuator/health` shows details (provider circuit breakers) to anyone.
- _Frontend scope._
- _Others as discovered._

## 7. What I would do next

_Priority-ordered list._

- **Budget-aware retry.** Let the retry decision see the time left in the fan-out budget, so a retry is only started when it can finish; the provider is then reported as ERROR, and no abandoned call keeps hitting it.
- **Known technical debt: the aggregation-under-lock sequence is written three times.** tryLock → aggregate → markFresh if any provider succeeded → release appears in `SearchItinerariesService` (cold miss and background refresh) and in `RefreshItinerariesService`. The three differ in how they treat a failure (serve stale data, let the executor log it, report it to the caller), and the cold miss also keeps the outcomes when `markFresh` fails. Tests pin each path today; a shared helper that keeps those differences explicit would make a future rule change a single edit.
