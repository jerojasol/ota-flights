package io.github.jerojasol.flights.domain.port.out;

import io.github.jerojasol.flights.domain.model.Itinerary;
import io.github.jerojasol.flights.domain.search.ItineraryFilter;
import io.github.jerojasol.flights.domain.search.ItineraryPage;
import io.github.jerojasol.flights.domain.search.PageCursor;
import io.github.jerojasol.flights.domain.search.SearchCriteria;

import java.util.Collection;
import java.util.Optional;

public interface ItineraryRepositoryPort {

    /**
     * Stores what the Providers are offering now. Stores Itineraries whose {@code ItineraryKey} is not known yet; for
     * a key that is already stored the incoming Itinerary replaces it unconditionally, even when its Final Price is
     * higher, so a Provider that raised its price corrects what is stored. Keeping the cheapest Offer within one
     * Aggregation is the DeduplicationPolicy's job and has already happened when this is called.
     *
     * @return the number of Itineraries stored by this call
     */
    int upsertAll(Collection<Itinerary> itineraries);

    ItineraryPage findBy(SearchCriteria criteria, ItineraryFilter filter, Optional<PageCursor> cursor, int limit);
}
