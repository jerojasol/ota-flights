package io.github.jerojasol.flights.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record SegmentResponse(
        String carrier,
        @Schema(description = "IATA flight designator: carrier code followed by the number, e.g. IB6251") String flightNumber,
        String from,
        String to,
        Instant departureAt,
        Instant arrivalAt) {
}
