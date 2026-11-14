package io.github.jerojasol.flights.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Deduplication identity: SHA-256 of the canonical form of the segments (flight numbers,
 * departure instants at minute precision, airports). Price, provider and arrival times
 * are deliberately excluded.
 */
public final class ItineraryKey {

    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");
    private static final DateTimeFormatter MINUTES =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'").withZone(ZoneOffset.UTC);

    private final String value;

    private ItineraryKey(String value) {
        if (value == null || !SHA_256_HEX.matcher(value).matches()) {
            throw new InvalidItineraryKeyException("Itinerary key must be 64 lowercase hex characters: " + value);
        }
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ItineraryKey that && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "ItineraryKey[" + value + "]";
    }

    public static ItineraryKey of(List<Segment> segments) {
        return new ItineraryKey(sha256Hex(canonical(segments)));
    }

    /** Rebuilds a key read from storage. Does NOT validate the format; for the persistence adapter only. */
    public static ItineraryKey fromStored(String value) {
        return new ItineraryKey(value);
    }

    /** Canonical form, e.g. {@code IB6251@2026-11-20T07:35Z>MAD-JFK|IB4120@2026-11-20T14:10Z>JFK-MIA}. */
    public static String canonical(List<Segment> segments) {
        if (segments == null || segments.isEmpty()) {
            throw new InvalidItineraryKeyException("Itinerary key requires at least one segment");
        }
        return segments.stream().map(ItineraryKey::canonical).collect(Collectors.joining("|"));
    }

    private static String canonical(Segment segment) {
        FlightNumber flight = segment.flightNumber();
        Instant departure = segment.departureAt().truncatedTo(ChronoUnit.MINUTES);
        return flight.carrier().code() + flight.number()
                + "@" + MINUTES.format(departure)
                + ">" + segment.departureAirport().code() + "-" + segment.arrivalAirport().code();
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
