package io.github.jerojasol.flights.domain.aggregation;

import io.github.jerojasol.flights.domain.exception.InvalidAggregationException;
import java.time.Duration;

public record ProviderOutcome(ProviderStatus status, int offersReceived, int offersRejected, Duration elapsed) {

    public ProviderOutcome {
        if (status == null || elapsed == null || elapsed.isNegative()) {
            throw new InvalidAggregationException("Outcome requires a status and a non-negative elapsed time");
        }
        if (offersReceived < 0 || offersRejected < 0) {
            throw new InvalidAggregationException("Offer counts must not be negative");
        }
    }

    public boolean succeeded() {
        return status == ProviderStatus.OK;
    }
}
