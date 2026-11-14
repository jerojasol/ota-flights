package io.github.jerojasol.flights.application;

import io.github.jerojasol.flights.application.fakes.CapturingExecutor;
import io.github.jerojasol.flights.application.fakes.InMemoryFreshness;
import io.github.jerojasol.flights.application.fakes.InMemoryItineraryRepository;
import io.github.jerojasol.flights.application.fakes.RecordingAggregator;
import io.github.jerojasol.flights.domain.model.AggregationResult;
import io.github.jerojasol.flights.domain.model.Freshness;
import io.github.jerojasol.flights.domain.model.Itinerary;
import io.github.jerojasol.flights.domain.model.ItineraryFactory;
import io.github.jerojasol.flights.domain.model.ItineraryFilter;
import io.github.jerojasol.flights.domain.model.ItineraryKey;
import io.github.jerojasol.flights.domain.model.LockToken;
import io.github.jerojasol.flights.domain.model.PageCursor;
import io.github.jerojasol.flights.domain.model.PercentageMarkupPolicy;
import io.github.jerojasol.flights.domain.model.Price;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.ProviderOutcome;
import io.github.jerojasol.flights.domain.model.ProviderStatus;
import io.github.jerojasol.flights.domain.model.SearchResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.jerojasol.flights.application.Fixtures.MAD_JFK;
import static io.github.jerojasol.flights.application.Fixtures.direct;
import static io.github.jerojasol.flights.domain.model.Provider.ALPHA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchItinerariesServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-11-14T09:00:00Z");
    private static final Duration FRESHNESS_TTL = Duration.ofMinutes(10);
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final ItineraryFactory FACTORY = new ItineraryFactory(
            new PercentageMarkupPolicy(new BigDecimal("5")), Clock.fixed(NOW, ZoneOffset.UTC));
    private static final Itinerary STORED = FACTORY.create(direct(ALPHA, "IB6251", "500.00"));
    private static final Itinerary NEW_ONE = FACTORY.create(direct(ALPHA, "IB6253", "600.00"));
    private static final AggregationResult SUCCEEDED = result(NOW, ProviderStatus.OK);
    private static final AggregationResult PREVIOUS = result(EARLIER, ProviderStatus.OK);

    private final InMemoryItineraryRepository repository = new InMemoryItineraryRepository();
    private final CapturingExecutor executor = new CapturingExecutor();

    @Test
    void should_serve_from_repository_without_aggregating_when_data_is_fresh() {
        repository.upsertAll(List.of(STORED));
        InMemoryFreshness freshness = new InMemoryFreshness(Freshness.FRESH).withLastResult(PREVIOUS);
        RecordingAggregator aggregator = RecordingAggregator.returning(SUCCEEDED);

        SearchResult result = service(freshness, aggregator, executor).search(MAD_JFK, ItineraryFilter.none(), Optional.empty(), 20);

        assertThat(result.freshness()).isEqualTo(Freshness.FRESH);
        assertThat(result.page().items()).containsExactly(STORED);
        assertThat(result.aggregatedAt()).contains(EARLIER);
        assertThat(result.lastOutcomes()).isEqualTo(PREVIOUS.outcomes());
        assertThat(aggregator.invocations()).isZero();
        assertThat(freshness.calls()).containsExactly("status");
        assertThat(executor.pending()).isZero();
    }

    @Test
    void should_aggregate_before_querying_and_mark_fresh_when_cold_miss_acquires_lock() {
        InMemoryFreshness freshness = new InMemoryFreshness(Freshness.MISSING);
        RecordingAggregator aggregator = RecordingAggregator.returning(SUCCEEDED)
                .andThen(() -> {
                    assertThat(repository.findByCalls()).isZero();
                    repository.upsertAll(List.of(NEW_ONE));
                });

        SearchResult result = service(freshness, aggregator, executor).search(MAD_JFK, ItineraryFilter.none(), Optional.empty(), 20);

        assertThat(aggregator.invocations()).isEqualTo(1);
        assertThat(result.page().items()).containsExactly(NEW_ONE);
        assertThat(result.freshness()).isEqualTo(Freshness.FRESH);
        assertThat(result.aggregatedAt()).contains(NOW);
        assertThat(result.lastOutcomes()).isEqualTo(SUCCEEDED.outcomes());
        assertThat(freshness.calls()).containsExactly("status", "tryLock", "markFresh", "release");
        assertThat(freshness.markedTtl()).isEqualTo(FRESHNESS_TTL);
        assertThat(freshness.locked()).isFalse();
    }

    @Test
    void should_report_outcomes_of_aggregation_just_performed_when_no_last_result_is_stored() {
        AggregationResult allFailed = result(NOW, ProviderStatus.ERROR);
        InMemoryFreshness freshness = new InMemoryFreshness(Freshness.MISSING);

        SearchResult result = service(freshness, RecordingAggregator.returning(allFailed), executor)
                .search(MAD_JFK, ItineraryFilter.none(), Optional.empty(), 20);

        assertThat(result.freshness()).isEqualTo(Freshness.MISSING);
        assertThat(result.aggregatedAt()).contains(NOW);
        assertThat(result.lastOutcomes()).isEqualTo(allFailed.outcomes());
        assertThat(freshness.calls()).containsExactly("status", "tryLock", "release");
    }

    @Test
    void should_keep_new_holders_lock_when_lock_expired_during_aggregation_and_was_reacquired() {
        InMemoryFreshness freshness = new InMemoryFreshness(Freshness.MISSING);
        AtomicReference<Optional<LockToken>> secondCaller = new AtomicReference<>(Optional.empty());
        RecordingAggregator aggregator = RecordingAggregator.returning(SUCCEEDED).andThen(() -> {
            freshness.expireLock();
            secondCaller.set(freshness.tryLock(MAD_JFK, LOCK_TTL));
        });

        service(freshness, aggregator, executor).search(MAD_JFK, ItineraryFilter.none(), Optional.empty(), 20);

        assertThat(secondCaller.get()).isPresent();
        assertThat(freshness.holder()).isEqualTo(secondCaller.get());
        assertThat(freshness.calls()).containsExactly("status", "tryLock", "tryLock", "markFresh", "release");
    }

    @Test
    void should_report_refreshing_without_aggregating_when_cold_miss_cannot_acquire_lock() {
        InMemoryFreshness freshness = new InMemoryFreshness(Freshness.MISSING).lockHeldElsewhere();
        RecordingAggregator aggregator = RecordingAggregator.returning(SUCCEEDED);

        SearchResult result = service(freshness, aggregator, executor).search(MAD_JFK, ItineraryFilter.none(), Optional.empty(), 20);

        assertThat(result.freshness()).isEqualTo(Freshness.REFRESHING);
        assertThat(result.page().items()).isEmpty();
        assertThat(result.aggregatedAt()).isEmpty();
        assertThat(result.lastOutcomes()).isEmpty();
        assertThat(aggregator.invocations()).isZero();
        assertThat(repository.findByCalls()).isEqualTo(1);
    }

    @Test
    void should_serve_stale_data_first_and_refresh_in_background_when_stale_acquires_lock() {
        repository.upsertAll(List.of(STORED));
        InMemoryFreshness freshness = new InMemoryFreshness(Freshness.STALE).withLastResult(PREVIOUS);
        RecordingAggregator aggregator = RecordingAggregator.returning(SUCCEEDED);
        SearchItinerariesService service = service(freshness, aggregator, executor);

        SearchResult result = service.search(MAD_JFK, ItineraryFilter.none(), Optional.empty(), 20);

        assertThat(result.freshness()).isEqualTo(Freshness.STALE);
        assertThat(result.page().items()).containsExactly(STORED);
        assertThat(result.aggregatedAt()).contains(EARLIER);
        assertThat(repository.findByCalls()).isEqualTo(1);
        assertThat(executor.pending()).isEqualTo(1);
        assertThat(aggregator.invocations()).isZero();
        assertThat(freshness.locked()).isTrue();

        executor.runAll();

        assertThat(aggregator.invocations()).isEqualTo(1);
        assertThat(freshness.calls()).containsExactly("status", "tryLock", "markFresh", "release");
        assertThat(freshness.locked()).isFalse();
        assertThat(freshness.lastResult(MAD_JFK)).contains(SUCCEEDED);
    }

    @Test
    void should_serve_stale_data_without_scheduling_when_stale_cannot_acquire_lock() {
        InMemoryFreshness freshness = new InMemoryFreshness(Freshness.STALE).lockHeldElsewhere();
        RecordingAggregator aggregator = RecordingAggregator.returning(SUCCEEDED);

        SearchResult result = service(freshness, aggregator, executor).search(MAD_JFK, ItineraryFilter.none(), Optional.empty(), 20);

        assertThat(result.freshness()).isEqualTo(Freshness.STALE);
        assertThat(executor.pending()).isZero();
        assertThat(aggregator.invocations()).isZero();
        assertThat(freshness.calls()).containsExactly("status", "tryLock");
    }

    @Test
    void should_serve_without_aggregating_when_refresh_is_already_running() {
        InMemoryFreshness freshness = new InMemoryFreshness(Freshness.REFRESHING);
        RecordingAggregator aggregator = RecordingAggregator.returning(SUCCEEDED);

        SearchResult result = service(freshness, aggregator, executor).search(MAD_JFK, ItineraryFilter.none(), Optional.empty(), 20);

        assertThat(result.freshness()).isEqualTo(Freshness.REFRESHING);
        assertThat(aggregator.invocations()).isZero();
        assertThat(freshness.calls()).containsExactly("status");
    }

    @Test
    void should_never_aggregate_when_cursor_is_present_even_if_data_is_missing() {
        repository.upsertAll(List.of(STORED, NEW_ONE));
        InMemoryFreshness freshness = new InMemoryFreshness(Freshness.MISSING);
        RecordingAggregator aggregator = RecordingAggregator.returning(SUCCEEDED);
        PageCursor afterStored = new PageCursor(STORED.finalPrice(), STORED.key());

        SearchResult result = service(freshness, aggregator, executor)
                .search(MAD_JFK, ItineraryFilter.none(), Optional.of(afterStored), 20);

        assertThat(result.freshness()).isEqualTo(Freshness.MISSING);
        assertThat(result.page().items()).containsExactly(NEW_ONE);
        assertThat(aggregator.invocations()).isZero();
        assertThat(executor.pending()).isZero();
        assertThat(freshness.calls()).containsExactly("status");
    }

    @Test
    void should_never_schedule_refresh_when_cursor_is_present_and_data_is_stale() {
        InMemoryFreshness freshness = new InMemoryFreshness(Freshness.STALE);
        PageCursor cursor = new PageCursor(Price.of("1.00", "EUR"), ItineraryKey.of(STORED.segments()));

        SearchResult result = service(freshness, RecordingAggregator.returning(SUCCEEDED), executor)
                .search(MAD_JFK, ItineraryFilter.none(), Optional.of(cursor), 20);

        assertThat(result.freshness()).isEqualTo(Freshness.STALE);
        assertThat(executor.pending()).isZero();
    }

    @Test
    void should_release_lock_and_still_return_results_when_synchronous_aggregation_throws() {
        repository.upsertAll(List.of(STORED));
        InMemoryFreshness freshness = new InMemoryFreshness(Freshness.MISSING);
        RecordingAggregator aggregator = RecordingAggregator.failing(new IllegalStateException("boom"));

        SearchResult result = service(freshness, aggregator, executor).search(MAD_JFK, ItineraryFilter.none(), Optional.empty(), 20);

        assertThat(result.freshness()).isEqualTo(Freshness.MISSING);
        assertThat(result.page().items()).containsExactly(STORED);
        assertThat(result.aggregatedAt()).isEmpty();
        assertThat(freshness.calls()).containsExactly("status", "tryLock", "release");
        assertThat(freshness.locked()).isFalse();
    }

    @Test
    void should_release_lock_before_reading_repository_when_synchronous_aggregation_throws() {
        repository.upsertAll(List.of(STORED));
        InMemoryFreshness freshness = new InMemoryFreshness(Freshness.MISSING).withLastResult(PREVIOUS);
        repository.onFindBy(() -> assertThat(freshness.locked()).as("lock held during repository read").isFalse());
        RecordingAggregator aggregator = RecordingAggregator.failing(new IllegalStateException("boom"));

        SearchResult result = service(freshness, aggregator, executor).search(MAD_JFK, ItineraryFilter.none(), Optional.empty(), 20);

        assertThat(result.freshness()).isEqualTo(Freshness.MISSING);
        assertThat(result.page().items()).containsExactly(STORED);
        assertThat(result.lastOutcomes()).isEqualTo(PREVIOUS.outcomes());
        assertThat(repository.findByCalls()).isEqualTo(1);
    }

    @Test
    void should_report_just_aggregated_outcomes_as_missing_when_mark_fresh_fails() {
        InMemoryFreshness freshness = new InMemoryFreshness(Freshness.MISSING).withLastResult(PREVIOUS)
                .failingMarkFreshWith(new IllegalStateException("store down"));
        repository.onFindBy(() -> assertThat(freshness.locked()).as("lock held during repository read").isFalse());
        RecordingAggregator aggregator = RecordingAggregator.returning(SUCCEEDED);

        SearchResult result = service(freshness, aggregator, executor).search(MAD_JFK, ItineraryFilter.none(), Optional.empty(), 20);

        assertThat(result.freshness()).isEqualTo(Freshness.MISSING);
        assertThat(result.aggregatedAt()).contains(NOW);
        assertThat(result.lastOutcomes()).isEqualTo(SUCCEEDED.outcomes());
        assertThat(freshness.lastResultCalls()).isZero();
        assertThat(freshness.calls()).containsExactly("status", "tryLock", "markFresh", "release");
        assertThat(freshness.locked()).isFalse();
    }

    @Test
    void should_release_lock_when_background_refresh_throws() {
        InMemoryFreshness freshness = new InMemoryFreshness(Freshness.STALE);
        RecordingAggregator aggregator = RecordingAggregator.failing(new IllegalStateException("boom"));
        service(freshness, aggregator, executor).search(MAD_JFK, ItineraryFilter.none(), Optional.empty(), 20);

        assertThatThrownBy(executor::runAll).isInstanceOf(IllegalStateException.class);

        assertThat(aggregator.invocations()).isEqualTo(1);
        assertThat(freshness.calls()).containsExactly("status", "tryLock", "release");
        assertThat(freshness.locked()).isFalse();
    }

    @Test
    void should_not_mark_fresh_when_background_refresh_has_no_successful_provider() {
        InMemoryFreshness freshness = new InMemoryFreshness(Freshness.STALE);
        service(freshness, RecordingAggregator.returning(result(NOW, ProviderStatus.TIMEOUT)), executor)
                .search(MAD_JFK, ItineraryFilter.none(), Optional.empty(), 20);

        executor.runAll();

        assertThat(freshness.calls()).containsExactly("status", "tryLock", "release");
        assertThat(freshness.locked()).isFalse();
    }

    @Test
    void should_release_lock_and_propagate_when_aggregator_throws_error() {
        InMemoryFreshness freshness = new InMemoryFreshness(Freshness.MISSING);
        RecordingAggregator aggregator = RecordingAggregator.returning(SUCCEEDED).andThen(() -> {
            throw new StackOverflowError("boom");
        });

        assertThatThrownBy(() -> service(freshness, aggregator, executor)
                .search(MAD_JFK, ItineraryFilter.none(), Optional.empty(), 20))
                .isInstanceOf(StackOverflowError.class);
        assertThat(freshness.locked()).isFalse();
    }

    @Test
    void should_report_outcomes_just_aggregated_when_an_older_result_is_still_stored() {
        AggregationResult allFailed = result(NOW, ProviderStatus.ERROR);
        InMemoryFreshness freshness = new InMemoryFreshness(Freshness.MISSING).withLastResult(PREVIOUS);

        SearchResult result = service(freshness, RecordingAggregator.returning(allFailed), executor)
                .search(MAD_JFK, ItineraryFilter.none(), Optional.empty(), 20);

        assertThat(result.aggregatedAt()).contains(NOW);
        assertThat(result.lastOutcomes()).isEqualTo(allFailed.outcomes());
    }

    @Test
    void should_release_lock_when_refresh_executor_rejects_the_task() {
        InMemoryFreshness freshness = new InMemoryFreshness(Freshness.STALE);
        Executor rejecting = task -> {
            throw new RejectedExecutionException("full");
        };

        SearchResult result = service(freshness, RecordingAggregator.returning(SUCCEEDED), rejecting)
                .search(MAD_JFK, ItineraryFilter.none(), Optional.empty(), 20);

        assertThat(result.freshness()).isEqualTo(Freshness.STALE);
        assertThat(freshness.calls()).containsExactly("status", "tryLock", "release");
        assertThat(freshness.locked()).isFalse();
    }

    @Test
    void should_clamp_limit_to_one_when_limit_is_zero() {
        service(new InMemoryFreshness(Freshness.FRESH), RecordingAggregator.returning(SUCCEEDED), executor)
                .search(MAD_JFK, ItineraryFilter.none(), Optional.empty(), 0);

        assertThat(repository.lastLimit()).isEqualTo(1);
    }

    @Test
    void should_clamp_limit_to_one_hundred_when_limit_is_five_hundred() {
        service(new InMemoryFreshness(Freshness.FRESH), RecordingAggregator.returning(SUCCEEDED), executor)
                .search(MAD_JFK, ItineraryFilter.none(), Optional.empty(), 500);

        assertThat(repository.lastLimit()).isEqualTo(100);
    }

    @Test
    void should_clamp_limit_to_one_when_limit_is_negative() {
        service(new InMemoryFreshness(Freshness.FRESH), RecordingAggregator.returning(SUCCEEDED), executor)
                .search(MAD_JFK, ItineraryFilter.none(), Optional.empty(), -5);

        assertThat(repository.lastLimit()).isEqualTo(1);
    }

    @Test
    void should_pass_limit_through_when_within_bounds() {
        service(new InMemoryFreshness(Freshness.FRESH), RecordingAggregator.returning(SUCCEEDED), executor)
                .search(MAD_JFK, ItineraryFilter.none(), Optional.empty(), 37);

        assertThat(repository.lastLimit()).isEqualTo(37);
    }

    private SearchItinerariesService service(InMemoryFreshness freshness, RecordingAggregator aggregator,
                                             Executor refreshExecutor) {
        return new SearchItinerariesService(repository, freshness, aggregator, refreshExecutor, FRESHNESS_TTL, LOCK_TTL);
    }

    private static AggregationResult result(Instant at, ProviderStatus alphaStatus) {
        Map<Provider, ProviderOutcome> outcomes = Map.of(
                ALPHA, new ProviderOutcome(alphaStatus, 1, 0, Duration.ofMillis(120)));
        return new AggregationResult(alphaStatus == ProviderStatus.OK ? 1 : 0, outcomes, at);
    }
}
