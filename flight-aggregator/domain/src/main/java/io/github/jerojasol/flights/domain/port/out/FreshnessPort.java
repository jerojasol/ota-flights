package io.github.jerojasol.flights.domain.port.out;

import io.github.jerojasol.flights.domain.aggregation.AggregationResult;
import io.github.jerojasol.flights.domain.search.Freshness;
import io.github.jerojasol.flights.domain.search.LockToken;
import io.github.jerojasol.flights.domain.search.SearchCriteria;

import java.time.Duration;
import java.util.Optional;

public interface FreshnessPort {

    Freshness status(SearchCriteria criteria);

    /** @return the caller's token when it now holds the aggregation lock; empty when another caller holds it */
    Optional<LockToken> tryLock(SearchCriteria criteria, Duration ttl);

    /** Releases the lock only if {@code token} is still its holder; otherwise (expired or re-acquired) a no-op. */
    void release(SearchCriteria criteria, LockToken token);

    void markFresh(SearchCriteria criteria, AggregationResult result, Duration ttl);

    Optional<AggregationResult> lastResult(SearchCriteria criteria);
}
