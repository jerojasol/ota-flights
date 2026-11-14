package io.github.jerojasol.flights.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Aggregate root: a complete journey from origin to destination sold at a Price. */
public final class Itinerary {

    public static final int MAX_SEGMENTS = 4;
    public static final Duration MIN_CONNECTION = Duration.ofMinutes(45);
    public static final Duration MAX_CONNECTION = Duration.ofHours(24);
    public static final Duration MAX_TOTAL_DURATION = Duration.ofHours(72);

    private final ItineraryKey key;
    private final List<Segment> segments;
    private final Price basePrice;
    private final Price finalPrice;
    private final Provider source;
    private final Instant ingestedAt;

    private Itinerary(ItineraryKey key, List<Segment> segments, Price basePrice, Price finalPrice,
            Provider source, Instant ingestedAt) {
        this.key = key;
        this.segments = segments;
        this.basePrice = basePrice;
        this.finalPrice = finalPrice;
        this.source = source;
        this.ingestedAt = ingestedAt;
    }

    /** Builds the Itinerary for a winning Offer. Key (I12) and final price (I10) are always derived here. */
    public static Itinerary from(Offer winning, MarkupPolicy markup, Instant ingestedAt) {
        require(winning != null && markup != null && ingestedAt != null, "Offer, markup policy and ingestion time are required");
        List<Segment> segments = winning.segments();
        validateStructure(segments);
        Price finalPrice = markup.apply(winning.basePrice());
        requireSameCurrency(winning.basePrice(), finalPrice);
        return new Itinerary(ItineraryKey.of(segments), segments, winning.basePrice(), finalPrice,
                winning.provider(), ingestedAt);
    }

    public static Itinerary from(Offer winning, MarkupPolicy markup) {
        return from(winning, markup, Instant.now());
    }

    /**
     * Rebuilds an Itinerary read from storage. Structure (I1–I9) and currency (I11) are still checked, but the
     * stored key and prices are trusted: the markup percentage in force when the row was written may differ from
     * today's policy, and re-deriving would silently reprice history. For the persistence adapter only.
     */
    public static Itinerary rehydrate(ItineraryKey key, List<Segment> segments, Price basePrice, Price finalPrice,
            Provider source, Instant ingestedAt) {
        require(key != null && basePrice != null && finalPrice != null && source != null && ingestedAt != null,
                "Stored itinerary fields are required");
        validateStructure(segments);
        requireSameCurrency(basePrice, finalPrice);
        return new Itinerary(key, List.copyOf(segments), basePrice, finalPrice, source, ingestedAt);
    }

    private static void validateStructure(List<Segment> segments) {
        if (segments == null || segments.isEmpty()) {
            throw new InvalidItineraryException(RejectionReason.NO_SEGMENTS, "Itinerary has no segments");
        }
        if (segments.size() > MAX_SEGMENTS) {
            throw new InvalidItineraryException(RejectionReason.TOO_MANY_SEGMENTS,
                    "Itinerary has " + segments.size() + " segments, maximum is " + MAX_SEGMENTS);
        }
        for (int i = 0; i + 1 < segments.size(); i++) {
            validateConnection(segments.get(i), segments.get(i + 1));
        }
        Segment first = segments.getFirst();
        Segment last = segments.getLast();
        if (first.departureAirport().equals(last.arrivalAirport())) {
            throw new InvalidItineraryException(RejectionReason.ORIGIN_EQUALS_DESTINATION,
                    "Origin equals destination: " + first.departureAirport().code());
        }
        if (Duration.between(first.departureAt(), last.arrivalAt()).compareTo(MAX_TOTAL_DURATION) > 0) {
            throw new InvalidItineraryException(RejectionReason.TOTAL_DURATION_EXCEEDED,
                    "Total duration exceeds " + MAX_TOTAL_DURATION.toHours() + " hours");
        }
    }

    private static void validateConnection(Segment previous, Segment next) {
        if (!previous.arrivalAirport().equals(next.departureAirport())) {
            throw new InvalidItineraryException(RejectionReason.AIRPORT_MISMATCH_AT_CONNECTION,
                    "Segment arrives at " + previous.arrivalAirport().code()
                            + " but next departs from " + next.departureAirport().code());
        }
        if (!next.departureAt().isAfter(previous.arrivalAt())) {
            throw new InvalidItineraryException(RejectionReason.CONNECTION_OVERLAP,
                    "Next segment departs at or before the previous arrival");
        }
        Duration connection = Duration.between(previous.arrivalAt(), next.departureAt());
        if (connection.compareTo(MIN_CONNECTION) < 0) {
            throw new InvalidItineraryException(RejectionReason.CONNECTION_TOO_SHORT,
                    "Connection of " + connection.toMinutes() + " minutes is below " + MIN_CONNECTION.toMinutes());
        }
        if (connection.compareTo(MAX_CONNECTION) > 0) {
            throw new InvalidItineraryException(RejectionReason.CONNECTION_TOO_LONG,
                    "Connection of " + connection.toMinutes() + " minutes exceeds " + MAX_CONNECTION.toMinutes());
        }
    }

    private static void requireSameCurrency(Price base, Price derived) {
        if (!base.currency().equals(derived.currency())) {
            throw new InvalidItineraryException("Final price currency " + derived.currency()
                    + " differs from base price currency " + base.currency());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidItineraryException(message);
        }
    }

    public ItineraryKey key() {
        return key;
    }

    public List<Segment> segments() {
        return segments;
    }

    public Price basePrice() {
        return basePrice;
    }

    public Price finalPrice() {
        return finalPrice;
    }

    public Provider source() {
        return source;
    }

    public Instant ingestedAt() {
        return ingestedAt;
    }

    public Airport origin() {
        return segments.getFirst().departureAirport();
    }

    public Airport destination() {
        return segments.getLast().arrivalAirport();
    }

    public int connections() {
        return segments.size() - 1;
    }

    public Duration totalDuration() {
        return Duration.between(segments.getFirst().departureAt(), segments.getLast().arrivalAt());
    }

    public List<Carrier> carriers() {
        return segments.stream().map(Segment::carrier).distinct().toList();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Itinerary that && key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public String toString() {
        return "Itinerary[" + origin().code() + "->" + destination().code() + ", key=" + key.value() + "]";
    }
}
