package io.github.jerojasol.flights.infrastructure.provider.beta;

import io.github.jerojasol.flights.domain.aggregation.ProviderResponse;
import io.github.jerojasol.flights.domain.exception.DomainException;
import io.github.jerojasol.flights.domain.model.Airport;
import io.github.jerojasol.flights.domain.model.FlightNumber;
import io.github.jerojasol.flights.domain.model.Offer;
import io.github.jerojasol.flights.domain.model.Price;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.Segment;
import io.github.jerojasol.flights.infrastructure.provider.ProviderContractViolation;
import io.github.jerojasol.flights.infrastructure.provider.beta.BetaSearchResponse.BetaItinerary;
import io.github.jerojasol.flights.infrastructure.provider.beta.BetaSearchResponse.Endpoint;
import io.github.jerojasol.flights.infrastructure.provider.beta.BetaSearchResponse.Leg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Beta → domain. Beta sends local time plus an IANA zone per endpoint; the instant is the combination of the two.
 * Legs arrive in any order and are sorted by {@code sequence}, which must then read 1..n with no gap or repeat.
 * {@code Number} has no leading zeros; FlightNumber normalises whatever it gets.
 *
 * <p>Every required element and attribute is checked explicitly. A missing one, or a leg sequence that does not
 * read 1..n, is a {@link MalformedEntry}. The mapper catches only that, DomainException, and the zone and time
 * parsers' DateTimeException: an itinerary that fails any of them is counted as unmappable and never fails the
 * response. Connection rules are not checked here; OfferValidator does that after mapping.
 */
final class BetaOfferMapper {

    private static final Logger LOG = LoggerFactory.getLogger(BetaOfferMapper.class);

    ProviderResponse map(BetaSearchResponse response) {
        if (response == null || response.itineraries() == null) {
            throw new ProviderContractViolation("Beta response has no Itineraries element");
        }
        List<BetaItinerary> entries = response.itineraries().itinerary();
        if (entries == null) {
            return ProviderResponse.of(List.of());
        }
        List<Offer> offers = new ArrayList<>(entries.size());
        int unmappable = 0;
        for (BetaItinerary itinerary : entries) {
            if (itinerary == null) {
                unmappable++;
                LOG.debug("provider=beta itinerary_ref=none outcome=UNMAPPABLE reason=\"null itinerary entry\"");
                continue;
            }
            try {
                offers.add(toOffer(itinerary));
            } catch (DomainException | DateTimeException | MalformedEntry invalid) {
                unmappable++;
                LOG.debug("provider=beta itinerary_ref={} outcome=UNMAPPABLE reason=\"{}\"",
                        itinerary.ref(), invalid.getMessage());
            }
        }
        return new ProviderResponse(offers, unmappable);
    }

    private static Offer toOffer(BetaItinerary itinerary) {
        BetaSearchResponse.Total total = required(required(itinerary.pricing(), "Pricing").total(), "Total");
        Price price = Price.of(required(total.amount(), "Total/@amount"), required(total.currency(), "Total/@currency"));
        List<Segment> segments = segments(required(itinerary.legs(), "Legs"));
        return new Offer(Provider.BETA, segments, price);
    }

    private static List<Segment> segments(BetaSearchResponse.Legs legs) {
        List<Leg> entries = required(legs.leg(), "Leg");
        if (entries.isEmpty()) {
            throw new MalformedEntry("Legs has no Leg");
        }
        List<Leg> ordered = new ArrayList<>(entries.size());
        for (Leg leg : entries) {
            ordered.add(required(leg, "Leg"));
        }
        ordered.sort(Comparator.comparingInt(BetaOfferMapper::sequence));
        for (int i = 0; i < ordered.size(); i++) {
            if (sequence(ordered.get(i)) != i + 1) {
                throw new MalformedEntry("Leg sequence must read 1..n with no gap or repeat");
            }
        }
        return ordered.stream().map(BetaOfferMapper::toSegment).toList();
    }

    private static int sequence(Leg leg) {
        String sequence = required(leg.sequence(), "Leg/@sequence");
        try {
            return Integer.parseInt(sequence);
        } catch (NumberFormatException notANumber) {
            throw new MalformedEntry("Leg/@sequence is not a number: " + sequence);
        }
    }

    private static Segment toSegment(Leg leg) {
        BetaSearchResponse.Flight flight = required(leg.flight(), "Flight");
        String flightNumber = required(required(flight.airline(), "Airline").code(), "Airline/@code")
                + required(flight.number(), "Number");
        Endpoint departure = required(leg.departure(), "Departure");
        Endpoint arrival = required(leg.arrival(), "Arrival");
        return new Segment(FlightNumber.of(flightNumber),
                airport(departure), instant(departure),
                airport(arrival), instant(arrival));
    }

    private static Airport airport(Endpoint endpoint) {
        return Airport.of(required(required(endpoint.airport(), "Airport").code(), "Airport/@code"));
    }

    /**
     * Local time in the endpoint's own zone. The zone is IANA; an unknown name is a DateTimeException. A local time
     * inside a daylight-saving gap does not exist, so it is malformed. A local time in the autumn overlap happens
     * twice and the earlier offset is used: a departure in the repeated hour may be mapped one hour early
     * (documented limitation, see provider-contracts.md, Beta).
     */
    private static Instant instant(Endpoint endpoint) {
        LocalDateTime local = LocalDateTime.parse(required(endpoint.localDateTime(), "LocalDateTime"));
        ZoneId zone = ZoneId.of(required(endpoint.timeZone(), "TimeZone"));
        if (zone.getRules().getValidOffsets(local).isEmpty()) {
            throw new MalformedEntry("local time " + local + " does not exist in " + zone + " (daylight-saving gap)");
        }
        return ZonedDateTime.of(local, zone).withEarlierOffsetAtOverlap().toInstant();
    }

    private static <T> T required(T value, String element) {
        if (value == null) {
            throw new MalformedEntry("missing " + element);
        }
        return value;
    }

    /** A structural problem in one itinerary, raised by the explicit checks above. */
    static final class MalformedEntry extends RuntimeException {

        MalformedEntry(String message) {
            super(message);
        }
    }
}
