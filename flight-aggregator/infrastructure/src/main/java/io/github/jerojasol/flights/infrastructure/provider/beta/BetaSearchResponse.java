package io.github.jerojasol.flights.infrastructure.provider.beta;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Beta's 200 body in Beta's vocabulary. Every value is kept as raw text (numbers, amounts, local times, zones), and
 * BetaOfferMapper parses them per itinerary. Attributes are marked explicitly; the namespace is matched by local name.
 */
@JacksonXmlRootElement(localName = "SearchResponse")
record BetaSearchResponse(@JacksonXmlProperty(localName = "Itineraries") Itineraries itineraries) {

    record Itineraries(
            @JacksonXmlProperty(isAttribute = true, localName = "count") String count,
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "Itinerary") List<BetaItinerary> itinerary) {
    }

    record BetaItinerary(
            @JacksonXmlProperty(isAttribute = true, localName = "ref") String ref,
            @JacksonXmlProperty(localName = "Pricing") Pricing pricing,
            @JacksonXmlProperty(localName = "Legs") Legs legs) {
    }

    record Pricing(@JacksonXmlProperty(localName = "Total") Total total) {
    }

    record Total(
            @JacksonXmlProperty(isAttribute = true, localName = "amount") String amount,
            @JacksonXmlProperty(isAttribute = true, localName = "currency") String currency) {
    }

    record Legs(@JacksonXmlElementWrapper(useWrapping = false)
                @JacksonXmlProperty(localName = "Leg") List<Leg> leg) {
    }

    record Leg(
            @JacksonXmlProperty(isAttribute = true, localName = "sequence") String sequence,
            @JacksonXmlProperty(localName = "Flight") Flight flight,
            @JacksonXmlProperty(localName = "Departure") Endpoint departure,
            @JacksonXmlProperty(localName = "Arrival") Endpoint arrival) {
    }

    record Flight(
            @JacksonXmlProperty(localName = "Airline") Airline airline,
            @JacksonXmlProperty(localName = "Number") String number) {
    }

    record Airline(@JacksonXmlProperty(isAttribute = true, localName = "code") String code) {
    }

    record Endpoint(
            @JacksonXmlProperty(localName = "Airport") BetaAirportRef airport,
            @JacksonXmlProperty(localName = "LocalDateTime") String localDateTime,
            @JacksonXmlProperty(localName = "TimeZone") String timeZone) {
    }

    record BetaAirportRef(@JacksonXmlProperty(isAttribute = true, localName = "code") String code) {
    }
}
