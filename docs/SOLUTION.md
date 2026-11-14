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
_Offset cost at 1M rows; keyset on `(final_price, id)` with covering index; opaque Base64 cursor; what keyset gives up (random page access, total count) and why that is acceptable for a flight search UI. Measurements from the 1M-row seed._

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
- _Redis outage: fail closed, serve from Postgres, no refresh._ While Redis is unreachable, searches serve whatever Postgres holds with freshness STALE, nothing is aggregated and nothing is refreshed, so provider traffic and latency stay where they were. A route that was never aggregated before returns an empty page until Redis is back. Stale data is served for as long as the outage lasts, which is the cost of protecting the providers. A manual refresh (`POST /api/admin/aggregations`) needs the same lock, so it also answers 409 Conflict: nothing is refreshed until Redis is back. Lock release is compare-and-delete, so an expired holder cannot release another holder's lock.
- _No authentication._
- _Frontend scope._
- _Others as discovered._

## 7. What I would do next

_Priority-ordered list._
