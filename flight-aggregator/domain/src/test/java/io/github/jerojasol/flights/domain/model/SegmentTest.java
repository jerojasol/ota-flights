package io.github.jerojasol.flights.domain.model;

import static io.github.jerojasol.flights.domain.Fixtures.IB;
import static io.github.jerojasol.flights.domain.Fixtures.IB6251;
import static io.github.jerojasol.flights.domain.Fixtures.JFK;
import static io.github.jerojasol.flights.domain.Fixtures.MAD;
import static io.github.jerojasol.flights.domain.Fixtures.instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class SegmentTest {

    private static final Instant DEPARTURE = instant("2026-11-20T07:35:00Z");

    @Test
    void should_create_segment_when_fields_are_valid() {
        Segment segment = new Segment(IB6251, MAD, DEPARTURE, JFK, DEPARTURE.plus(Duration.ofHours(8)));

        assertThat(segment.carrier()).isEqualTo(IB);
        assertThat(segment.flightNumber()).isEqualTo(IB6251);
    }

    @Test
    void should_return_elapsed_time_when_duration_is_requested() {
        Segment segment = new Segment(IB6251, MAD, DEPARTURE, JFK, DEPARTURE.plus(Duration.ofMinutes(500)));

        assertThat(segment.duration()).isEqualTo(Duration.ofMinutes(500));
    }

    @Test
    void should_reject_segment_when_departure_equals_arrival_airport() { // S1
        assertThatThrownBy(() -> new Segment(IB6251, MAD, DEPARTURE, MAD, DEPARTURE.plusSeconds(7200)))
                .isInstanceOf(InvalidSegmentException.class);
    }

    @Test
    void should_reject_segment_when_arrival_equals_departure_time() { // S2
        assertThatThrownBy(() -> new Segment(IB6251, MAD, DEPARTURE, JFK, DEPARTURE))
                .isInstanceOf(InvalidSegmentException.class);
    }

    @Test
    void should_reject_segment_when_arrival_is_before_departure() { // S2
        assertThatThrownBy(() -> new Segment(IB6251, MAD, DEPARTURE, JFK, DEPARTURE.minusSeconds(60)))
                .isInstanceOf(InvalidSegmentException.class);
    }

    @Test
    void should_accept_segment_when_duration_is_exactly_twenty_hours() { // S3
        assertThat(new Segment(IB6251, MAD, DEPARTURE, JFK, DEPARTURE.plus(Duration.ofHours(20))).duration())
                .isEqualTo(Duration.ofHours(20));
    }

    @Test
    void should_reject_segment_when_duration_exceeds_twenty_hours() { // S3
        assertThatThrownBy(() -> new Segment(IB6251, MAD, DEPARTURE, JFK, DEPARTURE.plus(Duration.ofHours(20)).plusSeconds(1)))
                .isInstanceOf(InvalidSegmentException.class);
    }

    @Test
    void should_reject_segment_when_any_field_is_null() {
        assertThatThrownBy(() -> new Segment(null, MAD, DEPARTURE, JFK, DEPARTURE.plusSeconds(3600)))
                .isInstanceOf(InvalidSegmentException.class);
        assertThatThrownBy(() -> new Segment(IB6251, MAD, null, JFK, DEPARTURE.plusSeconds(3600)))
                .isInstanceOf(InvalidSegmentException.class);
        assertThatThrownBy(() -> new Segment(IB6251, MAD, DEPARTURE, JFK, null))
                .isInstanceOf(InvalidSegmentException.class);
        assertThatThrownBy(() -> new Segment(IB6251, null, DEPARTURE, JFK, DEPARTURE.plusSeconds(3600)))
                .isInstanceOf(InvalidSegmentException.class);
        assertThatThrownBy(() -> new Segment(IB6251, MAD, DEPARTURE, null, DEPARTURE.plusSeconds(3600)))
                .isInstanceOf(InvalidSegmentException.class);
    }
}
