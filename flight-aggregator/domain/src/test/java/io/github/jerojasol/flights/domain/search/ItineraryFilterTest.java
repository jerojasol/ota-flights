package io.github.jerojasol.flights.domain.search;

import io.github.jerojasol.flights.domain.exception.InvalidSearchException;
import static io.github.jerojasol.flights.domain.Fixtures.IB;
import static io.github.jerojasol.flights.domain.Fixtures.JFK;
import static io.github.jerojasol.flights.domain.Fixtures.MAD;
import static io.github.jerojasol.flights.domain.Fixtures.price;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ItineraryFilterTest {

    @Test
    void should_have_all_components_empty_when_filter_is_none() {
        ItineraryFilter none = ItineraryFilter.none();

        assertThat(none.origin()).isEmpty();
        assertThat(none.destination()).isEmpty();
        assertThat(none.maxPrice()).isEmpty();
        assertThat(none.carrier()).isEmpty();
    }

    @Test
    void should_combine_components_when_with_methods_are_chained() {
        ItineraryFilter filter = ItineraryFilter.none().withOrigin(MAD).withDestination(JFK)
                .withMaxPrice(price("500.00")).withCarrier(IB);

        assertThat(filter.origin()).contains(MAD);
        assertThat(filter.destination()).contains(JFK);
        assertThat(filter.maxPrice()).contains(price("500.00"));
        assertThat(filter.carrier()).contains(IB);
    }

    @Test
    void should_not_change_original_when_with_method_is_used() {
        ItineraryFilter none = ItineraryFilter.none();

        none.withCarrier(IB);

        assertThat(none.carrier()).isEmpty();
    }

    @Test
    void should_clear_component_when_with_method_receives_null() {
        ItineraryFilter filter = ItineraryFilter.none().withCarrier(IB).withCarrier(null);

        assertThat(filter.carrier()).isEmpty();
    }

    @Test
    void should_reject_filter_when_a_component_is_null() {
        assertThatThrownBy(() -> new ItineraryFilter(null, java.util.Optional.empty(), java.util.Optional.empty(),
                java.util.Optional.empty())).isInstanceOf(InvalidSearchException.class);
    }
}
