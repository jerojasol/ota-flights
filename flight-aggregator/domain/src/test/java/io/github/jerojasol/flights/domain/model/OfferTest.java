package io.github.jerojasol.flights.domain.model;

import static io.github.jerojasol.flights.domain.Fixtures.IB6251;
import static io.github.jerojasol.flights.domain.Fixtures.JFK;
import static io.github.jerojasol.flights.domain.Fixtures.MAD;
import static io.github.jerojasol.flights.domain.Fixtures.price;
import static io.github.jerojasol.flights.domain.Fixtures.segment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class OfferTest {

    private static final Segment SEGMENT =
            segment(IB6251, MAD, "2026-11-20T07:35:00Z", JFK, "2026-11-20T15:35:00Z");

    @Test
    void should_create_offer_when_fields_are_valid() {
        Offer offer = new Offer(Provider.ALPHA, List.of(SEGMENT), price("512.30"));

        assertThat(offer.provider()).isEqualTo(Provider.ALPHA);
        assertThat(offer.segments()).containsExactly(SEGMENT);
    }

    @Test
    void should_not_change_offer_when_source_list_is_modified_afterwards() {
        List<Segment> source = new ArrayList<>(List.of(SEGMENT));
        Offer offer = new Offer(Provider.BETA, source, price("1.00"));

        source.clear();

        assertThat(offer.segments()).containsExactly(SEGMENT);
        assertThatThrownBy(() -> offer.segments().add(SEGMENT)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_reject_offer_when_provider_is_null() {
        assertThatThrownBy(() -> new Offer(null, List.of(SEGMENT), price("1.00")))
                .isInstanceOf(InvalidOfferException.class);
    }

    @Test
    void should_reject_offer_when_segments_are_null_or_empty() {
        assertThatThrownBy(() -> new Offer(Provider.ALPHA, null, price("1.00")))
                .isInstanceOf(InvalidOfferException.class);
        assertThatThrownBy(() -> new Offer(Provider.ALPHA, List.of(), price("1.00")))
                .isInstanceOf(InvalidOfferException.class);
    }

    @Test
    void should_reject_offer_when_base_price_is_null() {
        assertThatThrownBy(() -> new Offer(Provider.ALPHA, List.of(SEGMENT), null))
                .isInstanceOf(InvalidOfferException.class);
    }

    @Test
    void should_order_providers_alpha_beta_gamma() {
        assertThat(Provider.values()).containsExactly(Provider.ALPHA, Provider.BETA, Provider.GAMMA);
    }
}
