package io.github.jerojasol.flights.domain.model;

import static io.github.jerojasol.flights.domain.Fixtures.instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AggregationResultTest {

    private static ProviderOutcome outcome(ProviderStatus status) {
        return new ProviderOutcome(status, 0, 0, Duration.ofMillis(120));
    }

    private static AggregationResult result(ProviderStatus alpha, ProviderStatus beta, ProviderStatus gamma) {
        return new AggregationResult(0, Map.of(Provider.ALPHA, outcome(alpha), Provider.BETA, outcome(beta),
                Provider.GAMMA, outcome(gamma)), instant("2026-11-14T10:00:00Z"));
    }

    @Test
    void should_report_all_failed_when_no_provider_is_ok() {
        AggregationResult result = result(ProviderStatus.TIMEOUT, ProviderStatus.ERROR, ProviderStatus.CIRCUIT_OPEN);

        assertThat(result.allFailed()).isTrue();
        assertThat(result.anySucceeded()).isFalse();
    }

    @Test
    void should_report_any_succeeded_when_at_least_one_provider_is_ok() {
        AggregationResult result = result(ProviderStatus.ERROR, ProviderStatus.OK, ProviderStatus.TIMEOUT);

        assertThat(result.anySucceeded()).isTrue();
        assertThat(result.allFailed()).isFalse();
    }

    @Test
    void should_report_all_succeeded_when_every_provider_is_ok() {
        AggregationResult result = result(ProviderStatus.OK, ProviderStatus.OK, ProviderStatus.OK);

        assertThat(result.anySucceeded()).isTrue();
        assertThat(result.allFailed()).isFalse();
    }

    @Test
    void should_reject_result_when_outcomes_are_empty_or_count_is_negative() {
        assertThatThrownBy(() -> new AggregationResult(0, Map.of(), instant("2026-11-14T10:00:00Z")))
                .isInstanceOf(InvalidAggregationException.class);
        assertThatThrownBy(() -> new AggregationResult(-1, Map.of(Provider.ALPHA, outcome(ProviderStatus.OK)),
                instant("2026-11-14T10:00:00Z"))).isInstanceOf(InvalidAggregationException.class);
    }

    @Test
    void should_reject_outcome_when_counts_or_elapsed_are_negative() { // PO1
        assertThatThrownBy(() -> new ProviderOutcome(ProviderStatus.OK, -1, 0, Duration.ZERO))
                .isInstanceOf(InvalidAggregationException.class);
        assertThatThrownBy(() -> new ProviderOutcome(ProviderStatus.OK, 0, 0, Duration.ofMillis(-1)))
                .isInstanceOf(InvalidAggregationException.class);
    }

    @Test
    void should_carry_provider_and_status_when_provider_is_unavailable() {
        ProviderUnavailableException exception = new ProviderUnavailableException(Provider.BETA, ProviderStatus.TIMEOUT);

        assertThat(exception.provider()).isEqualTo(Provider.BETA);
        assertThat(exception.status()).isEqualTo(ProviderStatus.TIMEOUT);
    }

    @Test
    void should_reject_unavailable_exception_when_status_is_ok() {
        assertThatThrownBy(() -> new ProviderUnavailableException(Provider.BETA, ProviderStatus.OK))
                .isInstanceOf(InvalidAggregationException.class);
    }
}
