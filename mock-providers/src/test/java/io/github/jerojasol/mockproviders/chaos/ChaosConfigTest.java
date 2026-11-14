package io.github.jerojasol.mockproviders.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ChaosConfigTest {

    @Test
    void should_accept_config_when_values_are_in_range() {
        ChaosConfig config = new ChaosConfig(true, Duration.ZERO, Duration.ofSeconds(30), 1.0);

        assertThat(config.errorRate()).isEqualTo(1.0);
    }

    @Test
    void should_reject_config_when_error_rate_is_outside_unit_interval() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChaosConfig(true, Duration.ZERO, Duration.ZERO, 1.01));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChaosConfig(true, Duration.ZERO, Duration.ZERO, -0.1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChaosConfig(true, Duration.ZERO, Duration.ZERO, Double.NaN));
    }

    @Test
    void should_reject_config_when_latency_bounds_are_inverted_negative_or_too_large() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChaosConfig(true, Duration.ofSeconds(2), Duration.ofSeconds(1), 0.0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChaosConfig(true, Duration.ofMillis(-1), Duration.ZERO, 0.0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChaosConfig(true, Duration.ZERO, Duration.ofSeconds(31), 0.0));
    }
}
