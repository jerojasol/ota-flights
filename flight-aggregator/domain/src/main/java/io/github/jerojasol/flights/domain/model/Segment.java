package io.github.jerojasol.flights.domain.model;

import java.time.Duration;
import java.time.Instant;

public record Segment(
        FlightNumber flightNumber,
        Airport departureAirport,
        Instant departureAt,
        Airport arrivalAirport,
        Instant arrivalAt) {

    public static final Duration MAX_DURATION = Duration.ofHours(20);

    public Segment {
        if (flightNumber == null || departureAirport == null || departureAt == null
                || arrivalAirport == null || arrivalAt == null) {
            throw new InvalidSegmentException("Segment fields are required");
        }
        if (departureAirport.equals(arrivalAirport)) {
            throw new InvalidSegmentException("Dead leg: departure and arrival airport are both " + departureAirport.code());
        }
        if (!arrivalAt.isAfter(departureAt)) {
            throw new InvalidSegmentException("Dead leg: arrival " + arrivalAt + " is not after departure " + departureAt);
        }
        if (Duration.between(departureAt, arrivalAt).compareTo(MAX_DURATION) > 0) {
            throw new InvalidSegmentException("Segment duration exceeds " + MAX_DURATION.toHours() + " hours");
        }
    }

    public Carrier carrier() {
        return flightNumber.carrier();
    }

    public Duration duration() {
        return Duration.between(departureAt, arrivalAt);
    }
}
