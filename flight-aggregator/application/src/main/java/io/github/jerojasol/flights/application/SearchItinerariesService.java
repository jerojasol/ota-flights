package io.github.jerojasol.flights.application;

import io.github.jerojasol.flights.domain.model.AggregationResult;
import io.github.jerojasol.flights.domain.model.Freshness;
import io.github.jerojasol.flights.domain.model.ItineraryFilter;
import io.github.jerojasol.flights.domain.model.ItineraryPage;
import io.github.jerojasol.flights.domain.model.LockToken;
import io.github.jerojasol.flights.domain.model.PageCursor;
import io.github.jerojasol.flights.domain.model.SearchCriteria;
import io.github.jerojasol.flights.domain.model.SearchResult;
import io.github.jerojasol.flights.domain.port.in.AggregateFlightsUseCase;
import io.github.jerojasol.flights.domain.port.in.SearchItinerariesUseCase;
import io.github.jerojasol.flights.domain.port.out.FreshnessPort;
import io.github.jerojasol.flights.domain.port.out.ItineraryRepositoryPort;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Read path (domain-model.md §8 "Freshness"): serve from the repository, aggregate synchronously on a cold miss,
 * refresh in the background when stale. A search never fails because providers failed, and the aggregation lock
 * is always released.
 */
public final class SearchItinerariesService implements SearchItinerariesUseCase {

    static final int MIN_LIMIT = 1;
    static final int MAX_LIMIT = 100;

    private final ItineraryRepositoryPort repository;
    private final FreshnessPort freshness;
    private final AggregateFlightsUseCase aggregator;
    private final Executor refreshExecutor;
    private final Duration freshnessTtl;
    private final Duration lockTtl;

    public SearchItinerariesService(ItineraryRepositoryPort repository,
                                    FreshnessPort freshness,
                                    AggregateFlightsUseCase aggregator,
                                    Executor refreshExecutor,
                                    Duration freshnessTtl,
                                    Duration lockTtl) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.freshness = Objects.requireNonNull(freshness, "freshness");
        this.aggregator = Objects.requireNonNull(aggregator, "aggregator");
        this.refreshExecutor = Objects.requireNonNull(refreshExecutor, "refreshExecutor");
        this.freshnessTtl = requirePositive(freshnessTtl, "freshnessTtl");
        this.lockTtl = requirePositive(lockTtl, "lockTtl");
    }

    @Override
    public SearchResult search(SearchCriteria criteria, ItineraryFilter filter, Optional<PageCursor> cursor,
                               int limit) {
        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(cursor, "cursor");
        int clampedLimit = Math.clamp(limit, MIN_LIMIT, MAX_LIMIT);
        Freshness status = freshness.status(criteria);

        // A cursor request never aggregates: later pages must stay consistent with page 1.
        if (cursor.isPresent()) {
            return result(criteria, filter, cursor, clampedLimit, status, Optional.empty());
        }
        return switch (status) {
            case FRESH, REFRESHING -> result(criteria, filter, cursor, clampedLimit, status, Optional.empty());
            case MISSING -> coldMiss(criteria, filter, clampedLimit);
            case STALE -> stale(criteria, filter, clampedLimit);
        };
    }

    private SearchResult coldMiss(SearchCriteria criteria, ItineraryFilter filter, int limit) {
        Optional<LockToken> lock = freshness.tryLock(criteria, lockTtl);
        if (lock.isEmpty()) {
            return result(criteria, filter, Optional.empty(), limit, Freshness.REFRESHING, Optional.empty());
        }
        Optional<AggregationResult> aggregated = Optional.empty();
        Freshness reported = Freshness.MISSING;
        try {
            AggregationResult result = aggregator.aggregate(criteria);
            aggregated = Optional.of(result);
            // An aggregation where every provider failed is not cached as fresh: the next search retries.
            if (result.anySucceeded()) {
                freshness.markFresh(criteria, result, freshnessTtl);
                reported = Freshness.FRESH;
            }
        } catch (RuntimeException aggregationFailed) {
            // Serve whatever the repository has; providers failing never fails a search.
        } finally {
            freshness.release(criteria, lock.get());
        }
        return result(criteria, filter, Optional.empty(), limit, reported, aggregated);
    }

    private SearchResult stale(SearchCriteria criteria, ItineraryFilter filter, int limit) {
        SearchResult result = result(criteria, filter, Optional.empty(), limit, Freshness.STALE, Optional.empty());
        freshness.tryLock(criteria, lockTtl).ifPresent(lock -> {
            try {
                refreshExecutor.execute(() -> refresh(criteria, lock));
            } catch (RuntimeException notScheduled) {
                freshness.release(criteria, lock);
            }
        });
        return result;
    }

    private void refresh(SearchCriteria criteria, LockToken lock) {
        try {
            AggregationResult result = aggregator.aggregate(criteria);
            if (result.anySucceeded()) {
                freshness.markFresh(criteria, result, freshnessTtl);
            }
        } finally {
            // The lock is released first; the failure then reaches the executor so infrastructure can log it.
            // The data stays STALE and the next search tries again.
            freshness.release(criteria, lock);
        }
    }

    private SearchResult result(SearchCriteria criteria, ItineraryFilter filter, Optional<PageCursor> cursor,
                                int limit, Freshness reported, Optional<AggregationResult> justAggregated) {
        ItineraryPage page = repository.findBy(criteria, filter, cursor, limit);
        Optional<AggregationResult> last = justAggregated.or(() -> freshness.lastResult(criteria));
        return new SearchResult(page, reported,
                last.map(AggregationResult::aggregatedAt),
                last.map(AggregationResult::outcomes).orElse(Map.of()));
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
