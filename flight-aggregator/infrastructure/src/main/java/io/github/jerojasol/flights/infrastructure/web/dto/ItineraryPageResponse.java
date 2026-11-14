package io.github.jerojasol.flights.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ItineraryPageResponse(
        List<ItineraryResponse> items,
        @Schema(description = "Pass as `cursor` to get the next page; null on the last page") String nextCursor,
        @Schema(description = "FRESH, STALE, REFRESHING or MISSING") String freshness,
        @Schema(description = "When the last aggregation that produced the data shown ran; null if unknown. "
                + "A refresh in which every provider failed stored nothing and does not change it") Instant aggregatedAt,
        @Schema(description = "Outcome by provider of the last aggregation that produced the data shown, not of the last attempt. "
                + "A refresh in which every provider failed is not reflected here") Map<String, ProviderOutcomeResponse> providers) {
}
