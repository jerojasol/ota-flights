package io.github.jerojasol.flights.infrastructure.web.dto;

import java.time.Instant;
import java.util.Map;

public record AggregationResponse(int persisted, Instant aggregatedAt, Map<String, ProviderOutcomeResponse> providers) {
}
