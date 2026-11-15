package io.github.jerojasol.flights.domain.service;

import io.github.jerojasol.flights.domain.exception.InvalidPriceException;
import io.github.jerojasol.flights.domain.model.ItineraryKey;
import io.github.jerojasol.flights.domain.model.Offer;
import io.github.jerojasol.flights.domain.model.Price;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.Segment;
import static io.github.jerojasol.flights.domain.Fixtures.IB4120;
import static io.github.jerojasol.flights.domain.Fixtures.IB6251;
import static io.github.jerojasol.flights.domain.Fixtures.JFK;
import static io.github.jerojasol.flights.domain.Fixtures.MAD;
import static io.github.jerojasol.flights.domain.Fixtures.chain;
import static io.github.jerojasol.flights.domain.Fixtures.instant;
import static io.github.jerojasol.flights.domain.Fixtures.offer;
import static io.github.jerojasol.flights.domain.Fixtures.price;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class ItineraryDeduplicatorTest {

    private static final String START = "2026-11-20T07:35:00Z";
    private final ItineraryDeduplicator deduplicator = new ItineraryDeduplicator();

    @Test
    void should_keep_cheapest_offer_when_same_itinerary_comes_from_two_providers() {
        List<Segment> segments = chain(START, 480);
        Offer alpha = offer(Provider.ALPHA, "520.00", segments);
        Offer beta = offer(Provider.BETA, "512.30", segments);

        List<Offer> result = deduplicator.deduplicate(List.of(alpha, beta));

        assertThat(result).containsExactly(beta);
        assertThat(result.getFirst().basePrice()).isEqualTo(price("512.30"));
    }

    @Test
    void should_prefer_alpha_when_prices_tie_with_gamma() {
        List<Segment> segments = chain(START, 480);
        Offer gamma = offer(Provider.GAMMA, "500.00", segments);
        Offer alpha = offer(Provider.ALPHA, "500.00", segments);

        assertThat(deduplicator.deduplicate(List.of(gamma, alpha))).containsExactly(alpha);
        assertThat(deduplicator.deduplicate(List.of(alpha, gamma))).containsExactly(alpha);
    }

    @Test
    void should_prefer_beta_over_gamma_when_prices_tie() {
        List<Segment> segments = chain(START, 480);
        Offer gamma = offer(Provider.GAMMA, "500.00", segments);
        Offer beta = offer(Provider.BETA, "500.00", segments);

        assertThat(deduplicator.deduplicate(List.of(gamma, beta))).containsExactly(beta);
    }

    @Test
    void should_keep_all_offers_when_itineraries_are_distinct() {
        Offer first = offer(Provider.ALPHA, "100.00", chain(START, 480));
        Offer second = offer(Provider.BETA, "100.00", chain("2026-11-20T09:00:00Z", 480));
        Offer third = offer(Provider.GAMMA, "100.00", chain(START, 120, 90, 120));

        assertThat(deduplicator.deduplicate(List.of(first, second, third))).containsExactlyInAnyOrder(first, second, third);
    }

    @Test
    void should_treat_offers_as_same_when_only_arrival_time_differs() {
        Offer alpha = offer(Provider.ALPHA, "100.00",
                List.of(new Segment(IB6251, MAD, instant(START), JFK, instant("2026-11-20T15:35:00Z"))));
        Offer beta = offer(Provider.BETA, "90.00",
                List.of(new Segment(IB6251, MAD, instant(START), JFK, instant("2026-11-20T15:40:00Z"))));

        assertThat(deduplicator.deduplicate(List.of(alpha, beta))).containsExactly(beta);
    }

    @Test
    void should_pick_earlier_arrival_when_same_provider_and_price_differ_only_in_arrival() {
        Offer late = offer(Provider.ALPHA, "100.00",
                List.of(new Segment(IB6251, MAD, instant(START), JFK, instant("2026-11-20T15:40:00Z"))));
        Offer early = offer(Provider.ALPHA, "100.00",
                List.of(new Segment(IB6251, MAD, instant(START), JFK, instant("2026-11-20T15:35:00Z"))));

        assertThat(deduplicator.deduplicate(List.of(late, early))).containsExactly(early);
        assertThat(deduplicator.deduplicate(List.of(early, late))).containsExactly(early);
    }

    @Test
    void should_reject_deduplication_when_same_itinerary_is_quoted_in_different_currencies() { // P4
        List<Segment> segments = chain(START, 480);
        Offer euros = offer(Provider.ALPHA, "100.00", segments);
        Offer dollars = new Offer(Provider.BETA, segments,
                new Price(new java.math.BigDecimal("90.00"), java.util.Currency.getInstance("USD")));

        assertThatThrownBy(() -> deduplicator.deduplicate(List.of(euros, dollars)))
                .isInstanceOf(InvalidPriceException.class);
    }

    @Test
    void should_return_empty_when_there_are_no_offers() {
        assertThat(deduplicator.deduplicate(List.of())).isEmpty();
    }

    @Test
    void should_not_depend_on_input_order_when_deduplicating() {
        List<Offer> offers = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            List<Segment> segments = chain("2026-11-20T0" + (i % 5) + ":00:00Z", 480 + (i % 4));
            offers.add(offer(Provider.values()[i % 3], (100 + i % 7) + ".00", segments));
        }
        List<Offer> expected = deduplicator.deduplicate(offers);

        for (long seed = 0; seed < 5; seed++) {
            List<Offer> shuffled = new ArrayList<>(offers);
            Collections.shuffle(shuffled, new Random(seed));
            assertThat(deduplicator.deduplicate(shuffled)).isEqualTo(expected);
        }
    }

    @Test
    void should_return_offers_ordered_by_key_when_deduplicating() {
        List<Offer> offers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            offers.add(offer(Provider.ALPHA, "100.00", chain("2026-11-2" + i + "T07:35:00Z", 480)));
        }

        List<String> keys = deduplicator.deduplicate(offers).stream()
                .map(offer -> ItineraryKey.of(offer.segments()).value()).toList();

        assertThat(keys).isSorted();
    }

    @Test
    @Tag("sanity")
    void should_deduplicate_ten_thousand_offers_quickly_when_there_are_one_thousand_distinct_keys() {
        Instant base = instant(START);
        List<Offer> offers = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            Instant departure = base.plus(Duration.ofMinutes(i % 1_000));
            Segment segment = new Segment(IB4120, MAD, departure, JFK, departure.plus(Duration.ofHours(8)));
            offers.add(offer(Provider.values()[i % 3], (500 - i / 1_000) + ".00", List.of(segment)));
        }

        long started = System.nanoTime();
        List<Offer> result = deduplicator.deduplicate(offers);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(result).hasSize(1_000);
        assertThat(result).allSatisfy(offer -> assertThat(offer.basePrice()).isEqualTo(price("491.00")));
        assertThat(elapsed).isLessThan(Duration.ofSeconds(1));
    }
}
