package io.github.jerojasol.flights.infrastructure.provider.gamma;

import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.ProviderResponse;
import io.github.jerojasol.flights.domain.model.SearchCriteria;
import io.github.jerojasol.flights.domain.port.out.FlightProviderPort;
import io.github.jerojasol.flights.infrastructure.provider.ProviderCircuitState;
import io.github.jerojasol.flights.infrastructure.provider.ProviderResilience;
import io.github.jerojasol.flights.infrastructure.provider.ResilienceSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

/** Gamma behind Retry(CircuitBreaker(TimeLimiter(stream + map))). Throws ProviderUnavailableException on failure. */
public final class GammaProviderAdapter implements FlightProviderPort {

    private static final Logger LOG = LoggerFactory.getLogger(GammaProviderAdapter.class);

    private final GammaClient client;
    private final GammaOfferMapper mapper;
    private final ProviderResilience resilience;

    public GammaProviderAdapter(String baseUrl, ResilienceSettings settings, Executor executor) {
        this.client = new GammaClient(baseUrl, settings.timeout());
        this.mapper = new GammaOfferMapper();
        this.resilience = new ProviderResilience(Provider.GAMMA, settings, executor);
    }

    @Override
    public Provider provider() {
        return Provider.GAMMA;
    }

    @Override
    public ProviderResponse fetch(SearchCriteria criteria) {
        ProviderResponse response = resilience.call(() -> client.fetch(criteria, mapper::map));
        LOG.info("provider=gamma origin={} destination={} date={} offers={} unmappable={}",
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
