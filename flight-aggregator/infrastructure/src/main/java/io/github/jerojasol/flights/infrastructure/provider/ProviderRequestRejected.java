package io.github.jerojasol.flights.infrastructure.provider;

/**
 * The provider rejected our request with a 4xx other than 408 or 429: the fault is in the request we built, and the
 * provider is healthy. It would be rejected the same way again, so it is not retried, and the circuit breaker
 * ignores it (neither a failure nor a success). Reported to the domain as {@code ERROR}.
 */
public final class ProviderRequestRejected extends RuntimeException {

    public ProviderRequestRejected(String message) {
        super(message);
    }
}
