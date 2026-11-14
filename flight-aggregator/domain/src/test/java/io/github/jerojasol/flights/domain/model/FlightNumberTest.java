package io.github.jerojasol.flights.domain.model;

import static io.github.jerojasol.flights.domain.Fixtures.BA;
import static io.github.jerojasol.flights.domain.Fixtures.IB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FlightNumberTest {

    @Test
    void should_expose_carrier_and_number_when_flight_number_is_valid() { // F1
        FlightNumber flightNumber = FlightNumber.of("IB6251");

        assertThat(flightNumber.carrier()).isEqualTo(IB);
        assertThat(flightNumber.number()).isEqualTo(6251);
    }

    @ParameterizedTest
    @ValueSource(strings = {"IB1", "IB12", "IB123", "IB1234"})
    void should_create_flight_number_when_number_has_one_to_four_digits(String flightNumber) { // F1
        assertThat(FlightNumber.of(flightNumber).carrier()).isEqualTo(IB);
    }

    @Test
    void should_create_flight_number_when_carrier_code_contains_a_digit() { // F1
        FlightNumber flightNumber = FlightNumber.of("U21234");

        assertThat(flightNumber.carrier()).isEqualTo(new Carrier("U2"));
        assertThat(flightNumber.number()).isEqualTo(1234);
    }

    @Test
    void should_be_equal_when_numbers_differ_only_in_leading_zeros() { // F1
        assertThat(FlightNumber.of("IB0117"))
                .isEqualTo(FlightNumber.of("IB117"))
                .isEqualTo(new FlightNumber(IB, 117))
                .hasSameHashCodeAs(FlightNumber.of("IB117"));
    }

    @Test
    void should_not_be_equal_when_carriers_differ() {
        assertThat(FlightNumber.of("IB117")).isNotEqualTo(new FlightNumber(BA, 117));
    }

    @Test
    void should_normalise_flight_number_when_factory_receives_lowercase_untrimmed_text() { // F1
        assertThat(FlightNumber.of(" ib6251 ")).isEqualTo(new FlightNumber(IB, 6251));
    }

    @Test
    void should_reject_flight_number_when_text_is_null() { // F1
        assertThatThrownBy(() -> FlightNumber.of(null)).isInstanceOf(InvalidFlightNumberException.class);
    }

    @Test
    void should_reject_flight_number_when_carrier_is_null() { // F1
        assertThatThrownBy(() -> new FlightNumber(null, 117)).isInstanceOf(InvalidFlightNumberException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "IB", "IB12345", "IB00117", "I6251X", "IB-117", "IB 117", "IB11A"})
    void should_reject_flight_number_when_text_is_not_carrier_plus_one_to_four_digits(String flightNumber) { // F1
        assertThatThrownBy(() -> FlightNumber.of(flightNumber)).isInstanceOf(InvalidFlightNumberException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 10000})
    void should_reject_flight_number_when_number_is_out_of_range(int number) { // F1
        assertThatThrownBy(() -> new FlightNumber(IB, number)).isInstanceOf(InvalidFlightNumberException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"IB0", "IB0000"})
    void should_reject_flight_number_when_number_is_all_zeros(String flightNumber) { // F1
        assertThatThrownBy(() -> FlightNumber.of(flightNumber)).isInstanceOf(InvalidFlightNumberException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ß12", "ıb117", "ﬁ117"})
    void should_reject_flight_number_when_carrier_only_becomes_ascii_after_uppercasing(String flightNumber) { // F1
        assertThatThrownBy(() -> FlightNumber.of(flightNumber)).isInstanceOf(InvalidFlightNumberException.class);
    }

    @Test
    void should_parse_first_two_characters_as_carrier_when_text_is_all_digits() { // F1, C1
        assertThat(FlightNumber.of("6251")).isEqualTo(new FlightNumber(new Carrier("62"), 51));
    }
}
