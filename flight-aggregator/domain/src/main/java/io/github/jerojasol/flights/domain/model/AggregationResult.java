package io.github.jerojasol.flights.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record AggregationResult(int persisted, Map<Provider, ProviderOutcome> outcomes, Instant aggregatedAt) {

    public AggregationResult {
        if (persisted < 0 || outcomes == null || outcomes.isEmpty() || aggregatedAt == null) {
            throw new InvalidAggregationException(
                    "Aggregation result requires a non-negative count, at least one outcome and a time");
        }
        Map<Provider, ProviderOutcome> copy = new EnumMap<>(Provider.class);
        copy.putAll(outcomes);
        outcomes = Collections.unmodifiableMap(copy);
    }

    public boolean anySucceeded() {
        return outcomes.values().stream().anyMatch(ProviderOutcome::succeeded);
    }

    public boolean allFailed() {
        return !anySucceeded();
    }
}
