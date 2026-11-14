package io.github.jerojasol.mockproviders.beta;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/** Beta's XML vocabulary, mirroring {@code docs/provider-contracts.md} element for element. */
public final class BetaXml {

    public static final String NAMESPACE = "urn:beta-gds:search:v2";

    private BetaXml() {
    }

    @JacksonXmlRootElement(localName = "SearchResponse", namespace = NAMESPACE)
    @JsonPropertyOrder({"Meta", "Itineraries"})
    public record SearchResponse(
            @JacksonXmlProperty(localName = "Meta", namespace = NAMESPACE) Meta meta,
            @JacksonXmlProperty(localName = "Itineraries", namespace = NAMESPACE) Itineraries itineraries) {
    }

    @JsonPropertyOrder({"RequestId", "Currency"})
    public record Meta(
            @JacksonXmlProperty(localName = "RequestId", namespace = NAMESPACE) String requestId,
            @JacksonXmlProperty(localName = "Currency", namespace = NAMESPACE) String currency) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Itineraries(
            @JacksonXmlProperty(localName = "count", isAttribute = true) int count,
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "Itinerary", namespace = NAMESPACE) List<Itinerary> itinerary) {
    }

    @JsonPropertyOrder({"ref", "Pricing", "Legs"})
    public record Itinerary(
            @JacksonXmlProperty(localName = "ref", isAttribute = true) String ref,
            @JacksonXmlProperty(localName = "Pricing", namespace = NAMESPACE) Pricing pricing,
            @JacksonXmlElementWrapper(localName = "Legs", namespace = NAMESPACE)
            @JacksonXmlProperty(localName = "Leg", namespace = NAMESPACE) List<Leg> legs) {
    }

    public record Pricing(@JacksonXmlProperty(localName = "Total", namespace = NAMESPACE) Total total) {
    }

    public record Total(
            @JacksonXmlProperty(localName = "amount", isAttribute = true) String amount,
            @JacksonXmlProperty(localName = "currency", isAttribute = true) String currency) {
    }

    @JsonPropertyOrder({"sequence", "Flight", "Departure", "Arrival"})
    public record Leg(
            @JacksonXmlProperty(localName = "sequence", isAttribute = true) int sequence,
            @JacksonXmlProperty(localName = "Flight", namespace = NAMESPACE) Flight flight,
            @JacksonXmlProperty(localName = "Departure", namespace = NAMESPACE) Endpoint departure,
            @JacksonXmlProperty(localName = "Arrival", namespace = NAMESPACE) Endpoint arrival) {
    }

    @JsonPropertyOrder({"Airline", "Number"})
    public record Flight(
            @JacksonXmlProperty(localName = "Airline", namespace = NAMESPACE) Code airline,
            @JacksonXmlProperty(localName = "Number", namespace = NAMESPACE) String number) {
    }

    @JsonPropertyOrder({"Airport", "LocalDateTime", "TimeZone"})
    public record Endpoint(
            @JacksonXmlProperty(localName = "Airport", namespace = NAMESPACE) Code airport,
            @JacksonXmlProperty(localName = "LocalDateTime", namespace = NAMESPACE) String localDateTime,
            @JacksonXmlProperty(localName = "TimeZone", namespace = NAMESPACE) String timeZone) {
    }

    public record Code(@JacksonXmlProperty(localName = "code", isAttribute = true) String code) {
    }

    @JacksonXmlRootElement(localName = "Fault", namespace = NAMESPACE)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"Code", "Message", "Reference"})
    public record Fault(
            @JacksonXmlProperty(localName = "Code", namespace = NAMESPACE) String code,
            @JacksonXmlProperty(localName = "Message", namespace = NAMESPACE) String message,
            @JacksonXmlProperty(localName = "Reference", namespace = NAMESPACE) String reference) {
    }
}
