package io.github.jerojasol.flights.domain.model;

public final class InvalidPriceException extends DomainException {

    public InvalidPriceException(String message) {
        super(message);
    }
}
