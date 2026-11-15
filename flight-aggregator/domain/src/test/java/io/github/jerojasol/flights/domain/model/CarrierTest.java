package io.github.jerojasol.flights.domain.model;

import static io.github.jerojasol.flights.domain.Fixtures.IB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jerojasol.flights.domain.exception.InvalidCarrierException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CarrierTest {

    @ParameterizedTest
    @ValueSource(strings = {"IB", "U2", "3K", "9W"})
    void should_create_carrier_when_code_is_two_uppercase_alphanumerics(String code) { // C1
        assertThat(new Carrier(code).code()).isEqualTo(code);
    }

    @Test
    void should_reject_carrier_when_code_is_null() { // C1
        assertThatThrownBy(() -> new Carrier(null)).isInstanceOf(InvalidCarrierException.class);
        assertThatThrownBy(() -> Carrier.of(null)).isInstanceOf(InvalidCarrierException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "I", "IBE"})
    void should_reject_carrier_when_code_has_wrong_length(String code) { // C1
        assertThatThrownBy(() -> new Carrier(code)).isInstanceOf(InvalidCarrierException.class);
        assertThatThrownBy(() -> Carrier.of(code)).isInstanceOf(InvalidCarrierException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"I-", "I ", "ÑB"})
    void should_reject_carrier_when_code_has_non_ascii_alphanumeric_characters(String code) { // C1
        assertThatThrownBy(() -> new Carrier(code)).isInstanceOf(InvalidCarrierException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ß", "ıb", "ﬁ"})
    void should_reject_carrier_when_factory_input_only_becomes_ascii_after_uppercasing(String code) { // C1
        assertThatThrownBy(() -> Carrier.of(code)).isInstanceOf(InvalidCarrierException.class);
    }

    @Test
    void should_reject_carrier_when_constructor_receives_lowercase_code() { // C1
        assertThatThrownBy(() -> new Carrier("ib")).isInstanceOf(InvalidCarrierException.class);
    }

    @Test
    void should_normalise_code_when_factory_receives_lowercase_untrimmed_code() { // C1
        assertThat(Carrier.of(" ib ")).isEqualTo(IB);
        assertThat(Carrier.of("u2")).isEqualTo(new Carrier("U2"));
    }

    @Test
    void should_be_equal_when_codes_are_equal() {
        assertThat(new Carrier("IB")).isEqualTo(IB).hasSameHashCodeAs(IB);
    }
}
