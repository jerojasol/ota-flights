package io.github.jerojasol.flights.domain.model;

public final class InvalidAirportException extends DomainException {

    public InvalidAirportException(String code) {
        super("Invalid airport code: " + code);
    }
}
