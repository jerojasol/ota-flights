package io.github.jerojasol.flights.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jerojasol.flights.domain.model.Airport;
import io.github.jerojasol.flights.domain.model.SearchCriteria;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

class FreshnessKeysTest {

    private static final SearchCriteria MAD_JFK =
            new SearchCriteria(Airport.of("mad"), Airport.of(" jfk "), LocalDate.parse("2026-11-20"));

    @Test
    void should_build_key_with_uppercase_codes_and_iso_date_when_criteria_is_given() {
        assertThat(FreshnessKeys.fresh(MAD_JFK)).isEqualTo("fresh:MAD:JFK:2026-11-20");
        assertThat(FreshnessKeys.lock(MAD_JFK)).isEqualTo("lock:MAD:JFK:2026-11-20");
        assertThat(FreshnessKeys.seen(MAD_JFK)).isEqualTo("seen:MAD:JFK:2026-11-20");
    }

    @Test
    void should_pad_single_digit_month_and_day_when_building_key() {
        SearchCriteria january = new SearchCriteria(Airport.of("MAD"), Airport.of("JFK"), LocalDate.parse("2027-01-05"));

        assertThat(FreshnessKeys.fresh(january)).isEqualTo("fresh:MAD:JFK:2027-01-05");
    }
}
