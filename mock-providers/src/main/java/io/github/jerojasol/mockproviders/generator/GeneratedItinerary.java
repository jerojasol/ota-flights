package io.github.jerojasol.mockproviders.generator;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generator-internal representation; controllers translate it into their own provider format.
 * {@code invalid} is null for valid itineraries.
 */
public record GeneratedItinerary(
        String canonicalId,
        List<GeneratedSegment> segments,
        long priceCents,
        String currency,
        Set<ProviderId> offeredBy,
        InvalidKind invalid) {

    private static final DateTimeFormatter MINUTE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'").withZone(ZoneOffset.UTC);

    public GeneratedItinerary {
        segments = List.copyOf(segments);
        offeredBy = Set.copyOf(offeredBy);
    }

    /** {@code carrier+flightNo@departureUtcMinute>from-to}, segments joined by {@code |}. */
    public static String canonicalIdOf(List<GeneratedSegment> segments) {
        return segments.stream()
                .map(s -> s.carrier() + s.flightNumber() + "@" + MINUTE.format(s.departureUtc())
                        + ">" + s.from() + "-" + s.to())
                .collect(Collectors.joining("|"));
    }
}
