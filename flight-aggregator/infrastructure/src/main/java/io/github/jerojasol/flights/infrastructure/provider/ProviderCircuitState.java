package io.github.jerojasol.flights.infrastructure.provider;

/** A provider's circuit breaker as operators see it. */
public enum ProviderCircuitState {
    CLOSED,
    HALF_OPEN,
    OPEN
}
