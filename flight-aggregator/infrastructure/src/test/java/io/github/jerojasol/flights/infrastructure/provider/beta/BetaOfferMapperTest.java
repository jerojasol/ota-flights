package io.github.jerojasol.flights.infrastructure.provider.beta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jerojasol.flights.domain.model.Airport;
import io.github.jerojasol.flights.domain.model.FlightNumber;
import io.github.jerojasol.flights.domain.model.Offer;
import io.github.jerojasol.flights.domain.model.Price;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.ProviderResponse;
import io.github.jerojasol.flights.domain.model.Segment;
import io.github.jerojasol.flights.infrastructure.provider.ProviderContractViolation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

class BetaOfferMapperTest {

    private final BetaOfferMapper mapper = new BetaOfferMapper();

    @Test
    void should_map_two_offers_with_instants_from_local_time_and_zone_when_reading_exported_fixture() {
        ProviderResponse response = map(fixture("two-itineraries.xml"));

        assertThat(response.unmappable()).isZero();
        assertThat(response.offers()).hasSize(2);
        Offer direct = response.offers().get(0);
        assertThat(direct.provider()).isEqualTo(Provider.BETA);
        assertThat(direct.basePrice()).isEqualTo(Price.of("364.88", "EUR"));
        assertThat(direct.segments()).containsExactly(new Segment(FlightNumber.of("KL1898"),
                Airport.of("MAD"), Instant.parse("2026-11-20T16:40:00Z"),
                Airport.of("JFK"), Instant.parse("2026-11-20T21:15:00Z")));
    }

    @Test
    void should_sort_legs_by_sequence_when_document_lists_them_out_of_order() {
        ProviderResponse response = map(fixture("two-itineraries.xml"));

        Offer threeLegs = response.offers().get(1);
        assertThat(threeLegs.basePrice()).isEqualTo(Price.of("1422.83", "EUR"));
        assertThat(threeLegs.segments()).extracting(Segment::flightNumber)
                .containsExactly(FlightNumber.of("BA4451"), FlightNumber.of("DL3942"), FlightNumber.of("AA9646"));
        assertThat(threeLegs.segments()).extracting(Segment::departureAirport)
                .containsExactly(Airport.of("MAD"), Airport.of("AMS"), Airport.of("FRA"));
        assertThat(threeLegs.segments().getFirst().departureAt()).isEqualTo(Instant.parse("2026-11-20T13:05:00Z"));
    }

    @Test
    void should_normalise_flight_number_when_reading_contract_example() {
        ProviderResponse response = map(fixture("contract-example.xml"));

        assertThat(response.offers()).hasSize(2);
        Offer connecting = response.offers().get(1);
        assertThat(connecting.basePrice()).isEqualTo(Price.of("455.40", "EUR"));
        assertThat(connecting.segments()).extracting(Segment::flightNumber)
                .containsExactly(FlightNumber.of("IB3166"), FlightNumber.of("BA117"));
        assertThat(connecting.segments().getFirst().departureAt()).isEqualTo(Instant.parse("2026-11-20T06:00:00Z"));
        assertThat(connecting.segments().get(1).arrivalAt()).isEqualTo(Instant.parse("2026-11-20T18:55:00Z"));
    }

    @Test
    void should_return_no_offers_when_itineraries_are_empty() {
        ProviderResponse response = map(fixture("empty.xml"));

        assertThat(response.offers()).isEmpty();
        assertThat(response.unmappable()).isZero();
    }

    @Test
    void should_skip_and_count_itinerary_when_a_segment_is_a_dead_leg() {
        ProviderResponse response = map(fixture("dead-legs.xml"));

        assertThat(response.unmappable()).isEqualTo(1);
        assertThat(response.offers()).singleElement()
                .satisfies(offer -> assertThat(offer.basePrice()).isEqualTo(Price.of("519.00", "EUR")));
    }

    @Test
    void should_skip_and_count_each_malformed_itinerary_without_failing_the_response() {
        ProviderResponse response = map(fixture("malformed-entries.xml"));

        assertThat(response.unmappable()).isEqualTo(3);
        assertThat(response.offers()).singleElement()
                .satisfies(offer -> assertThat(offer.basePrice()).isEqualTo(Price.of("519.00", "EUR")));
    }

    @Test
    void should_skip_and_count_itinerary_when_leg_sequence_has_a_gap() {
        ProviderResponse response = map("""
                <SearchResponse xmlns="urn:beta-gds:search:v2"><Itineraries count="1"><Itinerary ref="BET-gap">
                  <Pricing><Total amount="100.00" currency="EUR"/></Pricing>
                  <Legs>
                    <Leg sequence="1"><Flight><Airline code="IB"/><Number>6251</Number></Flight>
                      <Departure><Airport code="MAD"/><LocalDateTime>2026-11-20T08:35:00</LocalDateTime><TimeZone>Europe/Madrid</TimeZone></Departure>
                      <Arrival><Airport code="LHR"/><LocalDateTime>2026-11-20T10:00:00</LocalDateTime><TimeZone>Europe/London</TimeZone></Arrival></Leg>
                    <Leg sequence="3"><Flight><Airline code="BA"/><Number>117</Number></Flight>
                      <Departure><Airport code="LHR"/><LocalDateTime>2026-11-20T12:00:00</LocalDateTime><TimeZone>Europe/London</TimeZone></Departure>
                      <Arrival><Airport code="JFK"/><LocalDateTime>2026-11-20T15:00:00</LocalDateTime><TimeZone>America/New_York</TimeZone></Arrival></Leg>
                  </Legs></Itinerary></Itineraries></SearchResponse>
                """);

        assertThat(response.offers()).isEmpty();
        assertThat(response.unmappable()).isEqualTo(1);
    }

    @Test
    void should_fail_the_whole_response_when_the_root_has_no_itineraries_element() {
        assertThatThrownBy(() -> map("<SearchResponse xmlns=\"urn:beta-gds:search:v2\"><Meta/></SearchResponse>"))
                .isInstanceOf(ProviderContractViolation.class)
                .hasMessageContaining("no Itineraries element");
    }

    @Test
    void should_skip_and_count_itinerary_when_local_time_falls_in_a_daylight_saving_gap() {
        // 2026-03-29 02:00 → 03:00 in Europe/Madrid: 02:30 never happens.
        ProviderResponse response = map(singleLeg("2026-03-29T02:30:00", "Europe/Madrid", "2026-03-29T05:00:00"));

        assertThat(response.offers()).isEmpty();
        assertThat(response.unmappable()).isEqualTo(1);
    }

    @Test
    void should_use_earlier_offset_when_local_time_falls_in_the_autumn_overlap() {
        // 2026-10-25 03:00 → 02:00 in Europe/Madrid: 02:30 happens at +02:00 and again at +01:00.
        ProviderResponse response = map(singleLeg("2026-10-25T02:30:00", "Europe/Madrid", "2026-10-25T05:00:00"));

        assertThat(response.offers()).singleElement().satisfies(offer -> assertThat(
                offer.segments().getFirst().departureAt()).isEqualTo(Instant.parse("2026-10-25T00:30:00Z")));
    }

    /** One itinerary, one leg MAD → JFK; the arrival is in America/New_York. */
    private static String singleLeg(String departureLocal, String departureZone, String arrivalLocal) {
        return """
                <SearchResponse xmlns="urn:beta-gds:search:v2"><Itineraries count="1"><Itinerary ref="BET-dst">
                  <Pricing><Total amount="100.00" currency="EUR"/></Pricing>
                  <Legs><Leg sequence="1"><Flight><Airline code="IB"/><Number>6251</Number></Flight>
                    <Departure><Airport code="MAD"/><LocalDateTime>%s</LocalDateTime><TimeZone>%s</TimeZone></Departure>
                    <Arrival><Airport code="JFK"/><LocalDateTime>%s</LocalDateTime><TimeZone>America/New_York</TimeZone></Arrival>
                  </Leg></Legs></Itinerary></Itineraries></SearchResponse>
                """.formatted(departureLocal, departureZone, arrivalLocal);
    }

    private ProviderResponse map(String xml) {
        return mapper.map(BetaResponseParser.parse(xml.getBytes(StandardCharsets.UTF_8)));
    }

    static String fixture(String name) {
        try (InputStream in = BetaOfferMapperTest.class.getResourceAsStream("/fixtures/beta/" + name)) {
            assertThat(in).as("fixture " + name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
