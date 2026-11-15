package io.github.jerojasol.flights.domain.port.in;

import io.github.jerojasol.flights.domain.aggregation.AggregationResult;
import io.github.jerojasol.flights.domain.search.SearchCriteria;

public interface AggregateFlightsUseCase {

    AggregationResult aggregate(SearchCriteria criteria);
}
