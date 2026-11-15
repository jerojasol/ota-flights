package io.github.jerojasol.mockproviders.gamma;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.stream.Collectors;

import io.github.jerojasol.mockproviders.generator.GeneratedItinerary;
import io.github.jerojasol.mockproviders.generator.GeneratedSegment;

final class GammaLineFormatter {

    static final String HEADER = "ref,price_cents,currency,segments";
    static final String TRAILER = "#END";

    private static final DateTimeFormatter COMPACT_UTC =
            DateTimeFormatter.ofPattern("uuuuMMddHHmm").withZone(ZoneOffset.UTC);

    private GammaLineFormatter() {
    }

    static String format(GeneratedItinerary itinerary) {
        String segments = itinerary.segments().stream()
                .map(GammaLineFormatter::format)
                .collect(Collectors.joining("|"));
        return ref(itinerary.canonicalId()) + "," + itinerary.priceCents() + "," + itinerary.currency() + ","
                + segments;
    }

    private static String format(GeneratedSegment segment) {
        return String.join(";",
                segment.carrier(),
                "%04d".formatted(segment.flightNumber()),
                segment.from(),
                segment.to(),
                COMPACT_UTC.format(segment.departureUtc()),
                COMPACT_UTC.format(segment.arrivalUtc()));
    }

    static String ref(String canonicalId) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonicalId.getBytes(StandardCharsets.UTF_8));
            return "GAM-" + HexFormat.of().formatHex(hash).substring(0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
