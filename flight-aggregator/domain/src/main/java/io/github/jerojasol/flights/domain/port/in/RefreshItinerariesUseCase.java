package io.github.jerojasol.flights.domain.port.in;

import io.github.jerojasol.flights.domain.aggregation.AggregationResult;
import io.github.jerojasol.flights.domain.search.SearchCriteria;

import java.util.Optional;

/**
 * On-demand refresh of one criteria, under the same lock and freshness rules as a search's cold miss.
 *
 * @return the aggregation result; empty when the aggregation lock could not be taken, in which case nothing ran
 */
public interface RefreshItinerariesUseCase {

    Optional<AggregationResult> refresh(SearchCriteria criteria);
}
