-- One row per Itinerary Key: the cheapest offer seen for that product, with markup already applied.
CREATE TABLE itineraries (
    id             BIGSERIAL PRIMARY KEY,
    itinerary_key  CHAR(64)      NOT NULL,          -- SHA-256 hex of the canonical segments (domain identity)
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
    -- Deduplication across providers and across aggregations: the upsert's ON CONFLICT target.
    -- Without it two aggregations of the same route would insert the same product twice.
    CONSTRAINT uq_itineraries_key UNIQUE (itinerary_key)
);

-- Keyset pagination for the main search. Every search filters by route + date (equality prefix) and orders by
-- (final_price, itinerary_key), the order defined by the domain's PageCursor. With the sort columns as the index
-- suffix, Postgres seeks straight to the cursor position and reads `limit + 1` entries in index order: the cost of a
-- page does not grow with its depth, and no sort step is needed. itinerary_key (unique) is the tie-break, so rows
-- with equal prices are never skipped or repeated across pages.
CREATE INDEX ix_itineraries_search
    ON itineraries (origin, destination, departure_date, final_price, itinerary_key);

-- Carrier filter: `carrier = ANY(carriers)` has to match any segment's carrier, which a B-tree cannot index.
-- A GIN index on the array answers containment (`carriers @> ARRAY[...]`) without scanning every row of the route.
CREATE INDEX ix_itineraries_carriers ON itineraries USING GIN (carriers);
