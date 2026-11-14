package io.github.jerojasol.flights.domain.model;

import static io.github.jerojasol.flights.domain.Fixtures.IB4120;
import static io.github.jerojasol.flights.domain.Fixtures.IB6251;
import static io.github.jerojasol.flights.domain.Fixtures.LHR;
import static io.github.jerojasol.flights.domain.Fixtures.JFK;
import static io.github.jerojasol.flights.domain.Fixtures.MAD;
import static io.github.jerojasol.flights.domain.Fixtures.MIA;
import static io.github.jerojasol.flights.domain.Fixtures.chain;
import static io.github.jerojasol.flights.domain.Fixtures.instant;
import static io.github.jerojasol.flights.domain.Fixtures.offer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class OfferValidatorTest {

    private static final String START = "2026-11-20T07:35:00Z";
    private final OfferValidator validator = new OfferValidator();

    private OfferValidation validate(List<Segment> segments) {
        return validator.validate(offer(Provider.ALPHA, "100.00", segments));
    }

    private void assertRejectedWith(List<Segment> segments, RejectionReason reason) {
        assertThat(validate(segments)).isInstanceOfSatisfying(OfferValidation.Rejected.class,
                rejected -> assertThat(rejected.reason()).isEqualTo(reason));
    }

    @Test
    void should_return_valid_when_offer_has_one_segment() {
        assertThat(validate(chain(START, 480))).isInstanceOf(OfferValidation.Valid.class);
    }

    @Test
    void should_return_valid_when_offer_has_two_segments() {
        assertThat(validate(chain(START, 480, 90, 120))).isInstanceOf(OfferValidation.Valid.class);
    }

    @Test
    void should_return_offer_unchanged_when_valid() {
        Offer offer = offer(Provider.BETA, "10.00", chain(START, 480));

        assertThat(validator.validate(offer).offer()).isSameAs(offer);
    }

    @Test
    void should_reject_when_offer_has_five_segments() { // I2
        assertRejectedWith(chain(START, 60, 60, 60, 60, 60, 60, 60, 60, 60), RejectionReason.TOO_MANY_SEGMENTS);
    }

    @Test
    void should_reject_when_origin_equals_destination() { // I4
        assertRejectedWith(roundTrip(), RejectionReason.ORIGIN_EQUALS_DESTINATION);
    }

    @Test
    void should_reject_when_connection_airports_do_not_match() { // I5
        assertRejectedWith(mismatch(),
                RejectionReason.AIRPORT_MISMATCH_AT_CONNECTION);
    }

    @Test
    void should_reject_when_next_segment_departs_before_previous_arrival() { // I6
        assertRejectedWith(chain(START, 120, -30, 120), RejectionReason.CONNECTION_OVERLAP);
    }

    @Test
    void should_reject_when_connection_is_shorter_than_45_minutes() { // I7
        assertRejectedWith(chain(START, 120, 44, 120), RejectionReason.CONNECTION_TOO_SHORT);
        assertThat(validate(chain(START, 120, 45, 120))).isInstanceOf(OfferValidation.Valid.class);
    }

    @Test
    void should_reject_when_connection_is_longer_than_24_hours() { // I8
        assertRejectedWith(chain(START, 120, 24 * 60 + 1, 120), RejectionReason.CONNECTION_TOO_LONG);
        assertThat(validate(chain(START, 120, 24 * 60, 120))).isInstanceOf(OfferValidation.Valid.class);
    }

    @Test
    void should_reject_when_total_duration_exceeds_72_hours() { // I9
        assertRejectedWith(chain(START, 20 * 60, 24 * 60, 20 * 60, 5 * 60, 3 * 60 + 1),
                RejectionReason.TOTAL_DURATION_EXCEEDED);
        assertThat(validate(chain(START, 20 * 60, 24 * 60, 20 * 60, 5 * 60, 3 * 60)))
                .isInstanceOf(OfferValidation.Valid.class);
    }

    @Test
    void should_report_origin_equals_destination_when_it_and_airport_mismatch_both_fail() { // I4 before I5
        List<Segment> both = List.of(
                new Segment(IB6251, MAD, instant(START), JFK, instant("2026-11-20T15:35:00Z")),
                new Segment(IB4120, LHR, instant("2026-11-20T18:00:00Z"), MAD, instant("2026-11-21T02:00:00Z")));

        assertRejectedWith(both, RejectionReason.ORIGIN_EQUALS_DESTINATION);
    }

    @Test
    void should_report_airport_mismatch_when_it_and_overlap_both_fail() { // I5 before I6
        List<Segment> both = List.of(
                new Segment(IB6251, MAD, instant(START), JFK, instant("2026-11-20T15:35:00Z")),
                new Segment(IB4120, LHR, instant("2026-11-20T14:00:00Z"), MIA, instant("2026-11-20T20:00:00Z")));

        assertRejectedWith(both, RejectionReason.AIRPORT_MISMATCH_AT_CONNECTION);
    }

    @Test
    void should_report_too_many_segments_when_it_and_other_rules_fail() { // I2 before I4
        assertRejectedWith(chain(START, 60, 10, 60, 10, 60, 10, 60, 10, 60), RejectionReason.TOO_MANY_SEGMENTS);
    }

    @Test
    void should_report_first_failing_pair_when_several_connections_fail() { // I7 before I8
        assertRejectedWith(chain(START, 120, 44, 120, 24 * 60 + 1, 120), RejectionReason.CONNECTION_TOO_SHORT);
        assertRejectedWith(chain(START, 120, 24 * 60 + 1, 120, 44, 120), RejectionReason.CONNECTION_TOO_LONG);
    }

    @Test
    void should_report_connection_reason_when_total_duration_also_exceeds_limit() { // I8 before I9
        assertRejectedWith(chain(START, 20 * 60, 24 * 60 + 1, 20 * 60, 5 * 60, 3 * 60 + 1),
                RejectionReason.CONNECTION_TOO_LONG);
    }

    @Test
    void should_count_rejections_per_reason_when_validating_many_offers() {
        List<Offer> offers = List.of(
                offer(Provider.ALPHA, "1.00", chain(START, 480)),
                offer(Provider.BETA, "2.00", chain(START, 480, 90, 120)),
                offer(Provider.GAMMA, "3.00", roundTrip()),
                offer(Provider.ALPHA, "4.00", roundTrip()),
                offer(Provider.BETA, "5.00", chain(START, 120, 44, 120)));

        ValidationSummary summary = validator.validateAll(offers);

        assertThat(summary.valid()).containsExactly(offers.get(0), offers.get(1));
        assertThat(summary.rejectedByReason()).isEqualTo(Map.of(
                RejectionReason.ORIGIN_EQUALS_DESTINATION, 2,
                RejectionReason.CONNECTION_TOO_SHORT, 1));
        assertThat(summary.rejected()).isEqualTo(3);
    }

    @Test
    void should_accept_immutable_empty_map_when_summary_is_built_directly() {
        ValidationSummary summary = new ValidationSummary(List.of(), Map.of());

        assertThat(summary.rejected()).isZero();
    }

    @Test
    void should_return_empty_summary_when_there_are_no_offers() {
        ValidationSummary summary = validator.validateAll(List.of());

        assertThat(summary.valid()).isEmpty();
        assertThat(summary.rejectedByReason()).isEmpty();
    }

    @Test
    void should_report_segment_level_reason_through_the_segment_exception_when_adapter_builds_dead_leg() { // S1, S2, S3
        Segment ok = chain(START, 480).getFirst();

        assertThatThrownBy(
                        () -> new Segment(IB6251, MAD, ok.departureAt(), MAD, ok.arrivalAt()))
                .isInstanceOfSatisfying(InvalidSegmentException.class,
                        e -> assertThat(e.reason()).contains(RejectionReason.DEAD_LEG_SAME_AIRPORT));
        assertThatThrownBy(
                        () -> new Segment(IB6251, MAD, ok.departureAt(), JFK, ok.departureAt()))
                .isInstanceOfSatisfying(InvalidSegmentException.class,
                        e -> assertThat(e.reason()).contains(RejectionReason.DEAD_LEG_NON_POSITIVE_DURATION));
        assertThatThrownBy(
                        () -> new Segment(IB6251, MAD, ok.departureAt(), JFK, ok.departureAt().plusSeconds(20 * 3600 + 1)))
                .isInstanceOfSatisfying(InvalidSegmentException.class,
                        e -> assertThat(e.reason()).contains(RejectionReason.SEGMENT_DURATION_EXCEEDED));
    }

    private static List<Segment> roundTrip() {
        return List.of(
                new Segment(IB6251, MAD, instant(START), JFK, instant("2026-11-20T15:35:00Z")),
                new Segment(IB4120, JFK, instant("2026-11-20T18:00:00Z"), MAD, instant("2026-11-21T02:00:00Z")));
    }

    private static List<Segment> mismatch() {
        return List.of(
                new Segment(IB6251, MAD, instant(START), JFK, instant("2026-11-20T15:35:00Z")),
                new Segment(IB4120, LHR, instant("2026-11-20T18:00:00Z"), MIA, instant("2026-11-21T02:00:00Z")));
    }
}
