package io.github.jerojasol.flights.application.fakes;

import io.github.jerojasol.flights.domain.model.AggregationResult;
import io.github.jerojasol.flights.domain.model.SearchCriteria;
import io.github.jerojasol.flights.domain.port.in.AggregateFlightsUseCase;

import java.util.concurrent.atomic.AtomicInteger;

public final class RecordingAggregator implements AggregateFlightsUseCase {

    private final AggregationResult result;
    private final RuntimeException failure;
    private final Runnable sideEffect;
    private final AtomicInteger invocations = new AtomicInteger();

    private RecordingAggregator(AggregationResult result, RuntimeException failure, Runnable sideEffect) {
        this.result = result;
        this.failure = failure;
        this.sideEffect = sideEffect;
    }

    public static RecordingAggregator returning(AggregationResult result) {
        return new RecordingAggregator(result, null, () -> { });
    }

    public static RecordingAggregator failing(RuntimeException failure) {
        return new RecordingAggregator(null, failure, () -> { });
    }

    /** Runs {@code sideEffect} on each call before answering, e.g. to seed the repository. */
    public RecordingAggregator andThen(Runnable sideEffect) {
        return new RecordingAggregator(result, failure, sideEffect);
    }

    public int invocations() {
        return invocations.get();
    }

    @Override
    public AggregationResult aggregate(SearchCriteria criteria) {
        invocations.incrementAndGet();
        sideEffect.run();
        if (failure != null) {
            throw failure;
        }
        return result;
    }
}
