package io.github.jerojasol.flights.infrastructure.web;

/** A {@code cursor} query parameter that is not one this API issued. Mapped to 400. */
public final class InvalidCursorException extends RuntimeException {

    public InvalidCursorException(String message) {
        super(message);
    }
}
