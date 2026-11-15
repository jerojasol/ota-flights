package io.github.jerojasol.flights.domain.search;

import io.github.jerojasol.flights.domain.aggregation.ProviderOutcome;
import io.github.jerojasol.flights.domain.exception.InvalidSearchException;
import io.github.jerojasol.flights.domain.model.Provider;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public record SearchResult(
        ItineraryPage page,
        Freshness freshness,
        Optional<Instant> aggregatedAt,
        Map<Provider, ProviderOutcome> lastOutcomes) {

    public SearchResult {
        if (page == null || freshness == null || aggregatedAt == null || lastOutcomes == null) {
            throw new InvalidSearchException("Search result components are required");
        }
        Map<Provider, ProviderOutcome> copy = new EnumMap<>(Provider.class);
        copy.putAll(lastOutcomes);
        lastOutcomes = Collections.unmodifiableMap(copy);
    }
}
