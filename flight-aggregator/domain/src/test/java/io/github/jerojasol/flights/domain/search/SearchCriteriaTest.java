package io.github.jerojasol.flights.domain.search;

import io.github.jerojasol.flights.domain.exception.InvalidSearchException;
import static io.github.jerojasol.flights.domain.Fixtures.JFK;
import static io.github.jerojasol.flights.domain.Fixtures.MAD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class SearchCriteriaTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 20);

    @Test
    void should_create_criteria_when_origin_and_destination_differ() {
        assertThat(new SearchCriteria(MAD, JFK, DAY).departureDate()).isEqualTo(DAY);
    }

    @Test
    void should_reject_criteria_when_origin_equals_destination() {
        assertThatThrownBy(() -> new SearchCriteria(MAD, MAD, DAY)).isInstanceOf(InvalidSearchException.class);
    }

    @Test
    void should_reject_criteria_when_any_component_is_null() {
        assertThatThrownBy(() -> new SearchCriteria(null, JFK, DAY)).isInstanceOf(InvalidSearchException.class);
        assertThatThrownBy(() -> new SearchCriteria(MAD, null, DAY)).isInstanceOf(InvalidSearchException.class);
        assertThatThrownBy(() -> new SearchCriteria(MAD, JFK, null)).isInstanceOf(InvalidSearchException.class);
    }
}
