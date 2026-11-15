package io.github.jerojasol.flights.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.List;

class AggregationPropertiesTest {

    private static final Currency EUR = Currency.getInstance("EUR");

    @Test
    void should_accept_the_default_values() {
        AggregationProperties properties = properties(Duration.ofMinutes(10), Duration.ofSeconds(30),
                Duration.ofMillis(3500), List.of("MAD-JFK", " mad-lhr ", ""));

        assertThat(properties.warmRoutes()).containsExactly("MAD-JFK", "mad-lhr");
    }

    @Test
    void should_fail_with_a_clear_message_when_lock_ttl_is_shorter_than_twice_the_provider_budget() {
        assertThatThrownBy(() -> properties(Duration.ofMinutes(10), Duration.ofSeconds(6), Duration.ofMillis(3500), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregation.lock-ttl").hasMessageContaining("twice");
    }

    @Test
    void should_fail_when_freshness_ttl_is_not_longer_than_lock_ttl() {
        assertThatThrownBy(() -> properties(Duration.ofSeconds(30), Duration.ofSeconds(30), Duration.ofSeconds(3), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregation.freshness-ttl");
    }

    @Test
    void should_fail_when_markup_is_negative() {
        assertThatThrownBy(() -> new AggregationProperties(new BigDecimal("-1"), EUR, Duration.ofMinutes(10),
                Duration.ofSeconds(30), Duration.ofSeconds(3), List.of(), Duration.ofMinutes(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("markup-percentage");
    }

    @Test
    void should_fail_when_a_warm_route_has_the_wrong_shape() {
        assertThatThrownBy(() -> properties(Duration.ofMinutes(10), Duration.ofSeconds(30), Duration.ofSeconds(3),
                List.of("MADJFK")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MADJFK");
    }

    @Test
    void should_fail_when_warm_interval_is_shorter_than_freshness_ttl_and_routes_are_set() {
        assertThatThrownBy(() -> new AggregationProperties(new BigDecimal("5"), EUR, Duration.ofMinutes(10),
                Duration.ofSeconds(30), Duration.ofSeconds(3), List.of("MAD-JFK"), Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregation.warm-interval").hasMessageContaining("freshness-ttl");
    }

    @Test
    void should_accept_warm_interval_equal_to_freshness_ttl() {
        assertThat(new AggregationProperties(new BigDecimal("5"), EUR, Duration.ofMinutes(10), Duration.ofSeconds(30),
                Duration.ofSeconds(3), List.of("MAD-JFK"), Duration.ofMinutes(10)).warmInterval())
                .isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void should_not_check_warm_interval_when_no_routes_are_set() {
        assertThat(new AggregationProperties(new BigDecimal("5"), EUR, Duration.ofMinutes(10), Duration.ofSeconds(30),
                Duration.ofSeconds(3), List.of(), Duration.ofMinutes(5)).warmRoutes()).isEmpty();
    }

    @Test
    void should_treat_missing_warm_routes_as_none() {
        assertThat(properties(Duration.ofMinutes(10), Duration.ofSeconds(30), Duration.ofSeconds(3), null).warmRoutes())
                .isEmpty();
    }

    private static AggregationProperties properties(Duration freshness, Duration lock, Duration budget, List<String> routes) {
        return new AggregationProperties(new BigDecimal("5"), EUR, freshness, lock, budget, routes, Duration.ofMinutes(10));
    }
}
