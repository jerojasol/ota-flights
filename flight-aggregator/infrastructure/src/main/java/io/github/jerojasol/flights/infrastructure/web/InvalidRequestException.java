package io.github.jerojasol.flights.infrastructure.web;

/**
 * A well-formed request the domain rejects while it is being mapped (e.g. origin equals destination). Mapped to 422
 * with the domain's message, which describes the client's own input. Domain failures anywhere else are server faults.
 */
public final class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
