package io.github.jerojasol.flights.infrastructure.provider.alpha;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jerojasol.flights.domain.model.Airport;
import io.github.jerojasol.flights.domain.model.FlightNumber;
import io.github.jerojasol.flights.domain.model.Offer;
import io.github.jerojasol.flights.domain.model.Price;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.ProviderResponse;
import io.github.jerojasol.flights.domain.model.Segment;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

class AlphaOfferMapperTest {

    private final AlphaOfferMapper mapper = new AlphaOfferMapper();

    @Test
    void should_map_two_offers_with_utc_instants_prices_and_segment_order_when_reading_exported_fixture() {
        ProviderResponse response = map(fixture("two-itineraries.json"));

        assertThat(response.unmappable()).isZero();
        assertThat(response.offers()).hasSize(2);
        Offer direct = response.offers().get(0);
        assertThat(direct.provider()).isEqualTo(Provider.ALPHA);
        assertThat(direct.basePrice()).isEqualTo(Price.of("367.62", "EUR"));
        assertThat(direct.segments()).containsExactly(new Segment(FlightNumber.of("KL1898"),
                Airport.of("MAD"), Instant.parse("2026-11-20T16:40:00Z"),
                Airport.of("JFK"), Instant.parse("2026-11-20T21:15:00Z")));
        Offer threeLegs = response.offers().get(1);
        assertThat(threeLegs.basePrice()).isEqualTo(Price.of("1465.90", "EUR"));
        assertThat(threeLegs.segments()).extracting(Segment::flightNumber)
                .containsExactly(FlightNumber.of("BA4451"), FlightNumber.of("DL3942"), FlightNumber.of("AA9646"));
        assertThat(threeLegs.segments()).extracting(Segment::departureAirport)
                .containsExactly(Airport.of("MAD"), Airport.of("AMS"), Airport.of("FRA"));
        assertThat(threeLegs.segments().getLast().arrivalAt()).isEqualTo(Instant.parse("2026-11-21T11:05:00Z"));
    }

    @Test
    void should_normalise_leading_zero_flight_number_when_reading_contract_example() {
        ProviderResponse response = map(fixture("contract-example.json"));

        assertThat(response.offers()).hasSize(2);
        Offer connecting = response.offers().get(1);
        assertThat(connecting.basePrice()).isEqualTo(Price.of("438.90", "EUR"));
        assertThat(connecting.segments()).extracting(Segment::flightNumber)
                .containsExactly(FlightNumber.of("IB3166"), FlightNumber.of("BA117"));
        assertThat(connecting.segments().get(1).flightNumber().number()).isEqualTo(117);
        assertThat(connecting.segments().get(1).departureAt()).isEqualTo(Instant.parse("2026-11-20T10:40:00Z"));
    }

    @Test
    void should_return_no_offers_when_response_is_empty() {
        ProviderResponse response = map(fixture("empty.json"));

        assertThat(response.offers()).isEmpty();
        assertThat(response.unmappable()).isZero();
    }

    @Test
    void should_skip_and_count_itineraries_when_a_segment_is_a_dead_leg() {
        ProviderResponse response = map(fixture("dead-legs.json"));

        assertThat(response.unmappable()).isEqualTo(2);
        assertThat(response.offers()).singleElement()
                .satisfies(offer -> assertThat(offer.basePrice()).isEqualTo(Price.of("512.30", "EUR")));
    }

    @Test
    void should_skip_and_count_null_entries_without_failing_the_response() {
        ProviderResponse response = map(fixture("null-entries.json"));

        assertThat(response.unmappable()).isEqualTo(2);
        assertThat(response.offers()).singleElement()
                .satisfies(offer -> assertThat(offer.basePrice()).isEqualTo(Price.of("512.30", "EUR")));
    }

    @Test
    void should_skip_and_count_itineraries_with_unreadable_price_without_failing_the_response() {
        ProviderResponse response = map(fixture("malformed-values.json"));

        assertThat(response.unmappable()).isEqualTo(2);
        assertThat(response.offers()).singleElement()
                .satisfies(offer -> assertThat(offer.basePrice()).isEqualTo(Price.of("438.90", "EUR")));
    }

    @Test
    void should_keep_exact_decimal_text_when_price_is_a_json_number() {
        ProviderResponse response = map("""
                [{"id":"ALP-exact","price":1465.90,"currency":"EUR","segments":[
                  {"carrier":"IB","flightNo":"6251","from":"MAD","to":"JFK","dep":"2026-11-20T07:35:00Z","arr":"2026-11-20T16:10:00Z"}]}]
                """);

        assertThat(response.offers()).singleElement()
                .satisfies(offer -> assertThat(offer.basePrice().amount()).isEqualByComparingTo("1465.90"));
    }

    @Test
    void should_skip_and_count_itinerary_when_a_timestamp_is_malformed() {
        String body = """
                [{"id":"ALP-bad","price":10.00,"currency":"EUR","segments":[
                  {"carrier":"IB","flightNo":"6251","from":"MAD","to":"JFK","dep":"2026-11-20 07:35","arr":"2026-11-20T16:10:00Z"}]}]
                """;

        ProviderResponse response = map(body);

        assertThat(response.offers()).isEmpty();
        assertThat(response.unmappable()).isEqualTo(1);
    }

    private ProviderResponse map(String json) {
        return mapper.map(AlphaResponseParser.parse(json.getBytes(StandardCharsets.UTF_8)));
    }

    static String fixture(String name) {
        try (InputStream in = AlphaOfferMapperTest.class.getResourceAsStream("/fixtures/alpha/" + name)) {
            assertThat(in).as("fixture " + name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
