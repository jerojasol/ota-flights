package io.github.jerojasol.mockproviders.beta;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import io.github.jerojasol.mockproviders.beta.BetaXml.Endpoint;
import io.github.jerojasol.mockproviders.beta.BetaXml.Leg;
import io.github.jerojasol.mockproviders.beta.BetaXml.SearchResponse;
import io.github.jerojasol.mockproviders.generator.GeneratedItinerary;
import io.github.jerojasol.mockproviders.generator.GeneratedSegment;
import io.github.jerojasol.mockproviders.generator.ProviderId;
import org.junit.jupiter.api.Test;

class BetaMapperTest {

    @Test
    void should_convert_to_airport_local_time_when_airport_is_known() {
        Endpoint endpoint = BetaMapper.endpoint("MAD", Instant.parse("2026-11-20T07:35:00Z"));

        assertThat(endpoint.localDateTime()).isEqualTo("2026-11-20T08:35:00");
        assertThat(endpoint.timeZone()).isEqualTo("Europe/Madrid");
        assertThat(endpoint.airport().code()).isEqualTo("MAD");
    }

    @Test
    void should_fall_back_to_utc_when_airport_is_unknown() {
        Endpoint endpoint = BetaMapper.endpoint("XXX", Instant.parse("2026-11-20T07:35:00Z"));

        assertThat(endpoint.localDateTime()).isEqualTo("2026-11-20T07:35:00");
        assertThat(endpoint.timeZone()).isEqualTo("UTC");
    }

    @Test
    void should_write_number_without_leading_zeros_when_flight_is_117() {
        Leg leg = BetaMapper.toLeg(1, segment("BA", 117, "LHR", "JFK", "2026-11-20T10:40:00Z", "2026-11-20T18:55:00Z"));

        assertThat(leg.flight().number()).isEqualTo("117");
        assertThat(leg.flight().airline().code()).isEqualTo("BA");
    }

    @Test
    void should_write_amount_with_two_decimals_and_short_ref_when_mapping() {
        SearchResponse response = new BetaMapper(42).toSearchResponse(List.of(twoLegs(45540)), "BET-20261120-0001", "EUR");

        BetaXml.Itinerary itinerary = response.itineraries().itinerary().getFirst();
        assertThat(itinerary.pricing().total().amount()).isEqualTo("455.40");
        assertThat(itinerary.ref()).matches("BET-[0-9a-f]{4}");
        assertThat(response.meta().requestId()).isEqualTo("BET-20261120-0001");
        assertThat(response.itineraries().count()).isEqualTo(1);
    }

    @Test
    void should_emit_some_multi_leg_itinerary_out_of_order_whatever_the_seed() {
        for (long seed = 0; seed < 50; seed++) {
            SearchResponse response = new BetaMapper(seed).toSearchResponse(
                    List.of(twoLegs(45540), twoLegs(39900)), "BET-20261120-0001", "EUR");

            assertThat(response.itineraries().itinerary())
                    .anySatisfy(i -> assertThat(i.legs()).extracting(Leg::sequence).containsExactly(2, 1));
        }
    }

    @Test
    void should_emit_same_leg_order_when_mapping_twice() {
        List<GeneratedItinerary> inventory = List.of(twoLegs(45540));

        assertThat(new BetaMapper(7).toSearchResponse(inventory, "r", "EUR"))
                .isEqualTo(new BetaMapper(7).toSearchResponse(inventory, "r", "EUR"));
    }

    private static GeneratedItinerary twoLegs(long priceCents) {
        List<GeneratedSegment> segments = List.of(
                segment("IB", 3166 + (int) priceCents % 7, "MAD", "LHR", "2026-11-20T06:00:00Z", "2026-11-20T08:25:00Z"),
                segment("BA", 117, "LHR", "JFK", "2026-11-20T10:40:00Z", "2026-11-20T18:55:00Z"));
        return new GeneratedItinerary(GeneratedItinerary.canonicalIdOf(segments), segments, priceCents, "EUR",
                Set.of(ProviderId.BETA), null);
    }

    private static GeneratedSegment segment(String carrier, int number, String from, String to, String dep, String arr) {
        return new GeneratedSegment(carrier, number, from, to, Instant.parse(dep), Instant.parse(arr));
    }
}
