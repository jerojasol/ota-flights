package io.github.jerojasol.flights.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Keyset vs OFFSET at depth on 1,000,000 rows of one route and date. Excluded from the default run; execute with
 * {@code ./mvnw verify -Pperf}. The printed timings feed SOLUTION.md §4.3.
 */
@Tag("perf")
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class KeysetPaginationPerfIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final int ROWS = 1_000_000;
    private static final int DEPTH = 900_000;
    private static final int PAGE = 20;

    /**
     * Same predicates as ItineraryJpaRepository.NEXT_PAGE, with the optional filters absent. The route is bound as
     * String parameters (sent as varchar, like the repository's) rather than literals, which Postgres would type
     * as char and so hide a cast on the indexed column.
     */
    private static final String KEYSET = """
            EXPLAIN (ANALYZE, FORMAT JSON)
            SELECT * FROM itineraries
            WHERE origin = CAST(? AS char(3)) AND destination = CAST(? AS char(3))
              AND departure_date = DATE '2026-11-20'
              AND (CAST(NULL AS numeric) IS NULL OR final_price <= CAST(NULL AS numeric))
              AND (CAST(NULL AS char(2)) IS NULL OR carriers @> ARRAY[CAST(NULL AS char(2))])
              AND (CAST(NULL AS char(3)) IS NULL OR currency = CAST(NULL AS char(3)))
              AND (final_price, itinerary_key) > (CAST(? AS numeric), CAST(? AS char(64)))
            ORDER BY final_price, itinerary_key
            LIMIT %d
            """.formatted(PAGE + 1);

    private static final String OFFSET = """
            EXPLAIN (ANALYZE, FORMAT JSON)
            SELECT * FROM itineraries
            WHERE origin = 'MAD' AND destination = 'JFK' AND departure_date = DATE '2026-11-20'
            ORDER BY final_price, itinerary_key
            OFFSET %d LIMIT %d
            """.formatted(DEPTH, PAGE + 1);

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void should_use_search_index_and_beat_offset_when_paging_at_depth() {
        seed();
        Map<String, Object> cursor = jdbc.queryForMap("""
                SELECT final_price, itinerary_key FROM itineraries
                WHERE origin = 'MAD' AND destination = 'JFK' AND departure_date = DATE '2026-11-20'
                ORDER BY final_price, itinerary_key OFFSET ? LIMIT 1
                """, DEPTH - 1);
        BigDecimal price = (BigDecimal) cursor.get("final_price");
        String key = (String) cursor.get("itinerary_key");

        explain(OFFSET); // warm the buffer cache so the first measured plan is not penalised
        String keysetPlan = explain(KEYSET, "MAD", "JFK", price, key);
        String offsetPlan = explain(OFFSET);
        double keysetMs = executionTime(keysetPlan);
        double offsetMs = executionTime(offsetPlan);

        System.out.printf("perf rows=%d depth=%d page=%d keyset_ms=%.3f offset_ms=%.3f%n",
                ROWS, DEPTH, PAGE, keysetMs, offsetMs);
        assertThat(keysetPlan).contains("\"Index Name\": \"ix_itineraries_search\"");
        assertThat(keysetMs).isLessThan(offsetMs);
    }

    private void seed() {
        jdbc.execute("TRUNCATE itineraries");
        jdbc.update("""
                INSERT INTO itineraries (itinerary_key, origin, destination, departure_date, first_carrier, carriers,
                                         base_price, final_price, currency, source, segments, ingested_at)
                SELECT encode(sha256(i::text::bytea), 'hex'), 'MAD', 'JFK', DATE '2026-11-20', 'IB',
                       ARRAY['IB']::char(2)[],
                       (100 + i % 5000)::numeric(12,2),
                       round((100 + i % 5000) * 1.05, 2)::numeric(12,2),
                       'EUR', 'ALPHA', '[]'::jsonb, now()
                FROM generate_series(1, ?) AS i
                """, ROWS);
        jdbc.execute("ANALYZE itineraries");
    }

    private String explain(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    private static double executionTime(String planJson) {
        int at = planJson.indexOf("\"Execution Time\":");
        assertThat(at).as("plan has an execution time").isNotNegative();
        String rest = planJson.substring(at + "\"Execution Time\":".length()).trim();
        return Double.parseDouble(rest.split("[,\\s}]")[0]);
    }
}
