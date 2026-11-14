package io.github.jerojasol.mockproviders.alpha;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import io.github.jerojasol.mockproviders.generator.GeneratedItinerary;
import io.github.jerojasol.mockproviders.generator.GeneratedSegment;

final class AlphaMapper {

    private AlphaMapper() {
    }

    static AlphaItinerary toAlpha(GeneratedItinerary itinerary) {
        return new AlphaItinerary(
                id(itinerary.canonicalId()),
                BigDecimal.valueOf(itinerary.priceCents(), 2),
                itinerary.currency(),
                itinerary.segments().stream().map(AlphaMapper::toAlpha).toList());
    }

    private static AlphaSegment toAlpha(GeneratedSegment segment) {
        return new AlphaSegment(segment.carrier(), Integer.toString(segment.flightNumber()), segment.from(),
                segment.to(), segment.departureUtc(), segment.arrivalUtc());
    }

    static String id(String canonicalId) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonicalId.getBytes(StandardCharsets.UTF_8));
            return "ALP-" + HexFormat.of().formatHex(hash).substring(0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
