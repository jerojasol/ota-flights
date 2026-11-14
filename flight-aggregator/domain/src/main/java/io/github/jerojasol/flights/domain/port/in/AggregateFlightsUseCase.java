package io.github.jerojasol.flights.domain.port.in;

import io.github.jerojasol.flights.domain.model.AggregationResult;
import io.github.jerojasol.flights.domain.model.SearchCriteria;

public interface AggregateFlightsUseCase {

    AggregationResult aggregate(SearchCriteria criteria);
}
