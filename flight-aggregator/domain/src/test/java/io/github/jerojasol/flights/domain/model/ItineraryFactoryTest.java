package io.github.jerojasol.flights.domain.model;

import static io.github.jerojasol.flights.domain.Fixtures.chain;
import static io.github.jerojasol.flights.domain.Fixtures.instant;
import static io.github.jerojasol.flights.domain.Fixtures.offer;
import static io.github.jerojasol.flights.domain.Fixtures.price;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class ItineraryFactoryTest {

    private static final Clock CLOCK = Clock.fixed(instant("2026-11-14T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void should_apply_markup_and_stamp_ingestion_time_when_creating_itinerary() { // I10
        ItineraryFactory factory = new ItineraryFactory(new PercentageMarkupPolicy(new BigDecimal("5")), CLOCK);
        Offer winning = offer(Provider.GAMMA, "100.00", chain("2026-11-20T07:35:00Z", 480));

        Itinerary itinerary = factory.create(winning);

        assertThat(itinerary.finalPrice()).isEqualTo(price("105.00"));
        assertThat(itinerary.basePrice()).isEqualTo(price("100.00"));
        assertThat(itinerary.source()).isEqualTo(Provider.GAMMA);
        assertThat(itinerary.ingestedAt()).isEqualTo(instant("2026-11-14T10:00:00Z"));
    }

    @Test
    void should_use_the_configured_policy_when_it_is_not_the_default() {
        ItineraryFactory factory = new ItineraryFactory(base -> base, CLOCK);

        assertThat(factory.create(offer(Provider.ALPHA, "50.00", chain("2026-11-20T07:35:00Z", 480))).finalPrice())
                .isEqualTo(price("50.00"));
    }

    @Test
    void should_propagate_rejection_when_offer_is_not_a_valid_itinerary() {
        ItineraryFactory factory = new ItineraryFactory(new PercentageMarkupPolicy(new BigDecimal("5")), CLOCK);

        assertThatThrownBy(() -> factory.create(offer(Provider.ALPHA, "50.00", chain("2026-11-20T07:35:00Z", 120, 10, 120))))
                .isInstanceOf(InvalidItineraryException.class);
    }

    @Test
    void should_reject_factory_when_policy_is_null() {
        assertThatThrownBy(() -> new ItineraryFactory(null)).isInstanceOf(InvalidItineraryException.class);
    }
}
