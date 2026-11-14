package io.github.jerojasol.flights.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ProviderOutcomeTest {

    @Test
    void should_reject_outcome_when_rejected_count_is_negative() { // PO1
        assertThatThrownBy(() -> new ProviderOutcome(ProviderStatus.OK, 0, -1, Duration.ZERO))
                .isInstanceOf(InvalidAggregationException.class);
    }

    @Test
    void should_reject_outcome_when_status_is_null() { // PO1
        assertThatThrownBy(() -> new ProviderOutcome(null, 0, 0, Duration.ZERO))
                .isInstanceOf(InvalidAggregationException.class);
    }

    @Test
    void should_reject_outcome_when_elapsed_is_null() { // PO1
        assertThatThrownBy(() -> new ProviderOutcome(ProviderStatus.OK, 0, 0, null))
                .isInstanceOf(InvalidAggregationException.class);
    }

    @Test
    void should_accept_outcome_when_counts_are_zero_and_elapsed_is_zero() { // PO1
        ProviderOutcome outcome = new ProviderOutcome(ProviderStatus.TIMEOUT, 0, 0, Duration.ZERO);

        assertThat(outcome.succeeded()).isFalse();
        assertThat(outcome.offersRejected()).isZero();
    }
}
