package io.github.jerojasol.flights.domain.model;

public final class InvalidFlightNumberException extends DomainException {

    public InvalidFlightNumberException(String flightNumber) {
        super("Invalid flight number: " + flightNumber);
    }
}
