package io.github.jerojasol.flights.infrastructure.provider.alpha;

import io.github.jerojasol.flights.domain.aggregation.ProviderResponse;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.port.out.FlightProviderPort;
import io.github.jerojasol.flights.domain.search.SearchCriteria;
import io.github.jerojasol.flights.infrastructure.provider.ProviderCircuitState;
import io.github.jerojasol.flights.infrastructure.provider.ProviderResilience;
import io.github.jerojasol.flights.infrastructure.provider.ResilienceSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

/** Alpha behind Retry(CircuitBreaker(TimeLimiter(fetch + map))). Throws ProviderUnavailableException on failure. */
public final class AlphaProviderAdapter implements FlightProviderPort {

    private static final Logger LOG = LoggerFactory.getLogger(AlphaProviderAdapter.class);

    private final AlphaClient client;
    private final AlphaOfferMapper mapper;
    private final ProviderResilience resilience;

    public AlphaProviderAdapter(String baseUrl, ResilienceSettings settings, Executor executor) {
        this.client = new AlphaClient(baseUrl, settings.timeout());
        this.mapper = new AlphaOfferMapper();
        this.resilience = new ProviderResilience(Provider.ALPHA, settings, executor);
    }

    @Override
    public Provider provider() {
        return Provider.ALPHA;
    }

    @Override
    public ProviderResponse fetch(SearchCriteria criteria) {
        ProviderResponse response = resilience.call(() -> mapper.map(client.fetch(criteria)));
        LOG.info("provider=alpha origin={} destination={} date={} offers={} unmappable={}",
                criteria.origin().code(), criteria.destination().code(), criteria.departureDate(),
                response.offers().size(), response.unmappable());
        return response;
    }

    /** For the provider health indicator, built in config. */
    public ProviderCircuitState circuitState() {
        return resilience.circuitState();
    }

    /** Failed calls recorded by this adapter's circuit breaker; for tests in this package. */
    int circuitFailedCalls() {
        return resilience.failedCalls();
    }
}
