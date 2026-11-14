package io.github.jerojasol.mockproviders.gamma;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import io.github.jerojasol.mockproviders.generator.GeneratedItinerary;
import io.github.jerojasol.mockproviders.generator.GeneratedSegment;
import io.github.jerojasol.mockproviders.generator.ProviderId;
import org.junit.jupiter.api.Test;

class GammaLineFormatterTest {

    @Test
    void should_pad_flight_number_to_four_digits_when_flight_is_117() {
        assertThat(field(line(oneSegment(117, 51230)), 1)).isEqualTo("0117");
    }

    @Test
    void should_write_compact_utc_timestamps_when_formatting() {
        String line = line(oneSegment(6251, 51230));

        assertThat(field(line, 4)).isEqualTo("202611200735");
        assertThat(field(line, 5)).isEqualTo("202611201610");
    }

    @Test
    void should_write_price_in_cents_when_price_is_512_30() {
        assertThat(line(oneSegment(6251, 51230)).split(",")[1]).isEqualTo("51230");
    }

    @Test
    void should_write_contract_line_when_itinerary_has_two_segments() {
        List<GeneratedSegment> segments = List.of(
                new GeneratedSegment("IB", 3166, "MAD", "LHR",
                        Instant.parse("2026-11-20T06:00:00Z"), Instant.parse("2026-11-20T08:25:00Z")),
                new GeneratedSegment("BA", 117, "LHR", "JFK",
                        Instant.parse("2026-11-20T10:40:00Z"), Instant.parse("2026-11-20T18:55:00Z")));

        String line = GammaLineFormatter.format(itinerary(segments, 43890));

        assertThat(line).matches("GAM-[0-9a-f]{4},43890,EUR,"
                + "IB;3166;MAD;LHR;202611200600;202611200825\\|BA;0117;LHR;JFK;202611201040;202611201855");
    }

    private static String line(GeneratedItinerary itinerary) {
        return GammaLineFormatter.format(itinerary);
    }

    private static String field(String line, int index) {
        return line.split(",")[3].split(";")[index];
    }

    private static GeneratedItinerary oneSegment(int flightNumber, long priceCents) {
        return itinerary(List.of(new GeneratedSegment("IB", flightNumber, "MAD", "JFK",
                Instant.parse("2026-11-20T07:35:00Z"), Instant.parse("2026-11-20T16:10:00Z"))), priceCents);
    }

    private static GeneratedItinerary itinerary(List<GeneratedSegment> segments, long priceCents) {
        return new GeneratedItinerary(GeneratedItinerary.canonicalIdOf(segments), segments, priceCents, "EUR",
                Set.of(ProviderId.GAMMA), null);
    }
}
