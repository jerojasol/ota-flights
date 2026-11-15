package io.github.jerojasol.flights.domain.search;

import io.github.jerojasol.flights.domain.exception.InvalidSearchException;
import io.github.jerojasol.flights.domain.model.Itinerary;
import java.util.List;
import java.util.Optional;

public record ItineraryPage(List<Itinerary> items, Optional<PageCursor> next) {

    public ItineraryPage {
        if (items == null || next == null) {
            throw new InvalidSearchException("Page requires items and an Optional next cursor");
        }
        items = List.copyOf(items);
    }

    public static ItineraryPage empty() {
        return new ItineraryPage(List.of(), Optional.empty());
    }
}
