package io.github.jerojasol.flights.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ItineraryPageResponse(
        List<ItineraryResponse> items,
        @Schema(description = "Pass as `cursor` to get the next page; null on the last page") String nextCursor,
        @Schema(description = "FRESH, STALE, REFRESHING or MISSING") String freshness,
        @Schema(description = "When the data was last aggregated; null if unknown") Instant aggregatedAt,
        @Schema(description = "Outcome of the last aggregation, by provider") Map<String, ProviderOutcomeResponse> providers) {
}
