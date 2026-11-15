package io.github.jerojasol.flights.domain.exception;

public final class InvalidCarrierException extends DomainException {

    public InvalidCarrierException(String code) {
        super("Invalid carrier code: " + code);
    }
}
