package io.github.jerojasol.flights.domain.search;

import io.github.jerojasol.flights.domain.exception.InvalidSearchException;
import io.github.jerojasol.flights.domain.model.Airport;
import java.time.LocalDate;

public record SearchCriteria(Airport origin, Airport destination, LocalDate departureDate) {

    public SearchCriteria {
        if (origin == null || destination == null || departureDate == null) {
            throw new InvalidSearchException("Search criteria require origin, destination and departure date");
        }
        if (origin.equals(destination)) {
            throw new InvalidSearchException("Origin and destination must differ: " + origin.code());
        }
    }
}
