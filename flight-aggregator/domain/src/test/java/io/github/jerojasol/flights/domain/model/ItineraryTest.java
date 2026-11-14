package io.github.jerojasol.flights.domain.model;

import static io.github.jerojasol.flights.domain.Fixtures.BA;
import static io.github.jerojasol.flights.domain.Fixtures.IB;
import static io.github.jerojasol.flights.domain.Fixtures.IB4120;
import static io.github.jerojasol.flights.domain.Fixtures.IB6251;
import static io.github.jerojasol.flights.domain.Fixtures.JFK;
import static io.github.jerojasol.flights.domain.Fixtures.LHR;
import static io.github.jerojasol.flights.domain.Fixtures.MAD;
import static io.github.jerojasol.flights.domain.Fixtures.MIA;
import static io.github.jerojasol.flights.domain.Fixtures.chain;
import static io.github.jerojasol.flights.domain.Fixtures.instant;
import static io.github.jerojasol.flights.domain.Fixtures.offer;
import static io.github.jerojasol.flights.domain.Fixtures.price;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import org.junit.jupiter.api.Test;

class ItineraryTest {

    private static final String START = "2026-11-20T07:35:00Z";
    private static final Instant NOW = instant("2026-11-14T10:00:00Z");
    private static final MarkupPolicy FIVE_PERCENT = new PercentageMarkupPolicy(new BigDecimal("5"));

    private static Itinerary itinerary(List<Segment> segments) {
        return Itinerary.from(offer(Provider.ALPHA, "100.00", segments), FIVE_PERCENT, NOW);
    }

    private static void assertRejected(List<Segment> segments, RejectionReason reason) {
        assertThatThrownBy(() -> itinerary(segments))
                .isInstanceOfSatisfying(InvalidItineraryException.class,
                        e -> assertThat(e.reason()).contains(reason));
    }

    @Test
    void should_create_itinerary_when_offer_is_valid() {
        Itinerary itinerary = itinerary(chain(START, 480));

        assertThat(itinerary.source()).isEqualTo(Provider.ALPHA);
        assertThat(itinerary.ingestedAt()).isEqualTo(NOW);
        assertThat(itinerary.basePrice()).isEqualTo(price("100.00"));
    }

    // Offer already rejects empty segments, so I1 is reached through rehydrate.
    @Test
    void should_reject_itinerary_when_there_are_no_segments() { // I1
        assertThatThrownBy(() -> Itinerary.rehydrate(ItineraryKey.of(chain(START, 480)), List.of(),
                price("1"), price("1"), Provider.ALPHA, NOW))
                .isInstanceOfSatisfying(InvalidItineraryException.class,
                        e -> assertThat(e.reason()).contains(RejectionReason.NO_SEGMENTS));
    }

    @Test
    void should_reject_rehydration_when_segments_are_null() { // I1
        assertThatThrownBy(() -> Itinerary.rehydrate(ItineraryKey.fromStored("a".repeat(64)), null, price("1"),
                price("1"), Provider.ALPHA, NOW))
                .isInstanceOfSatisfying(InvalidItineraryException.class,
                        e -> assertThat(e.reason()).contains(RejectionReason.NO_SEGMENTS));
    }

    @Test
    void should_reject_itinerary_when_offer_markup_or_ingestion_time_is_null() {
        Offer offer = offer(Provider.ALPHA, "10.00", chain(START, 480));

        assertThatThrownBy(() -> Itinerary.from(null, FIVE_PERCENT, NOW)).isInstanceOf(InvalidItineraryException.class);
        assertThatThrownBy(() -> Itinerary.from(offer, null, NOW)).isInstanceOf(InvalidItineraryException.class);
        assertThatThrownBy(() -> Itinerary.from(offer, FIVE_PERCENT, null)).isInstanceOf(InvalidItineraryException.class);
    }

    @Test
    void should_accept_itinerary_when_it_has_four_segments() { // I2
        assertThat(itinerary(chain(START, 60, 60, 60, 60, 60, 60, 60)).connections()).isEqualTo(3);
    }

    @Test
    void should_reject_itinerary_when_it_has_five_segments() { // I2
        assertRejected(chain(START, 60, 60, 60, 60, 60, 60, 60, 60, 60), RejectionReason.TOO_MANY_SEGMENTS);
    }

    @Test
    void should_derive_origin_and_destination_when_it_has_several_segments() { // I3
        Itinerary itinerary = itinerary(chain(START, 120, 90, 120));

        assertThat(itinerary.origin()).isEqualTo(MAD);
        assertThat(itinerary.destination()).isEqualTo(LHR);
    }

    @Test
    void should_reject_itinerary_when_origin_equals_destination() { // I4
        List<Segment> roundTrip = List.of(
                new Segment(IB6251, MAD, instant(START), JFK, instant("2026-11-20T15:35:00Z")),
                new Segment(IB4120, JFK, instant("2026-11-20T18:00:00Z"), MAD, instant("2026-11-21T02:00:00Z")));

        assertRejected(roundTrip, RejectionReason.ORIGIN_EQUALS_DESTINATION);
    }

    @Test
    void should_reject_itinerary_when_connection_airports_do_not_match() { // I5
        List<Segment> mismatch = List.of(
                new Segment(IB6251, MAD, instant(START), JFK, instant("2026-11-20T15:35:00Z")),
                new Segment(IB4120, LHR, instant("2026-11-20T18:00:00Z"), MIA, instant("2026-11-21T02:00:00Z")));

        assertRejected(mismatch, RejectionReason.AIRPORT_MISMATCH_AT_CONNECTION);
    }

    @Test
    void should_reject_itinerary_when_next_segment_departs_before_previous_arrival() { // I6
        assertRejected(chain(START, 120, -30, 120), RejectionReason.CONNECTION_OVERLAP);
    }

    @Test
    void should_reject_itinerary_when_next_segment_departs_exactly_at_previous_arrival() { // I6
        assertRejected(chain(START, 120, 0, 120), RejectionReason.CONNECTION_OVERLAP);
    }

    @Test
    void should_reject_itinerary_when_connection_is_44_minutes() { // I7
        assertRejected(chain(START, 120, 44, 120), RejectionReason.CONNECTION_TOO_SHORT);
    }

    @Test
    void should_accept_itinerary_when_connection_is_45_minutes() { // I7
        assertThat(itinerary(chain(START, 120, 45, 120)).connections()).isEqualTo(1);
    }

    @Test
    void should_accept_itinerary_when_connection_is_24_hours() { // I8
        assertThat(itinerary(chain(START, 120, 24 * 60, 120)).connections()).isEqualTo(1);
    }

    @Test
    void should_reject_itinerary_when_connection_is_24_hours_and_one_minute() { // I8
        assertRejected(chain(START, 120, 24 * 60 + 1, 120), RejectionReason.CONNECTION_TOO_LONG);
    }

    @Test
    void should_accept_itinerary_when_total_duration_is_72_hours() { // I9
        // 20h flight + 24h connection + 20h flight + 5h connection + 3h flight
        Itinerary itinerary = itinerary(chain(START, 20 * 60, 24 * 60, 20 * 60, 5 * 60, 3 * 60));

        assertThat(itinerary.totalDuration()).isEqualTo(Duration.ofHours(72));
    }

    @Test
    void should_reject_itinerary_when_total_duration_is_72_hours_and_one_minute() { // I9
        assertRejected(chain(START, 20 * 60, 24 * 60, 20 * 60, 5 * 60, 3 * 60 + 1),
                RejectionReason.TOTAL_DURATION_EXCEEDED);
    }

    @Test
    void should_derive_final_price_when_markup_policy_is_applied() { // I10
        Itinerary itinerary = Itinerary.from(offer(Provider.BETA, "99.99", chain(START, 480)), FIVE_PERCENT, NOW);

        assertThat(itinerary.finalPrice()).isEqualTo(FIVE_PERCENT.apply(price("99.99"))).isEqualTo(price("104.99"));
    }

    @Test
    void should_reject_itinerary_when_markup_changes_currency() { // I11
        Currency usd = Currency.getInstance("USD");
        MarkupPolicy wrongCurrency = base -> new Price(base.amount(), usd);

        assertThatThrownBy(() -> Itinerary.from(offer(Provider.ALPHA, "10.00", chain(START, 480)), wrongCurrency, NOW))
                .isInstanceOfSatisfying(InvalidItineraryException.class, e -> assertThat(e.reason()).isEmpty());
    }

    @Test
    void should_reject_rehydration_when_stored_prices_have_different_currencies() { // I11
        Price dollars = new Price(new BigDecimal("10.00"), Currency.getInstance("USD"));
        List<Segment> segments = chain(START, 480);

        assertThatThrownBy(() -> Itinerary.rehydrate(ItineraryKey.of(segments), segments, price("10.00"), dollars,
                Provider.ALPHA, NOW))
                .isInstanceOfSatisfying(InvalidItineraryException.class, e -> assertThat(e.reason()).isEmpty());
    }

    @Test
    void should_derive_key_when_itinerary_is_built_from_segments() { // I12
        List<Segment> segments = chain(START, 120, 90, 120);

        assertThat(itinerary(segments).key()).isEqualTo(ItineraryKey.of(segments));
    }

    @Test
    void should_expose_connections_and_total_duration_when_it_has_two_segments() {
        Itinerary itinerary = itinerary(chain(START, 120, 90, 180));

        assertThat(itinerary.connections()).isEqualTo(1);
        assertThat(itinerary.totalDuration()).isEqualTo(Duration.ofMinutes(390));
    }

    @Test
    void should_list_distinct_carriers_in_order_when_carriers_repeat() {
        List<Segment> base = chain(START, 120, 90, 120, 90, 120);
        List<Segment> segments = new ArrayList<>();
        segments.add(base.get(0));
        segments.add(withFlight(base.get(1), FlightNumber.of("BA117")));
        segments.add(withFlight(base.get(2), FlightNumber.of("BA118")));

        assertThat(itinerary(segments).carriers()).containsExactly(IB, BA);
    }

    @Test
    void should_be_equal_when_key_is_equal_regardless_of_provider_and_price() {
        List<Segment> segments = chain(START, 480);
        Itinerary alpha = Itinerary.from(offer(Provider.ALPHA, "100.00", segments), FIVE_PERCENT, NOW);
        Itinerary gamma = Itinerary.from(offer(Provider.GAMMA, "90.00", segments), FIVE_PERCENT, NOW);

        assertThat(alpha).isEqualTo(gamma).hasSameHashCodeAs(gamma);
    }

    @Test
    void should_keep_stored_key_and_prices_when_rehydrated_with_valid_structure() {
        List<Segment> segments = chain(START, 480);
        ItineraryKey storedKey = ItineraryKey.fromStored("a".repeat(64));

        Itinerary itinerary = Itinerary.rehydrate(storedKey, segments, price("100.00"), price("110.00"),
                Provider.BETA, NOW);

        assertThat(itinerary.key()).isEqualTo(storedKey);
        assertThat(itinerary.finalPrice()).isEqualTo(price("110.00"));
    }

    @Test
    void should_still_check_structure_when_rehydrated() { // I2
        List<Segment> tooMany = chain(START, 60, 60, 60, 60, 60, 60, 60, 60, 60);

        assertThatThrownBy(() -> Itinerary.rehydrate(ItineraryKey.of(tooMany), tooMany, price("1"), price("1"),
                Provider.ALPHA, NOW)).isInstanceOf(InvalidItineraryException.class);
    }

    @Test
    void should_not_change_when_source_segment_list_is_modified() {
        List<Segment> source = new ArrayList<>(chain(START, 480));
        Itinerary itinerary = itinerary(source);

        source.clear();

        assertThat(itinerary.segments()).hasSize(1);
        assertThatThrownBy(() -> itinerary.segments().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private static Segment withFlight(Segment segment, FlightNumber flight) {
        return new Segment(flight, segment.departureAirport(), segment.departureAt(), segment.arrivalAirport(),
                segment.arrivalAt());
    }
}
