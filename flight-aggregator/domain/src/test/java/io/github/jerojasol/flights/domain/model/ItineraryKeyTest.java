package io.github.jerojasol.flights.domain.model;

import static io.github.jerojasol.flights.domain.Fixtures.IB4120;
import static io.github.jerojasol.flights.domain.Fixtures.IB6251;
import static io.github.jerojasol.flights.domain.Fixtures.JFK;
import static io.github.jerojasol.flights.domain.Fixtures.MAD;
import static io.github.jerojasol.flights.domain.Fixtures.MIA;
import static io.github.jerojasol.flights.domain.Fixtures.price;
import static io.github.jerojasol.flights.domain.Fixtures.segment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class ItineraryKeyTest {

    private static final Segment FIRST = segment(IB6251, MAD, "2026-11-20T07:35:00Z", JFK, "2026-11-20T13:50:00Z");
    private static final Segment SECOND = segment(IB4120, JFK, "2026-11-20T14:10:00Z", MIA, "2026-11-20T17:30:00Z");

    @Test
    void should_build_canonical_form_from_domain_model_example_when_two_segments() { // §4
        assertThat(ItineraryKey.canonical(List.of(FIRST, SECOND)))
                .isEqualTo("IB6251@2026-11-20T07:35Z>MAD-JFK|IB4120@2026-11-20T14:10Z>JFK-MIA");
    }

    @Test
    void should_be_lowercase_sha256_hex_of_canonical_form() {
        ItineraryKey key = ItineraryKey.of(List.of(FIRST, SECOND));

        assertThat(key.value()).matches("[0-9a-f]{64}");
        assertThat(key.value()).isEqualTo("f86f0e81cead7cec7c6b1c71ff00d4fba322fc47efbcf47c3c01808d7f44705f");
    }

    @Test
    void should_be_equal_when_providers_and_prices_differ() {
        Offer alpha = new Offer(Provider.ALPHA, List.of(FIRST, SECOND), price("500.00"));
        Offer gamma = new Offer(Provider.GAMMA, List.of(FIRST, SECOND), price("480.00"));

        assertThat(ItineraryKey.of(alpha.segments()))
                .isEqualTo(ItineraryKey.of(gamma.segments()))
                .hasSameHashCodeAs(ItineraryKey.of(gamma.segments()));
    }

    @Test
    void should_differ_when_departure_differs_by_one_minute() {
        Segment shifted = segment(IB6251, MAD, "2026-11-20T07:36:00Z", JFK, "2026-11-20T13:50:00Z");

        assertThat(ItineraryKey.of(List.of(shifted, SECOND))).isNotEqualTo(ItineraryKey.of(List.of(FIRST, SECOND)));
    }

    @Test
    void should_be_equal_when_departure_differs_only_in_seconds() {
        Segment withSeconds = segment(IB6251, MAD, "2026-11-20T07:35:42Z", JFK, "2026-11-20T13:50:00Z");

        assertThat(ItineraryKey.of(List.of(withSeconds, SECOND))).isEqualTo(ItineraryKey.of(List.of(FIRST, SECOND)));
    }

    @Test
    void should_be_equal_when_arrival_differs() {
        Segment otherArrival = segment(IB6251, MAD, "2026-11-20T07:35:00Z", JFK, "2026-11-20T14:05:00Z");

        assertThat(ItineraryKey.of(List.of(otherArrival, SECOND))).isEqualTo(ItineraryKey.of(List.of(FIRST, SECOND)));
    }

    @Test
    void should_be_equal_when_flight_number_differs_only_in_leading_zeros() { // §4 key normalisation
        Segment shortOne = segment(FlightNumber.of("IB117"), MAD, "2026-11-20T07:35:00Z", JFK, "2026-11-20T13:50:00Z");
        Segment shortPadded = segment(FlightNumber.of("IB0117"), MAD, "2026-11-20T07:35:00Z", JFK, "2026-11-20T13:50:00Z");

        assertThat(ItineraryKey.of(List.of(shortOne))).isEqualTo(ItineraryKey.of(List.of(shortPadded)));
    }

    @Test
    void should_differ_when_segment_order_is_swapped() {
        assertThat(ItineraryKey.of(List.of(SECOND, FIRST))).isNotEqualTo(ItineraryKey.of(List.of(FIRST, SECOND)));
    }

    @Test
    void should_reject_key_when_segments_are_null_or_empty() {
        assertThatThrownBy(() -> ItineraryKey.of(List.of())).isInstanceOf(InvalidItineraryKeyException.class);
        assertThatThrownBy(() -> ItineraryKey.of(null)).isInstanceOf(InvalidItineraryKeyException.class);
    }

    @Test
    void should_reject_stored_key_when_value_is_not_sha256_hex() {
        assertThatThrownBy(() -> ItineraryKey.fromStored("x")).isInstanceOf(InvalidItineraryKeyException.class);
        assertThatThrownBy(() -> ItineraryKey.fromStored(null)).isInstanceOf(InvalidItineraryKeyException.class);
        assertThatThrownBy(() -> ItineraryKey.fromStored("F".repeat(64))).isInstanceOf(InvalidItineraryKeyException.class);
    }

    @Test
    void should_round_trip_value_when_rebuilt_from_stored() {
        ItineraryKey key = ItineraryKey.of(List.of(FIRST, SECOND));

        assertThat(ItineraryKey.fromStored(key.value())).isEqualTo(key);
    }
}
