package io.github.jerojasol.flights.infrastructure.provider;

/**
 * A 2xx response whose body as a whole does not match the provider's contract: unreadable JSON/XML, or a required
 * top-level element missing. It fails the same way on every attempt, so it is never retried; the circuit breaker
 * still records it as a failure. Problems in a single itinerary are not this: they are counted as unmappable.
 */
public final class ProviderContractViolation extends RuntimeException {

    public ProviderContractViolation(String message) {
        super(message);
    }

    public ProviderContractViolation(String message, Throwable cause) {
        super(message, cause);
    }
}
