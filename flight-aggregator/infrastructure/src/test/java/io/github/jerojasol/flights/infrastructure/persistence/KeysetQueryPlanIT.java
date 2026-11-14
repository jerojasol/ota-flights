package io.github.jerojasol.flights.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Proves the later-pages query keeps its keyset position as an index condition under a generic plan, the plan a
 * server-side prepared statement ends up with after a few executions. If the cursor predicate ends up in a Filter,
 * Postgres reads every index entry before the cursor and deep pages get slower with depth.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class KeysetQueryPlanIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    /** Parameter order of ItineraryJpaRepository.findNextPage, with the Postgres type of each. */
    private static final List<String> PARAMETERS = List.of(
            "origin", "destination", "date", "maxPrice", "carrier", "currency", "cursorPrice", "cursorKey",
            "limitPlusOne");
    private static final String PARAMETER_TYPES = "varchar, varchar, date, numeric, varchar, varchar, numeric, varchar, int";
    private static final String ROW_COMPARISON = "ROW(final_price, itinerary_key) > ROW(";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void should_use_cursor_as_index_condition_when_next_page_runs_with_generic_plan() {
        seed();

        String planJson = jdbc.execute((ConnectionCallback<String>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET plan_cache_mode = force_generic_plan");
                statement.execute("PREPARE next_page(" + PARAMETER_TYPES + ") AS "
                        + positional(ItineraryJpaRepository.NEXT_PAGE));
                try (ResultSet plan = statement.executeQuery("""
                        EXPLAIN (FORMAT JSON) EXECUTE next_page('MAD', 'JFK', DATE '2026-11-20', NULL, NULL, NULL,
                                                                 500.00, '%s', 21)
                        """.formatted("8".repeat(64)))) {
                    plan.next();
                    String json = plan.getString(1);
                    statement.execute("DEALLOCATE next_page");
                    statement.execute("RESET plan_cache_mode");
                    return json;
                }
            }
        });

        List<String> indexConditions = new ArrayList<>();
        List<String> filters = new ArrayList<>();
        List<String> indexNames = new ArrayList<>();
        collect(JsonMapper.builder().build().readTree(planJson), indexConditions, filters, indexNames);

        assertThat(indexNames).contains("ix_itineraries_search");
        assertThat(indexConditions).anySatisfy(condition -> assertThat(condition).contains(ROW_COMPARISON));
        assertThat(filters).noneSatisfy(filter -> assertThat(filter).contains(ROW_COMPARISON));
    }

    private void seed() {
        jdbc.execute("TRUNCATE itineraries");
        jdbc.update("""
                INSERT INTO itineraries (itinerary_key, origin, destination, departure_date, first_carrier, carriers,
                                         base_price, final_price, currency, source, segments, ingested_at)
                SELECT encode(sha256(i::text::bytea), 'hex'),
                       CASE WHEN i % 10 = 0 THEN 'MAD' ELSE 'LHR' END, 'JFK', DATE '2026-11-20', 'IB',
                       ARRAY['IB']::char(2)[], (100 + i % 900)::numeric(12,2),
                       round((100 + i % 900) * 1.05, 2)::numeric(12,2), 'EUR', 'ALPHA', '[]'::jsonb, now()
                FROM generate_series(1, 50000) AS i
                """);
        jdbc.execute("ANALYZE itineraries");
    }

    /** Rewrites the repository's named parameters (:name) to the $n placeholders PREPARE expects. */
    private static String positional(String sql) {
        Matcher matcher = Pattern.compile(":(\\w+)").matcher(sql);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            int index = PARAMETERS.indexOf(matcher.group(1));
            assertThat(index).as("known parameter :" + matcher.group(1)).isNotNegative();
            matcher.appendReplacement(out, "\\$" + (index + 1));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static void collect(JsonNode node, List<String> indexConditions, List<String> filters,
                                List<String> indexNames) {
        if (node.isArray()) {
            node.forEach(child -> collect(child, indexConditions, filters, indexNames));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        if (node.has("Index Cond")) {
            indexConditions.add(node.get("Index Cond").asString());
        }
        if (node.has("Filter")) {
            filters.add(node.get("Filter").asString());
        }
        if (node.has("Index Name")) {
            indexNames.add(node.get("Index Name").asString());
        }
        node.forEach(child -> collect(child, indexConditions, filters, indexNames));
    }
}
