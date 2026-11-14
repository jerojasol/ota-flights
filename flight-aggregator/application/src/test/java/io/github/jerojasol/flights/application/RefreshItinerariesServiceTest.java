package io.github.jerojasol.flights.application;

import static io.github.jerojasol.flights.application.Fixtures.MAD_JFK;
import static io.github.jerojasol.flights.domain.model.Provider.ALPHA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jerojasol.flights.application.fakes.InMemoryFreshness;
import io.github.jerojasol.flights.application.fakes.RecordingAggregator;
import io.github.jerojasol.flights.domain.model.AggregationResult;
import io.github.jerojasol.flights.domain.model.Freshness;
import io.github.jerojasol.flights.domain.model.ProviderOutcome;
import io.github.jerojasol.flights.domain.model.ProviderStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

class RefreshItinerariesServiceTest {

    private static final Duration FRESHNESS_TTL = Duration.ofMinutes(10);
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final AggregationResult SUCCEEDED = result(ProviderStatus.OK);
    private static final AggregationResult ALL_FAILED = result(ProviderStatus.ERROR);

    @Test
    void should_aggregate_mark_fresh_and_release_when_lock_is_free() {
        InMemoryFreshness freshness = new InMemoryFreshness(Freshness.STALE);
        RecordingAggregator aggregator = RecordingAggregator.returning(SUCCEEDED);

        assertThat(service(aggregator, freshness).refresh(MAD_JFK)).contains(SUCCEEDED);

        assertThat(aggregator.invocations()).isEqualTo(1);
        assertThat(freshness.calls()).containsExactly("tryLock", "markFresh", "release");
        assertThat(freshness.status(MAD_JFK)).isEqualTo(Freshness.FRESH);
        assertThat(freshness.lastResult(MAD_JFK)).contains(SUCCEEDED);
        assertThat(freshness.markedTtl()).isEqualTo(FRESHNESS_TTL);
        assertThat(freshness.locked()).isFalse();
    }

    @Test
    void should_aggregate_nothing_and_return_empty_when_lock_is_held_elsewhere() {
        InMemoryFreshness freshness = new InMemoryFreshness(Freshness.REFRESHING).lockHeldElsewhere();
        RecordingAggregator aggregator = RecordingAggregator.returning(SUCCEEDED);

        assertThat(service(aggregator, freshness).refresh(MAD_JFK)).isEmpty();

        assertThat(aggregator.invocations()).isZero();
        assertThat(freshness.calls()).containsExactly("tryLock");
    }

    @Test
    void should_return_result_without_marking_fresh_when_every_provider_failed() {
        InMemoryFreshness freshness = new InMemoryFreshness(Freshness.STALE);

        assertThat(service(RecordingAggregator.returning(ALL_FAILED), freshness).refresh(MAD_JFK)).contains(ALL_FAILED);

        assertThat(freshness.calls()).containsExactly("tryLock", "release");
        assertThat(freshness.status(MAD_JFK)).isEqualTo(Freshness.STALE);
        assertThat(freshness.locked()).isFalse();
    }

    @Test
    void should_release_lock_and_propagate_when_aggregation_throws() {
        InMemoryFreshness freshness = new InMemoryFreshness(Freshness.STALE);
        RefreshItinerariesService service =
                service(RecordingAggregator.failing(new IllegalStateException("boom")), freshness);

        assertThatThrownBy(() -> service.refresh(MAD_JFK)).isInstanceOf(IllegalStateException.class);

        assertThat(freshness.calls()).containsExactly("tryLock", "release");
        assertThat(freshness.locked()).isFalse();
    }

    @Test
    void should_reject_construction_when_a_ttl_is_not_positive() {
        assertThatThrownBy(() -> new RefreshItinerariesService(RecordingAggregator.returning(SUCCEEDED),
                new InMemoryFreshness(Freshness.MISSING), Duration.ZERO, LOCK_TTL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RefreshItinerariesService service(RecordingAggregator aggregator, InMemoryFreshness freshness) {
        return new RefreshItinerariesService(aggregator, freshness, FRESHNESS_TTL, LOCK_TTL);
    }

    private static AggregationResult result(ProviderStatus status) {
        return new AggregationResult(status == ProviderStatus.OK ? 1 : 0,
                Map.of(ALPHA, new ProviderOutcome(status, 1, 0, Duration.ofMillis(120))),
                Instant.parse("2026-11-14T10:00:00Z"));
    }
}
