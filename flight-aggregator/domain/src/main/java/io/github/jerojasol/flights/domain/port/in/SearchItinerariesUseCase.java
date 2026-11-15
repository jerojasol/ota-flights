package io.github.jerojasol.flights.domain.port.in;

import io.github.jerojasol.flights.domain.search.ItineraryFilter;
import io.github.jerojasol.flights.domain.search.PageCursor;
import io.github.jerojasol.flights.domain.search.SearchCriteria;
import io.github.jerojasol.flights.domain.search.SearchResult;

import java.util.Optional;

public interface SearchItinerariesUseCase {

    SearchResult search(SearchCriteria criteria, ItineraryFilter filter, Optional<PageCursor> cursor, int limit);
}
