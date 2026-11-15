package io.github.jerojasol.flights.domain.exception;

import io.github.jerojasol.flights.domain.aggregation.ProviderStatus;
import io.github.jerojasol.flights.domain.model.Provider;

/** Thrown by a FlightProviderPort when a provider cannot deliver Offers. The status is never OK. */
public final class ProviderUnavailableException extends DomainException {

    private final Provider provider;
    private final ProviderStatus status;

    public ProviderUnavailableException(Provider provider, ProviderStatus status) {
        super("Provider " + provider + " unavailable: " + status);
        if (provider == null || status == null || status == ProviderStatus.OK) {
            throw new InvalidAggregationException("An unavailable provider needs a provider and a non-OK status");
        }
        this.provider = provider;
        this.status = status;
    }

    public ProviderUnavailableException(Provider provider, ProviderStatus status, Throwable cause) {
        this(provider, status);
        initCause(cause);
    }

    public Provider provider() {
        return provider;
    }

    public ProviderStatus status() {
        return status;
    }
}
