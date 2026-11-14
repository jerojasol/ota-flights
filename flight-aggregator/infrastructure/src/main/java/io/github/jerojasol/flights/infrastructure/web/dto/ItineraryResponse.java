package io.github.jerojasol.flights.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ItineraryResponse(
        @Schema(description = "Itinerary Key: stable and provider-independent") String id,
        String origin,
        String destination,
        List<SegmentResponse> segments,
        @Schema(description = "Price as quoted by the provider") PriceResponse basePrice,
        @Schema(description = "Base price after the markup policy") PriceResponse finalPrice,
        @Schema(description = "Provider that offered the cheapest price: ALPHA, BETA or GAMMA") String source,
        int connections,
        @Schema(description = "First departure to last arrival, ISO-8601 duration", example = "PT8H35M") String totalDuration) {
}
