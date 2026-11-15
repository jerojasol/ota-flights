package io.github.jerojasol.flights.infrastructure.persistence;

import io.github.jerojasol.flights.domain.model.Airport;
import io.github.jerojasol.flights.domain.model.Carrier;
import io.github.jerojasol.flights.domain.model.FlightNumber;
import io.github.jerojasol.flights.domain.model.Itinerary;
import io.github.jerojasol.flights.domain.model.ItineraryKey;
import io.github.jerojasol.flights.domain.model.Price;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.Segment;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/** Itinerary ↔ table row. Segments are stored as JSONB in a shape owned by this class, not by the domain. */
final class ItineraryPersistenceMapper {

    private static final TypeReference<List<SegmentJson>> SEGMENTS = new TypeReference<>() { };

    private final JsonMapper json;

    ItineraryPersistenceMapper(JsonMapper json) {
        this.json = json;
    }

    Itinerary toDomain(ItineraryJpaEntity entity) {
        List<Segment> segments = json.readValue(entity.segments(), SEGMENTS).stream()
                .map(SegmentJson::toDomain)
                .toList();
        return Itinerary.rehydrate(
                ItineraryKey.fromStored(entity.itineraryKey()),
                segments,
                Price.of(entity.basePrice().toPlainString(), entity.currency()),
                Price.of(entity.finalPrice().toPlainString(), entity.currency()),
                Provider.valueOf(entity.source().trim()),
                entity.ingestedAt());
    }

    ItineraryRow toRow(Itinerary itinerary) {
        List<SegmentJson> segments = itinerary.segments().stream().map(SegmentJson::from).toList();
        return new ItineraryRow(
                itinerary.key().value(),
                itinerary.origin().code(),
                itinerary.destination().code(),
                itinerary.segments().getFirst().departureAt().atOffset(ZoneOffset.UTC).toLocalDate(),
                itinerary.segments().getFirst().carrier().code(),
                itinerary.carriers().stream().map(Carrier::code).toArray(String[]::new),
                itinerary.basePrice().amount(),
                itinerary.finalPrice().amount(),
                itinerary.finalPrice().currency().getCurrencyCode(),
                itinerary.source().name(),
                json.writeValueAsString(segments),
                Timestamp.from(itinerary.ingestedAt()));
    }

    /** Column values for one upsert, in table vocabulary. */
    record ItineraryRow(String itineraryKey, String origin, String destination, LocalDate departureDate,
                        String firstCarrier, String[] carriers, BigDecimal basePrice, BigDecimal finalPrice,
                        String currency, String source, String segmentsJson, Timestamp ingestedAt) {
    }

    /** JSON shape of one stored segment. Instants are ISO-8601 UTC strings so the format never depends on mapper settings. */
    private record SegmentJson(String carrier, int number, String from, String departureAt, String to,
                               String arrivalAt) {

        static SegmentJson from(Segment segment) {
            return new SegmentJson(segment.carrier().code(), segment.flightNumber().number(),
                    segment.departureAirport().code(), segment.departureAt().toString(),
                    segment.arrivalAirport().code(), segment.arrivalAt().toString());
        }

        Segment toDomain() {
            return new Segment(new FlightNumber(Carrier.of(carrier), number), Airport.of(from),
                    Instant.parse(departureAt), Airport.of(to), Instant.parse(arrivalAt));
        }
    }
}
