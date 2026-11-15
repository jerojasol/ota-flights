package io.github.jerojasol.flights.infrastructure.provider.gamma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jerojasol.flights.domain.aggregation.ProviderResponse;
import io.github.jerojasol.flights.domain.model.Airport;
import io.github.jerojasol.flights.domain.model.FlightNumber;
import io.github.jerojasol.flights.domain.model.Offer;
import io.github.jerojasol.flights.domain.model.Price;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.Segment;
import io.github.jerojasol.flights.infrastructure.provider.ProviderContractViolation;
import io.github.jerojasol.flights.infrastructure.provider.gamma.GammaStreamReader.TruncatedStream;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

class GammaOfferMapperTest {

    private final GammaOfferMapper mapper = new GammaOfferMapper();

    @Test
    void should_map_two_offers_with_utc_instants_and_cents_when_reading_exported_fixture() throws IOException {
        ProviderResponse response = map(fixture("two-itineraries.csv"));

        assertThat(response.unmappable()).isZero();
        assertThat(response.offers()).hasSize(2);
        Offer direct = response.offers().get(0);
        assertThat(direct.provider()).isEqualTo(Provider.GAMMA);
        assertThat(direct.basePrice()).isEqualTo(Price.of("359.13", "EUR"));
        assertThat(direct.segments()).containsExactly(new Segment(FlightNumber.of("KL1898"),
                Airport.of("MAD"), Instant.parse("2026-11-20T16:40:00Z"),
                Airport.of("JFK"), Instant.parse("2026-11-20T21:15:00Z")));
        Offer threeLegs = response.offers().get(1);
        assertThat(threeLegs.basePrice()).isEqualTo(Price.of("1401.02", "EUR"));
        assertThat(threeLegs.segments()).extracting(Segment::flightNumber)
                .containsExactly(FlightNumber.of("BA4451"), FlightNumber.of("DL3942"), FlightNumber.of("AA9646"));
        assertThat(threeLegs.segments().getLast().arrivalAt()).isEqualTo(Instant.parse("2026-11-21T11:05:00Z"));
    }

    @Test
    void should_unpack_two_segments_and_strip_flight_number_padding_when_reading_contract_example() throws IOException {
        ProviderResponse response = map(fixture("contract-example.csv"));

        assertThat(response.unmappable()).isZero();
        assertThat(response.offers()).hasSize(3);
        Offer connecting = response.offers().get(1);
        assertThat(connecting.basePrice()).isEqualTo(Price.of("438.90", "EUR"));
        assertThat(connecting.segments()).extracting(Segment::flightNumber)
                .containsExactly(FlightNumber.of("IB3166"), FlightNumber.of("BA117"));
        assertThat(connecting.segments().get(1).departureAt()).isEqualTo(Instant.parse("2026-11-20T10:40:00Z"));
        assertThat(response.offers().get(2).segments().getFirst().flightNumber()).isEqualTo(FlightNumber.of("UX91"));
    }

    @Test
    void should_return_no_offers_when_export_has_only_header_and_end_marker() throws IOException {
        ProviderResponse response = map(fixture("empty.csv"));

        assertThat(response.offers()).isEmpty();
        assertThat(response.unmappable()).isZero();
    }

    @Test
    void should_skip_and_count_lines_when_a_segment_is_a_dead_leg() throws IOException {
        ProviderResponse response = map(fixture("dead-legs.csv"));

        assertThat(response.unmappable()).isEqualTo(2);
        assertThat(response.offers()).singleElement()
                .satisfies(offer -> assertThat(offer.basePrice()).isEqualTo(Price.of("512.30", "EUR")));
    }

    @Test
    void should_skip_and_count_each_malformed_line_and_ignore_blank_lines_without_failing_the_export()
            throws IOException {
        // Wrong field count, non-numeric cents, bad timestamp, segment with five attributes. The blank line in the
        // fixture is not an itinerary: it is skipped and not counted.
        ProviderResponse response = map(fixture("malformed-entries.csv"));

        assertThat(response.unmappable()).isEqualTo(4);
        assertThat(response.offers()).singleElement()
                .satisfies(offer -> assertThat(offer.basePrice()).isEqualTo(Price.of("438.90", "EUR")));
    }

    @Test
    void should_use_the_currency_minor_units_when_converting_cents() throws IOException {
        // JPY has no minor units: 15000 is ¥15000, not ¥150.
        ProviderResponse response = map(GammaStreamReader.HEADER + "\n"
                + "GAM-jpy,15000,JPY,IB;6251;MAD;JFK;202611200735;202611201610\n#END\n");

        assertThat(response.offers()).singleElement()
                .satisfies(offer -> assertThat(offer.basePrice()).isEqualTo(Price.of("15000", "JPY")));
    }

    @Test
    void should_accept_lowercase_currency_code_like_alpha_and_beta_do() throws IOException {
        ProviderResponse response = map(GammaStreamReader.HEADER + "\n"
                + "GAM-lower,51230,eur,IB;6251;MAD;JFK;202611200735;202611201610\n#END\n");

        assertThat(response.unmappable()).isZero();
        assertThat(response.offers()).singleElement()
                .satisfies(offer -> assertThat(offer.basePrice()).isEqualTo(Price.of("512.30", "EUR")));
    }

    @Test
    void should_skip_and_count_line_when_currency_has_no_minor_units() throws IOException {
        ProviderResponse response = map(GammaStreamReader.HEADER + "\n"
                + "GAM-xxx,15000,XXX,IB;6251;MAD;JFK;202611200735;202611201610\n"
                + "GAM-5e1f,51230,EUR,IB;6251;MAD;JFK;202611200735;202611201610\n#END\n");

        assertThat(response.unmappable()).isEqualTo(1);
        assertThat(response.offers()).singleElement()
                .satisfies(offer -> assertThat(offer.basePrice()).isEqualTo(Price.of("512.30", "EUR")));
    }

    @Test
    void should_fail_as_truncated_when_export_ends_without_end_marker() {
        assertThatThrownBy(() -> map(fixture("truncated-no-end-marker.csv")))
                .isInstanceOf(TruncatedStream.class)
                .hasMessageContaining("#END");
    }

    @Test
    void should_fail_as_truncated_when_body_is_empty() {
        assertThatThrownBy(() -> map("")).isInstanceOf(TruncatedStream.class);
    }

    @Test
    void should_fail_as_contract_violation_when_header_is_wrong() {
        assertThatThrownBy(() -> map("ref,cents,currency,segments\n#END\n"))
                .isInstanceOf(ProviderContractViolation.class)
                .hasMessageContaining("header");
    }

    @Test
    void should_fail_as_contract_violation_when_data_follows_end_marker() {
        assertThatThrownBy(() -> map(GammaStreamReader.HEADER + "\n#END\n"
                + "GAM-late,100,EUR,IB;6251;MAD;JFK;202611200735;202611201610\n"))
                .isInstanceOf(ProviderContractViolation.class)
                .hasMessageContaining("after #END");
    }

    private ProviderResponse map(String csv) throws IOException {
        return mapper.map(new BufferedReader(new StringReader(csv)));
    }

    static String fixture(String name) {
        try (InputStream in = GammaOfferMapperTest.class.getResourceAsStream("/fixtures/gamma/" + name)) {
            assertThat(in).as("fixture " + name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
