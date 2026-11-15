package io.github.jerojasol.flights.application;

import io.github.jerojasol.flights.domain.aggregation.AggregationResult;
import io.github.jerojasol.flights.domain.port.in.AggregateFlightsUseCase;
import io.github.jerojasol.flights.domain.port.in.RefreshItinerariesUseCase;
import io.github.jerojasol.flights.domain.port.out.FreshnessPort;
import io.github.jerojasol.flights.domain.search.LockToken;
import io.github.jerojasol.flights.domain.search.SearchCriteria;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * On-demand refresh (domain-model.md §8): aggregate only while holding the aggregation lock, mark fresh only when at
 * least one provider succeeded, and always release the lock. When the lock cannot be taken, nothing runs.
 */
public final class RefreshItinerariesService implements RefreshItinerariesUseCase {

    private final AggregateFlightsUseCase aggregator;
    private final FreshnessPort freshness;
    private final Duration freshnessTtl;
    private final Duration lockTtl;

    public RefreshItinerariesService(AggregateFlightsUseCase aggregator, FreshnessPort freshness,
                                     Duration freshnessTtl, Duration lockTtl) {
        this.aggregator = Objects.requireNonNull(aggregator, "aggregator");
        this.freshness = Objects.requireNonNull(freshness, "freshness");
        this.freshnessTtl = requirePositive(freshnessTtl, "freshnessTtl");
        this.lockTtl = requirePositive(lockTtl, "lockTtl");
    }

    @Override
    public Optional<AggregationResult> refresh(SearchCriteria criteria) {
        Objects.requireNonNull(criteria, "criteria");
        Optional<LockToken> lock = freshness.tryLock(criteria, lockTtl);
        if (lock.isEmpty()) {
            return Optional.empty();
        }
        try {
            AggregationResult result = aggregator.aggregate(criteria);
            // An aggregation where every provider failed is not marked fresh: the next search retries.
            if (result.anySucceeded()) {
                freshness.markFresh(criteria, result, freshnessTtl);
            }
            return Optional.of(result);
        } finally {
            freshness.release(criteria, lock.get());
        }
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
