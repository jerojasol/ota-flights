package io.github.jerojasol.flights.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jerojasol.flights.infrastructure.persistence.ItineraryPersistenceMapper.ItineraryRow;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import java.math.BigDecimal;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

class ItineraryJdbcUpserterTest {

    private static final ItineraryRow ROW = new ItineraryRow("a".repeat(64), "MAD", "JFK",
            LocalDate.parse("2026-11-20"), "IB", new String[] {"IB"}, new BigDecimal("100.00"),
            new BigDecimal("105.00"), "EUR", "ALPHA", "[]", Timestamp.from(Instant.parse("2026-11-14T10:00:00Z")));

    @Test
    void should_fail_when_driver_reports_success_without_update_count() {
        ItineraryJdbcUpserter upserter = new ItineraryJdbcUpserter(returning(new int[][] {{1, Statement.SUCCESS_NO_INFO}}));

        assertThatThrownBy(() -> upserter.upsert(List.of(ROW, ROW)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not report an update count");
    }

    @Test
    void should_sum_update_counts_when_driver_reports_them() {
        ItineraryJdbcUpserter upserter = new ItineraryJdbcUpserter(returning(new int[][] {{1, 0, 1}}));

        assertThat(upserter.upsert(List.of(ROW, ROW, ROW))).isEqualTo(2);
    }

    private static JdbcTemplate returning(int[][] counts) {
        return new JdbcTemplate() {
            @Override
            public <T> int[][] batchUpdate(String sql, Collection<T> batchArgs, int batchSize,
                                           ParameterizedPreparedStatementSetter<T> pss) {
                return counts;
            }
        };
    }
}
