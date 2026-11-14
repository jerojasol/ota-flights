package io.github.jerojasol.mockproviders.beta;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import io.github.jerojasol.mockproviders.beta.BetaXml.Code;
import io.github.jerojasol.mockproviders.beta.BetaXml.Endpoint;
import io.github.jerojasol.mockproviders.beta.BetaXml.Flight;
import io.github.jerojasol.mockproviders.beta.BetaXml.Itineraries;
import io.github.jerojasol.mockproviders.beta.BetaXml.Itinerary;
import io.github.jerojasol.mockproviders.beta.BetaXml.Leg;
import io.github.jerojasol.mockproviders.beta.BetaXml.Meta;
import io.github.jerojasol.mockproviders.beta.BetaXml.Pricing;
import io.github.jerojasol.mockproviders.beta.BetaXml.SearchResponse;
import io.github.jerojasol.mockproviders.beta.BetaXml.Total;
import io.github.jerojasol.mockproviders.generator.GeneratedItinerary;
import io.github.jerojasol.mockproviders.generator.GeneratedSegment;

/**
 * Beta's traps are deliberate: local times without offset plus an IANA zone, unpadded flight numbers,
 * string amounts and legs emitted out of document order.
 */
final class BetaMapper {

    static final ZoneId UNKNOWN_AIRPORT_ZONE = ZoneId.of("UTC");

    private static final Map<String, ZoneId> AIRPORT_ZONES = Map.ofEntries(
            Map.entry("MAD", ZoneId.of("Europe/Madrid")),
            Map.entry("BCN", ZoneId.of("Europe/Madrid")),
            Map.entry("LHR", ZoneId.of("Europe/London")),
            Map.entry("CDG", ZoneId.of("Europe/Paris")),
            Map.entry("AMS", ZoneId.of("Europe/Amsterdam")),
            Map.entry("FRA", ZoneId.of("Europe/Berlin")),
            Map.entry("FCO", ZoneId.of("Europe/Rome")),
            Map.entry("LIS", ZoneId.of("Europe/Lisbon")),
            Map.entry("JFK", ZoneId.of("America/New_York")),
            Map.entry("MIA", ZoneId.of("America/New_York")),
            Map.entry("ORD", ZoneId.of("America/Chicago")),
            Map.entry("LAX", ZoneId.of("America/Los_Angeles")),
            Map.entry("MEX", ZoneId.of("America/Mexico_City")),
            Map.entry("BOG", ZoneId.of("America/Bogota")),
            Map.entry("GRU", ZoneId.of("America/Sao_Paulo")),
            Map.entry("DXB", ZoneId.of("Asia/Dubai")),
            Map.entry("NRT", ZoneId.of("Asia/Tokyo")));

    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");

    private final long seed;

    BetaMapper(long seed) {
        this.seed = seed;
    }

    SearchResponse toSearchResponse(List<GeneratedItinerary> inventory, String requestId, String currency) {
        List<Itinerary> itineraries = new ArrayList<>(inventory.size());
        boolean anyOutOfOrder = false;
        int firstMultiLeg = -1;
        for (GeneratedItinerary generated : inventory) {
            List<Leg> legs = legsInDocumentOrder(generated);
            if (legs.size() >= 2) {
                anyOutOfOrder |= isOutOfOrder(legs);
                if (firstMultiLeg < 0) {
                    firstMultiLeg = itineraries.size();
                }
            }
            itineraries.add(new Itinerary(ref(generated.canonicalId()), pricing(generated), legs));
        }
        if (!anyOutOfOrder && firstMultiLeg >= 0) {
            Itinerary first = itineraries.get(firstMultiLeg);
            List<Leg> reversed = new ArrayList<>(first.legs());
            reversed.sort((a, b) -> Integer.compare(b.sequence(), a.sequence()));
            itineraries.set(firstMultiLeg, new Itinerary(first.ref(), first.pricing(), reversed));
        }
        return new SearchResponse(new Meta(requestId, currency),
                new Itineraries(itineraries.size(), itineraries.isEmpty() ? null : itineraries));
    }

    private List<Leg> legsInDocumentOrder(GeneratedItinerary itinerary) {
        List<GeneratedSegment> segments = itinerary.segments();
        List<Leg> legs = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            legs.add(toLeg(i + 1, segments.get(i)));
        }
        Collections.shuffle(legs, new Random(Objects.hash(seed, itinerary.canonicalId())));
        return legs;
    }

    private static boolean isOutOfOrder(List<Leg> legs) {
        for (int i = 0; i < legs.size(); i++) {
            if (legs.get(i).sequence() != i + 1) {
                return true;
            }
        }
        return false;
    }

    static Leg toLeg(int sequence, GeneratedSegment segment) {
        return new Leg(sequence,
                new Flight(new Code(segment.carrier()), Integer.toString(segment.flightNumber())),
                endpoint(segment.from(), segment.departureUtc()),
                endpoint(segment.to(), segment.arrivalUtc()));
    }

    static Endpoint endpoint(String airport, Instant instant) {
        ZoneId zone = zoneOf(airport);
        return new Endpoint(new Code(airport), LOCAL.format(instant.atZone(zone)), zone.getId());
    }

    static ZoneId zoneOf(String airport) {
        return AIRPORT_ZONES.getOrDefault(airport, UNKNOWN_AIRPORT_ZONE);
    }

    private static Pricing pricing(GeneratedItinerary itinerary) {
        return new Pricing(new Total(BigDecimal.valueOf(itinerary.priceCents(), 2).toPlainString(),
                itinerary.currency()));
    }

    static String ref(String canonicalId) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonicalId.getBytes(StandardCharsets.UTF_8));
            return "BET-" + HexFormat.of().formatHex(hash).substring(0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
