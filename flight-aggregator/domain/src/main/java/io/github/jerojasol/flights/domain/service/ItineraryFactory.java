package io.github.jerojasol.flights.domain.service;

import io.github.jerojasol.flights.domain.exception.InvalidItineraryException;
import io.github.jerojasol.flights.domain.model.Itinerary;
import io.github.jerojasol.flights.domain.model.MarkupPolicy;
import io.github.jerojasol.flights.domain.model.Offer;
import java.time.Clock;

/** Domain service: builds an Itinerary from the winning Offer applying the configured MarkupPolicy. */
public final class ItineraryFactory {

    private final MarkupPolicy markup;
    private final Clock clock;

    public ItineraryFactory(MarkupPolicy markup, Clock clock) {
        if (markup == null || clock == null) {
            throw new InvalidItineraryException("Markup policy and clock are required");
        }
        this.markup = markup;
        this.clock = clock;
    }

    public ItineraryFactory(MarkupPolicy markup) {
        this(markup, Clock.systemUTC());
    }

    public Itinerary create(Offer winning) {
        return Itinerary.from(winning, markup, clock.instant());
    }
}
