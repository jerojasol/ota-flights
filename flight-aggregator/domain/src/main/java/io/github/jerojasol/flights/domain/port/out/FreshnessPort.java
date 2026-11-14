package io.github.jerojasol.flights.domain.port.out;

import io.github.jerojasol.flights.domain.model.AggregationResult;
import io.github.jerojasol.flights.domain.model.Freshness;
import io.github.jerojasol.flights.domain.model.SearchCriteria;

import java.time.Duration;
import java.util.Optional;

public interface FreshnessPort {

    Freshness status(SearchCriteria criteria);

    /** @return true when the caller now holds the aggregation lock for these criteria */
    boolean tryLock(SearchCriteria criteria, Duration ttl);

    void release(SearchCriteria criteria);

    void markFresh(SearchCriteria criteria, AggregationResult result, Duration ttl);

    Optional<AggregationResult> lastResult(SearchCriteria criteria);
}
