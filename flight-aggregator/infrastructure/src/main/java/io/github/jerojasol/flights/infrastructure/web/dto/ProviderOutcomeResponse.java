package io.github.jerojasol.flights.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProviderOutcomeResponse(
        @Schema(description = "OK, TIMEOUT, ERROR or CIRCUIT_OPEN") String status,
        int received,
        int rejected,
        long elapsedMs) {
}
