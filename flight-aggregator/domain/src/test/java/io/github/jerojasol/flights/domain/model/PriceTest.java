package io.github.jerojasol.flights.domain.model;

import static io.github.jerojasol.flights.domain.Fixtures.EUR;
import static io.github.jerojasol.flights.domain.Fixtures.price;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jerojasol.flights.domain.exception.InvalidPriceException;
import java.math.BigDecimal;
import java.util.Currency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PriceTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void should_create_price_when_amount_and_currency_are_valid() {
        Price price = new Price(new BigDecimal("512.30"), EUR);

        assertThat(price.amount()).isEqualTo(new BigDecimal("512.30"));
        assertThat(price.currency()).isEqualTo(EUR);
    }

    @Test
    void should_accept_price_when_amount_is_zero() { // P1
        assertThat(price("0").amount()).isEqualTo(new BigDecimal("0.00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "-100", "-0.001"})
    void should_reject_price_when_amount_is_negative(String amount) { // P1
        assertThatThrownBy(() -> new Price(new BigDecimal(amount), EUR)).isInstanceOf(InvalidPriceException.class);
    }

    @Test
    void should_reject_price_when_amount_is_null() {
        assertThatThrownBy(() -> new Price(null, EUR)).isInstanceOf(InvalidPriceException.class);
        assertThatThrownBy(() -> Price.of(null, "EUR")).isInstanceOf(InvalidPriceException.class);
    }

    @ParameterizedTest
    @CsvSource({
        "100.005, 100.00",
        "100.015, 100.02",
        "100.025, 100.02",
        "100.0051, 100.01",
        "512.3, 512.30",
        "512, 512.00"
    })
    void should_round_half_even_to_scale_two_when_constructed(String input, String expected) { // P2
        Price price = price(input);

        assertThat(price.amount()).isEqualTo(new BigDecimal(expected));
        assertThat(price.amount().scale()).isEqualTo(2);
    }

    @Test
    void should_be_equal_when_amounts_differ_only_in_scale() { // P2
        assertThat(price("512.3")).isEqualTo(price("512.300")).hasSameHashCodeAs(price("512.300"));
    }

    @Test
    void should_reject_price_when_currency_is_null() { // P3
        assertThatThrownBy(() -> new Price(new BigDecimal("1.00"), null)).isInstanceOf(InvalidPriceException.class);
        assertThatThrownBy(() -> Price.of("1.00", null)).isInstanceOf(InvalidPriceException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "EU", "EURO", "XYZ", "123"})
    void should_reject_price_when_currency_is_not_an_iso_4217_code(String currencyCode) { // P3
        assertThatThrownBy(() -> Price.of("1.00", currencyCode)).isInstanceOf(InvalidPriceException.class);
    }

    @Test
    void should_normalise_amount_and_currency_when_factory_receives_lowercase_untrimmed_text() { // P3
        assertThat(Price.of(" 512.30 ", " eur ")).isEqualTo(price("512.30"));
    }

    @Test
    void should_reject_price_when_factory_amount_is_not_a_number() {
        assertThatThrownBy(() -> Price.of("abc", "EUR")).isInstanceOf(InvalidPriceException.class);
    }

    @Test
    void should_add_amounts_when_currencies_match() {
        assertThat(price("512.30").plus(price("0.75"))).isEqualTo(price("513.05"));
    }

    @Test
    void should_compare_amounts_when_currencies_match() {
        assertThat(price("99.99").isLowerThan(price("100.00"))).isTrue();
        assertThat(price("100.00").isLowerThan(price("99.99"))).isFalse();
        assertThat(price("100.00").isLowerThan(price("100.00"))).isFalse();
    }

    @Test
    void should_reject_plus_when_currencies_differ() { // P4
        Price dollars = new Price(new BigDecimal("10.00"), USD);

        assertThatThrownBy(() -> price("10.00").plus(dollars)).isInstanceOf(InvalidPriceException.class);
    }

    @Test
    void should_reject_is_lower_than_when_currencies_differ() { // P4
        Price dollars = new Price(new BigDecimal("10.00"), USD);

        assertThatThrownBy(() -> price("5.00").isLowerThan(dollars)).isInstanceOf(InvalidPriceException.class);
    }

    @Test
    void should_reject_operation_when_other_price_is_null() {
        assertThatThrownBy(() -> price("10.00").plus(null)).isInstanceOf(InvalidPriceException.class);
        assertThatThrownBy(() -> price("10.00").isLowerThan(null)).isInstanceOf(InvalidPriceException.class);
    }

    @Test
    void should_return_new_price_in_same_currency_when_multiplied() { // P2
        Price base = price("100.00");

        Price result = base.multiply(new BigDecimal("1.05"));

        assertThat(result).isEqualTo(price("105.00"));
        assertThat(result.currency()).isEqualTo(EUR);
        assertThat(base).isEqualTo(price("100.00"));
    }

    @ParameterizedTest
    @CsvSource({
        "99.99, 1.05, 104.99",
        "0.10, 1.05, 0.10",
        "0.30, 1.05, 0.32",
        "100.00, 0, 0.00"
    })
    void should_round_half_even_when_multiplication_exceeds_scale_two(String amount, String factor, String expected) { // P2
        assertThat(price(amount).multiply(new BigDecimal(factor))).isEqualTo(price(expected));
    }

    @Test
    void should_reject_multiply_when_factor_is_negative() { // P1
        assertThatThrownBy(() -> price("100.00").multiply(new BigDecimal("-1"))).isInstanceOf(InvalidPriceException.class);
    }

    @Test
    void should_reject_multiply_when_factor_is_null() {
        assertThatThrownBy(() -> price("100.00").multiply(null)).isInstanceOf(InvalidPriceException.class);
    }
}
