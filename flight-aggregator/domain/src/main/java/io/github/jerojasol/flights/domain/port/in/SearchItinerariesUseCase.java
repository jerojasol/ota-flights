package io.github.jerojasol.flights.domain.port.in;

import io.github.jerojasol.flights.domain.model.ItineraryFilter;
import io.github.jerojasol.flights.domain.model.PageCursor;
import io.github.jerojasol.flights.domain.model.SearchCriteria;
import io.github.jerojasol.flights.domain.model.SearchResult;

import java.util.Optional;

public interface SearchItinerariesUseCase {

    SearchResult search(SearchCriteria criteria, ItineraryFilter filter, Optional<PageCursor> cursor, int limit);
}
