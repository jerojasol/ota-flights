package io.github.jerojasol.flights.application;

import io.github.jerojasol.flights.domain.model.Airport;
import io.github.jerojasol.flights.domain.model.FlightNumber;
import io.github.jerojasol.flights.domain.model.Offer;
import io.github.jerojasol.flights.domain.model.Price;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.SearchCriteria;
import io.github.jerojasol.flights.domain.model.Segment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

final class Fixtures {

    static final Airport MAD = new Airport("MAD");
    static final Airport JFK = new Airport("JFK");
    static final Airport LHR = new Airport("LHR");

    static final SearchCriteria MAD_JFK = new SearchCriteria(MAD, JFK, LocalDate.parse("2026-11-20"));

    private Fixtures() {
    }

    /** Direct MAD-JFK offer; the flight number alone decides the Itinerary Key. */
    static Offer direct(Provider provider, String flightNumber, String amount) {
        return new Offer(provider, List.of(madJfk(flightNumber)), Price.of(amount, "EUR"));
    }

    /** MAD-LHR-JFK with a 30 minute connection: rejected as CONNECTION_TOO_SHORT (I7). */
    static Offer shortConnection(Provider provider, String amount) {
        Segment first = new Segment(FlightNumber.of("BA457"), MAD,
                Instant.parse("2026-11-20T07:00:00Z"), LHR, Instant.parse("2026-11-20T09:30:00Z"));
        Segment second = new Segment(FlightNumber.of("BA117"), LHR,
                Instant.parse("2026-11-20T10:00:00Z"), JFK, Instant.parse("2026-11-20T18:00:00Z"));
        return new Offer(provider, List.of(first, second), Price.of(amount, "EUR"));
    }

    private static Segment madJfk(String flightNumber) {
        return new Segment(FlightNumber.of(flightNumber), MAD,
                Instant.parse("2026-11-20T07:35:00Z"), JFK, Instant.parse("2026-11-20T16:05:00Z"));
    }
}
