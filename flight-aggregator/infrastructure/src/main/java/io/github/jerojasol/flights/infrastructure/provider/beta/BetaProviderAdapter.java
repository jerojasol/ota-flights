package io.github.jerojasol.flights.infrastructure.provider.beta;

import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.ProviderResponse;
import io.github.jerojasol.flights.domain.model.SearchCriteria;
import io.github.jerojasol.flights.domain.port.out.FlightProviderPort;
import io.github.jerojasol.flights.infrastructure.provider.ProviderResilience;
import io.github.jerojasol.flights.infrastructure.provider.ResilienceSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

/** Beta behind Retry(CircuitBreaker(TimeLimiter(fetch + map))). Throws ProviderUnavailableException on failure. */
public final class BetaProviderAdapter implements FlightProviderPort {

    private static final Logger LOG = LoggerFactory.getLogger(BetaProviderAdapter.class);

    private final BetaClient client;
    private final BetaOfferMapper mapper;
    private final ProviderResilience resilience;

    public BetaProviderAdapter(String baseUrl, ResilienceSettings settings, Executor executor) {
        this.client = new BetaClient(baseUrl, settings.timeout());
        this.mapper = new BetaOfferMapper();
        this.resilience = new ProviderResilience(Provider.BETA, settings, executor);
    }

    @Override
    public Provider provider() {
        return Provider.BETA;
    }

    @Override
    public ProviderResponse fetch(SearchCriteria criteria) {
        ProviderResponse response = resilience.call(() -> mapper.map(client.fetch(criteria)));
        LOG.info("provider=beta origin={} destination={} date={} offers={} unmappable={}",
                criteria.origin().code(), criteria.destination().code(), criteria.departureDate(),
                response.offers().size(), response.unmappable());
        return response;
    }

    /** Failed calls recorded by this adapter's circuit breaker; for tests in this package. */
    int circuitFailedCalls() {
        return resilience.failedCalls();
    }
}
