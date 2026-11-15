package io.github.jerojasol.flights.domain.model;

import static io.github.jerojasol.flights.domain.Fixtures.price;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jerojasol.flights.domain.exception.InvalidPriceException;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PercentageMarkupPolicyTest {

    private final PercentageMarkupPolicy fivePercent = new PercentageMarkupPolicy(new BigDecimal("5"));

    @ParameterizedTest
    @CsvSource({
        "100.00, 105.00",
        "99.99, 104.99",
        "0.00, 0.00",
        "0.50, 0.52",
        "512.30, 537.92"
    })
    void should_add_five_percent_rounded_half_even_when_applied(String base, String expected) { // I10
        assertThat(fivePercent.apply(price(base))).isEqualTo(price(expected));
    }

    @Test
    void should_keep_scale_two_and_currency_when_applied() { // P2
        Price result = fivePercent.apply(price("12.34"));

        assertThat(result.amount().scale()).isEqualTo(2);
        assertThat(result.currency()).isEqualTo(price("1").currency());
    }

    @Test
    void should_return_base_price_when_percentage_is_zero() {
        assertThat(new PercentageMarkupPolicy(BigDecimal.ZERO).apply(price("99.99"))).isEqualTo(price("99.99"));
    }

    @Test
    void should_apply_other_percentage_when_configured() {
        assertThat(new PercentageMarkupPolicy(new BigDecimal("10")).apply(price("200.00"))).isEqualTo(price("220.00"));
    }

    @Test
    void should_reject_policy_when_percentage_is_negative_or_null() {
        assertThatThrownBy(() -> new PercentageMarkupPolicy(new BigDecimal("-1"))).isInstanceOf(InvalidPriceException.class);
        assertThatThrownBy(() -> new PercentageMarkupPolicy(null)).isInstanceOf(InvalidPriceException.class);
    }
}
