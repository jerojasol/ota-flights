package io.github.jerojasol.flights.domain.model;

import static io.github.jerojasol.flights.domain.Fixtures.MAD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jerojasol.flights.domain.exception.InvalidAirportException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AirportTest {

    @Test
    void should_create_airport_when_code_is_three_uppercase_letters() { // A1
        assertThat(new Airport("MAD").code()).isEqualTo("MAD");
    }

    @Test
    void should_reject_airport_when_code_is_null() { // A1
        assertThatThrownBy(() -> new Airport(null)).isInstanceOf(InvalidAirportException.class);
        assertThatThrownBy(() -> Airport.of(null)).isInstanceOf(InvalidAirportException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "MA", "MADR"})
    void should_reject_airport_when_code_has_wrong_length(String code) { // A1
        assertThatThrownBy(() -> new Airport(code)).isInstanceOf(InvalidAirportException.class);
        assertThatThrownBy(() -> Airport.of(code)).isInstanceOf(InvalidAirportException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"M4D", "M-D", "MÁD", "ΜAD", "mıd", "ßA", "ﬁA"})
    void should_reject_airport_when_code_has_non_ascii_letter_characters(String code) { // A1
        assertThatThrownBy(() -> Airport.of(code)).isInstanceOf(InvalidAirportException.class);
    }

    @Test
    void should_reject_airport_when_constructor_receives_lowercase_code() { // A1
        assertThatThrownBy(() -> new Airport("mad")).isInstanceOf(InvalidAirportException.class);
    }

    @Test
    void should_reject_airport_when_constructor_receives_untrimmed_code() { // A1
        assertThatThrownBy(() -> new Airport(" MAD ")).isInstanceOf(InvalidAirportException.class);
    }

    @Test
    void should_normalise_code_when_factory_receives_lowercase_untrimmed_code() { // A1
        assertThat(Airport.of(" mad ")).isEqualTo(MAD);
    }

    @Test
    void should_be_equal_when_codes_are_equal() {
        assertThat(new Airport("MAD")).isEqualTo(MAD).hasSameHashCodeAs(MAD);
    }
}
