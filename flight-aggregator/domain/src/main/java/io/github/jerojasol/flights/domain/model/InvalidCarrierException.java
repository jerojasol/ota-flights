package io.github.jerojasol.flights.domain.model;

public final class InvalidCarrierException extends DomainException {

    public InvalidCarrierException(String code) {
        super("Invalid carrier code: " + code);
    }
}
