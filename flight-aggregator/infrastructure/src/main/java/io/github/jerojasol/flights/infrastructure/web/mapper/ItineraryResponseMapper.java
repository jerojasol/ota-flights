package io.github.jerojasol.flights.infrastructure.web.mapper;

import io.github.jerojasol.flights.domain.model.AggregationResult;
import io.github.jerojasol.flights.domain.model.Itinerary;
import io.github.jerojasol.flights.domain.model.Price;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.ProviderOutcome;
import io.github.jerojasol.flights.domain.model.SearchResult;
import io.github.jerojasol.flights.domain.model.Segment;
import io.github.jerojasol.flights.infrastructure.web.CursorCodec;
import io.github.jerojasol.flights.infrastructure.web.dto.AggregationResponse;
import io.github.jerojasol.flights.infrastructure.web.dto.ItineraryPageResponse;
import io.github.jerojasol.flights.infrastructure.web.dto.ItineraryResponse;
import io.github.jerojasol.flights.infrastructure.web.dto.PriceResponse;
import io.github.jerojasol.flights.infrastructure.web.dto.ProviderOutcomeResponse;
import io.github.jerojasol.flights.infrastructure.web.dto.SegmentResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/** Domain → response DTOs. Hand-written; no business decisions, only translation. */
public final class ItineraryResponseMapper {

    private ItineraryResponseMapper() {
    }

    public static ItineraryPageResponse page(SearchResult result) {
        return new ItineraryPageResponse(
                result.page().items().stream().map(ItineraryResponseMapper::itinerary).toList(),
                result.page().next().map(CursorCodec::encode).orElse(null),
                result.freshness().name(),
                result.aggregatedAt().orElse(null),
                providers(result.lastOutcomes()));
    }

    public static AggregationResponse aggregation(AggregationResult result) {
        return new AggregationResponse(result.persisted(), result.aggregatedAt(), providers(result.outcomes()));
    }

    static ItineraryResponse itinerary(Itinerary itinerary) {
        return new ItineraryResponse(
                itinerary.key().value(),
                itinerary.origin().code(),
                itinerary.destination().code(),
                itinerary.segments().stream().map(ItineraryResponseMapper::segment).toList(),
                price(itinerary.basePrice()),
                price(itinerary.finalPrice()),
                itinerary.source().name(),
                itinerary.connections(),
                itinerary.totalDuration().toString());
    }

    private static SegmentResponse segment(Segment segment) {
        return new SegmentResponse(
                segment.carrier().code(),
                segment.carrier().code() + segment.flightNumber().number(),
                segment.departureAirport().code(),
                segment.arrivalAirport().code(),
                segment.departureAt(),
                segment.arrivalAt());
    }

    private static PriceResponse price(Price price) {
        return new PriceResponse(price.amount(), price.currency().getCurrencyCode());
    }

    /** In provider declaration order, so the JSON is stable. */
    private static Map<String, ProviderOutcomeResponse> providers(Map<Provider, ProviderOutcome> outcomes) {
        Map<String, ProviderOutcomeResponse> byName = new LinkedHashMap<>();
        for (Provider provider : Provider.values()) {
            ProviderOutcome outcome = outcomes.get(provider);
            if (outcome != null) {
                byName.put(provider.name(), new ProviderOutcomeResponse(outcome.status().name(),
                        outcome.offersReceived(), outcome.offersRejected(), outcome.elapsed().toMillis()));
            }
        }
        return byName;
    }
}
