package io.github.jerojasol.flights.domain.aggregation;

import io.github.jerojasol.flights.domain.exception.InvalidAggregationException;
import io.github.jerojasol.flights.domain.model.Offer;
import java.util.List;

/**
 * What one provider call delivered: the Offers the adapter could build, and how many of the provider's itineraries
 * it could not map because a Segment broke S1–S3 (dead legs). Unmappable itineraries count as received and rejected
 * for that provider (domain-model.md §9).
 */
public record ProviderResponse(List<Offer> offers, int unmappable) {

    public ProviderResponse {
        if (offers == null) {
            throw new InvalidAggregationException("Provider response offers must not be null");
        }
        if (unmappable < 0) {
            throw new InvalidAggregationException("Unmappable count must not be negative: " + unmappable);
        }
        offers = List.copyOf(offers);
    }

    public static ProviderResponse of(List<Offer> offers) {
        return new ProviderResponse(offers, 0);
    }
}
