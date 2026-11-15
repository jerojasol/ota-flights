package io.github.jerojasol.flights.domain.search;

import io.github.jerojasol.flights.domain.exception.InvalidSearchException;
import io.github.jerojasol.flights.domain.model.Airport;
import io.github.jerojasol.flights.domain.model.Carrier;
import io.github.jerojasol.flights.domain.model.Price;
import java.util.Optional;

/**
 * Optional narrowing of a search. Optional components are the agreed exception to "no Optional fields"
 * (domain-model.md §8): absence is part of the contract and null may not cross module boundaries.
 */
public record ItineraryFilter(
        Optional<Airport> origin,
        Optional<Airport> destination,
        Optional<Price> maxPrice,
        Optional<Carrier> carrier) {

    public ItineraryFilter {
        if (origin == null || destination == null || maxPrice == null || carrier == null) {
            throw new InvalidSearchException("Filter components must be Optional.empty(), not null");
        }
    }

    public static ItineraryFilter none() {
        return new ItineraryFilter(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public ItineraryFilter withOrigin(Airport value) {
        return new ItineraryFilter(Optional.ofNullable(value), destination, maxPrice, carrier);
    }

    public ItineraryFilter withDestination(Airport value) {
        return new ItineraryFilter(origin, Optional.ofNullable(value), maxPrice, carrier);
    }

    public ItineraryFilter withMaxPrice(Price value) {
        return new ItineraryFilter(origin, destination, Optional.ofNullable(value), carrier);
    }

    public ItineraryFilter withCarrier(Carrier value) {
        return new ItineraryFilter(origin, destination, maxPrice, Optional.ofNullable(value));
    }
}
