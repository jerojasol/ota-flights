package io.github.jerojasol.flights.infrastructure.observability;

import io.github.jerojasol.flights.domain.aggregation.AggregationResult;
import io.github.jerojasol.flights.domain.port.in.AggregateFlightsUseCase;
import io.github.jerojasol.flights.domain.search.SearchCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorates the aggregation use case with one structured log line per provider outcome. Every aggregation goes through
 * here (cold miss, background refresh, admin refresh, scheduler), so each is logged once and the application layer
 * stays free of logging.
 */
public final class ProviderOutcomeLogger implements AggregateFlightsUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ProviderOutcomeLogger.class);

    private final AggregateFlightsUseCase delegate;

    public ProviderOutcomeLogger(AggregateFlightsUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    public AggregationResult aggregate(SearchCriteria criteria) {
        AggregationResult result = delegate.aggregate(criteria);
        String route = criteria.origin().code() + "-" + criteria.destination().code();
        result.outcomes().forEach((provider, outcome) -> LOG.info(
                "aggregation route={} date={} provider={} status={} received={} rejected={} elapsed_ms={}",
                route, criteria.departureDate(), provider, outcome.status(), outcome.offersReceived(),
                outcome.offersRejected(), outcome.elapsed().toMillis()));
        LOG.info("aggregation route={} date={} persisted={}", route, criteria.departureDate(), result.persisted());
        return result;
    }
}
