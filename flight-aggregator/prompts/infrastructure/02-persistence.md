# D2 — Persistence: Flyway schema, JPA entity, JDBC upsert, keyset pagination

Load the `hexagonal-java` (§6) and `testing-conventions` (§5) skills. Read `docs/domain-model.md` §8 (`ItineraryRepositoryPort`). Everything in `infrastructure/.../persistence/` plus `src/main/resources/db/migration/`.

## Task

### 1. Flyway `V1__create_itineraries.sql`

```sql
CREATE TABLE itineraries (
    id             BIGSERIAL PRIMARY KEY,
    itinerary_key  CHAR(64)      NOT NULL,
    origin         CHAR(3)       NOT NULL,
    destination    CHAR(3)       NOT NULL,
    departure_date DATE          NOT NULL,          -- date of first departure, UTC
    first_carrier  CHAR(2)       NOT NULL,
    carriers       CHAR(2)[]     NOT NULL,          -- distinct carriers, for filter by carrier
    base_price     NUMERIC(12,2) NOT NULL,
    final_price    NUMERIC(12,2) NOT NULL,
    currency       CHAR(3)       NOT NULL,
    source         VARCHAR(8)    NOT NULL,
    segments       JSONB         NOT NULL,
    ingested_at    TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_itineraries_key UNIQUE (itinerary_key)
);

-- Keyset pagination for the main search: filter by route + date, order by (final_price, itinerary_key)
CREATE INDEX ix_itineraries_search
    ON itineraries (origin, destination, departure_date, final_price, itinerary_key);

-- Carrier filter
CREATE INDEX ix_itineraries_carriers ON itineraries USING GIN (carriers);
```

Comment every index with *why* it exists. These comments are quoted in `SOLUTION.md`.

### 2. `ItineraryJpaEntity` (package-private, `@Entity`, `@Table("itineraries")`)
Read-side only. `segments` as `String` (JSON) with a `@Convert` or `@JdbcTypeCode(SqlTypes.JSON)`; whichever is simpler with Hibernate 7.

### 3. `ItineraryJpaRepository extends JpaRepository<ItineraryJpaEntity, Long>`
One `@Query` (JPQL or native) implementing keyset:

```sql
WHERE origin = :origin AND destination = :destination AND departure_date = :date
  AND (:maxPrice IS NULL OR final_price <= :maxPrice)
  AND (:carrier IS NULL OR :carrier = ANY(carriers))
  AND (final_price, itinerary_key) > (:cursorPrice, :cursorKey)   -- later pages only; see note below
ORDER BY final_price, itinerary_key
LIMIT :limitPlusOne
```
Fetch `limit + 1` rows to know whether there is a next page without a `COUNT`.

The cursor is `(final_price, itinerary_key)`, not `(final_price, id)`: the domain's `PageCursor` is `(lastFinalPrice, lastKey)` (`domain-model.md` §8) and database ids never reach the domain (AGENTS.md §4). Use two queries, one for the first page without a cursor predicate and one for later pages with the plain row comparison. Do not write the cursor as `(:cursorPrice IS NULL OR …)`: under a generic plan that cannot become an index condition, so deep pages get slower.

### 4. `ItineraryJdbcUpserter`
`JdbcTemplate.batchUpdate` in chunks of 1,000:

```sql
INSERT INTO itineraries (...) VALUES (...)
ON CONFLICT (itinerary_key) DO UPDATE
SET base_price = EXCLUDED.base_price, final_price = EXCLUDED.final_price,
    source = EXCLUDED.source, segments = EXCLUDED.segments, ingested_at = EXCLUDED.ingested_at
WHERE EXCLUDED.final_price < itineraries.final_price
```
Return total affected rows.

### 5. `ItineraryPersistenceMapper`
Entity ↔ `Itinerary` using `Itinerary.rehydrate(...)` and `ItineraryKey.fromStored(...)`. Segments JSON ↔ `List<Segment>` via a Jackson 3 `ObjectMapper`/`JsonMapper` **inside this class**; define a small private `SegmentJson` record for the JSON shape so domain types never get Jackson annotations.

### 6. `ItineraryRepositoryAdapter implements ItineraryRepositoryPort`
Delegates reads to the JPA repository + mapper and writes to the upserter. `@Transactional` on `upsertAll`.

### 7. `PersistenceConfiguration`
`@Configuration` exposing the adapter as `ItineraryRepositoryPort`. Enable JPA repositories for the persistence package only.

## Tests

`ItineraryRepositoryAdapterIT` with Testcontainers PostgreSQL 16 and `@ServiceConnection` (Flyway runs on startup):

- upsert inserts; `findBy` returns them ordered by `(final_price, itinerary_key)`.
- upsert same key cheaper → updated; same key more expensive → unchanged (assert price and `source`).
- keyset: insert 25 rows with some equal prices; page through with `limit=10`; assert 10/10/5, no duplicates, no gaps, `next` empty on the last page.
- filters: `maxPrice` excludes; `carrier` matches any segment's carrier.
- round-trip: an `Itinerary` with 3 segments survives persist + load with equal key, prices, segments and source.
- `@Tag("perf")` `KeysetPaginationPerfIT` (excluded from default run): seed 1,000,000 rows with a single `INSERT ... SELECT generate_series`, run `EXPLAIN (ANALYZE, FORMAT JSON)` on the keyset query at page ~900,000 equivalent and on an `OFFSET 900000` variant; assert the keyset plan uses `ix_itineraries_search` and record both execution times in the test output. These numbers go to `SOLUTION.md` §4.3.

## Acceptance

- `./mvnw -q -pl infrastructure test` (ArchUnit still green).
- `./mvnw -q -pl infrastructure verify` runs the ITs green with Docker available.
- `spring.jpa.hibernate.ddl-auto=validate` passes (entity matches Flyway schema).

## Closing

`/check-arch`, `/test-layer it`, `/log-ai-decision`, show diff summary.
