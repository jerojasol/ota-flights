package io.github.jerojasol.flights.domain.model;

/** Outcome of a provider call during an aggregation. */
public enum ProviderStatus {
    OK,
    TIMEOUT,
    ERROR,
    CIRCUIT_OPEN
}
