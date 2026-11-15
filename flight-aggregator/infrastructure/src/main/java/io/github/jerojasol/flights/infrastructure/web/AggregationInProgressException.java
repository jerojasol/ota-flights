package io.github.jerojasol.flights.infrastructure.web;

/** A refresh was requested but the aggregation lock could not be taken. Mapped to 409. */
public final class AggregationInProgressException extends RuntimeException {

    public AggregationInProgressException() {
        super("An aggregation for this route and date is already running, or the lock service is unavailable");
    }
}
