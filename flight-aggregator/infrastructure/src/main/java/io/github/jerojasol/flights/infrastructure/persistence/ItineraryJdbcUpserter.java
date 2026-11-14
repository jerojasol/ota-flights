package io.github.jerojasol.flights.infrastructure.persistence;

import io.github.jerojasol.flights.infrastructure.persistence.ItineraryPersistenceMapper.ItineraryRow;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/**
 * Batched upsert. On a key conflict the row is only rewritten when the incoming Final Price is strictly lower and in
 * the same currency, so the table always holds the cheapest offer seen per Itinerary Key, whatever order aggregations
 * arrive in. Prices in different currencies are not comparable (P4): such a conflict writes nothing and counts 0.
 */
final class ItineraryJdbcUpserter {

    static final int CHUNK_SIZE = 1_000;

    private static final String UPSERT = """
            INSERT INTO itineraries (itinerary_key, origin, destination, departure_date, first_carrier, carriers,
                                     base_price, final_price, currency, source, segments, ingested_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
            ON CONFLICT (itinerary_key) DO UPDATE
            SET base_price = EXCLUDED.base_price, final_price = EXCLUDED.final_price,
                source = EXCLUDED.source, segments = EXCLUDED.segments, ingested_at = EXCLUDED.ingested_at
            WHERE EXCLUDED.final_price < itineraries.final_price
              AND EXCLUDED.currency = itineraries.currency
            """;

    private final JdbcTemplate jdbc;

    ItineraryJdbcUpserter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns the number of rows inserted or updated. */
    int upsert(List<ItineraryRow> rows) {
        int written = 0;
        for (int from = 0; from < rows.size(); from += CHUNK_SIZE) {
            List<ItineraryRow> chunk = rows.subList(from, Math.min(from + CHUNK_SIZE, rows.size()));
            int[][] counts = jdbc.batchUpdate(UPSERT, chunk, chunk.size(), ItineraryJdbcUpserter::bind);
            for (int[] batch : counts) {
                for (int count : batch) {
                    // A negative count (e.g. SUCCESS_NO_INFO with reWriteBatchedInserts=true) means the driver did
                    // not report rows written. Failing beats silently reporting 0 persisted itineraries.
                    if (count < 0) {
                        throw new IllegalStateException("JDBC driver did not report an update count (got " + count
                                + "); upsertAll cannot return rows written. Is reWriteBatchedInserts enabled?");
                    }
                    written += count;
                }
            }
        }
        return written;
    }

    private static void bind(PreparedStatement ps, ItineraryRow row) throws SQLException {
        Connection connection = ps.getConnection();
        Array carriers = connection.createArrayOf("bpchar", row.carriers());
        ps.setString(1, row.itineraryKey());
        ps.setString(2, row.origin());
        ps.setString(3, row.destination());
        ps.setObject(4, row.departureDate(), Types.DATE);
        ps.setString(5, row.firstCarrier());
        ps.setArray(6, carriers);
        ps.setBigDecimal(7, row.basePrice());
        ps.setBigDecimal(8, row.finalPrice());
        ps.setString(9, row.currency());
        ps.setString(10, row.source());
        ps.setString(11, row.segmentsJson());
        ps.setTimestamp(12, row.ingestedAt());
    }
}
