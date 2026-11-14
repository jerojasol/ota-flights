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

class ProviderResponseTest {

    private static final Offer OFFER = new Offer(Provider.ALPHA,
            List.of(segment(IB6251, MAD, "2026-11-20T07:35:00Z", JFK, "2026-11-20T15:35:00Z")), price("512.30"));

    @Test
    void should_have_no_unmappable_itineraries_when_built_from_offers_only() {
        ProviderResponse response = ProviderResponse.of(List.of(OFFER));

        assertThat(response.offers()).containsExactly(OFFER);
        assertThat(response.unmappable()).isZero();
    }

    @Test
    void should_reject_response_when_unmappable_count_is_negative() { // R2
        assertThatThrownBy(() -> new ProviderResponse(List.of(), -1)).isInstanceOf(InvalidAggregationException.class);
    }

    @Test
    void should_reject_response_when_offers_are_null() { // R1
        assertThatThrownBy(() -> new ProviderResponse(null, 0)).isInstanceOf(InvalidAggregationException.class);
    }

    @Test
    void should_not_change_response_when_source_list_is_modified_afterwards() { // R1
        List<Offer> source = new ArrayList<>(List.of(OFFER));
        ProviderResponse response = new ProviderResponse(source, 2);

        source.clear();

        assertThat(response.offers()).containsExactly(OFFER);
        assertThat(response.unmappable()).isEqualTo(2);
    }
}
