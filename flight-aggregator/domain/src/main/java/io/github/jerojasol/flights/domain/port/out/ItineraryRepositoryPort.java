package io.github.jerojasol.flights.domain.port.out;

import io.github.jerojasol.flights.domain.model.Itinerary;
import io.github.jerojasol.flights.domain.model.ItineraryFilter;
import io.github.jerojasol.flights.domain.model.ItineraryPage;
import io.github.jerojasol.flights.domain.model.PageCursor;
import io.github.jerojasol.flights.domain.model.SearchCriteria;

import java.util.Collection;
import java.util.Optional;

public interface ItineraryRepositoryPort {

    /** Inserts new keys; on a key conflict the lower Final Price wins. Returns the number of rows written. */
    int upsertAll(Collection<Itinerary> itineraries);

    ItineraryPage findBy(SearchCriteria criteria, ItineraryFilter filter, Optional<PageCursor> cursor, int limit);
}
