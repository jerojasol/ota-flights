package io.github.jerojasol.flights.domain.search;

import io.github.jerojasol.flights.domain.exception.InvalidSearchException;
import io.github.jerojasol.flights.domain.model.ItineraryKey;
import io.github.jerojasol.flights.domain.model.Price;

/**
 * Keyset position: the Final Price and the Itinerary Key of the last item of the previous page. Pages are ordered
 * by (final price, key), and the key is unique, so the position is unambiguous. Opaque to callers; turning it into
 * a string is a web-adapter concern.
 */
public record PageCursor(Price lastFinalPrice, ItineraryKey lastKey) {

    public PageCursor {
        if (lastFinalPrice == null || lastKey == null) {
            throw new InvalidSearchException("Cursor requires the last final price and the last itinerary key");
        }
    }
}
