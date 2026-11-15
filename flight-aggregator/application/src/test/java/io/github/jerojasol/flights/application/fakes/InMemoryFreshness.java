package io.github.jerojasol.flights.application.fakes;

import io.github.jerojasol.flights.domain.aggregation.AggregationResult;
import io.github.jerojasol.flights.domain.port.out.FreshnessPort;
import io.github.jerojasol.flights.domain.search.Freshness;
import io.github.jerojasol.flights.domain.search.LockToken;
import io.github.jerojasol.flights.domain.search.SearchCriteria;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Single-criteria freshness store: the test sets the status and whether the lock is free, and reads the calls. */
public final class InMemoryFreshness implements FreshnessPort {

    private final List<String> calls = new ArrayList<>();
    private Freshness status;
    private boolean lockAvailable = true;
    private LockToken holder;
    private int issued;
    private AggregationResult lastResult;
    private Duration markedTtl;
    private RuntimeException markFreshFailure;
    private int lastResultCalls;

    public InMemoryFreshness(Freshness status) {
        this.status = status;
    }

    public InMemoryFreshness lockHeldElsewhere() {
        this.lockAvailable = false;
        return this;
    }

    public InMemoryFreshness withLastResult(AggregationResult result) {
        this.lastResult = result;
        return this;
    }

    /** Simulates the freshness store failing on write (e.g. Redis down) after a successful aggregation. */
    public InMemoryFreshness failingMarkFreshWith(RuntimeException failure) {
        this.markFreshFailure = failure;
        return this;
    }

    public synchronized int lastResultCalls() {
        return lastResultCalls;
    }

    public synchronized List<String> calls() {
        return List.copyOf(calls);
    }

    public synchronized boolean locked() {
        return holder != null;
    }

    public synchronized Optional<LockToken> holder() {
        return Optional.ofNullable(holder);
    }

    /** Simulates the lock TTL running out while its holder is still working. */
    public synchronized void expireLock() {
        holder = null;
    }

    public synchronized Duration markedTtl() {
        return markedTtl;
    }

    @Override
    public synchronized Freshness status(SearchCriteria criteria) {
        calls.add("status");
        return status;
    }

    @Override
    public synchronized Optional<LockToken> tryLock(SearchCriteria criteria, Duration ttl) {
        calls.add("tryLock");
        if (!lockAvailable || holder != null) {
            return Optional.empty();
        }
        holder = new LockToken("token-" + (++issued));
        return Optional.of(holder);
    }

    @Override
    public synchronized void release(SearchCriteria criteria, LockToken token) {
        calls.add("release");
        if (token.equals(holder)) {
            holder = null;
        }
    }

    @Override
    public synchronized void markFresh(SearchCriteria criteria, AggregationResult result, Duration ttl) {
        calls.add("markFresh");
        if (markFreshFailure != null) {
            throw markFreshFailure;
        }
        status = Freshness.FRESH;
        lastResult = result;
        markedTtl = ttl;
    }

    @Override
    public synchronized Optional<AggregationResult> lastResult(SearchCriteria criteria) {
        lastResultCalls++;
        return Optional.ofNullable(lastResult);
    }
}
