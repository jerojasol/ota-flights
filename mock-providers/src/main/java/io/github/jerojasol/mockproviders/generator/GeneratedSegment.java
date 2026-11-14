package io.github.jerojasol.mockproviders.generator;

import java.time.Instant;

public record GeneratedSegment(
        String carrier,
        int flightNumber,
        String from,
        String to,
        Instant departureUtc,
        Instant arrivalUtc) {
}
