package io.github.jerojasol.flights.domain.model;

import static io.github.jerojasol.flights.domain.Fixtures.chain;
import static io.github.jerojasol.flights.domain.Fixtures.offer;
import static io.github.jerojasol.flights.domain.Fixtures.price;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ItineraryPageTest {

    @Test
    void should_have_no_items_and_no_cursor_when_page_is_empty() {
        assertThat(ItineraryPage.empty().items()).isEmpty();
        assertThat(ItineraryPage.empty().next()).isEmpty();
    }

    @Test
    void should_copy_items_when_page_is_created() {
        Itinerary itinerary = Itinerary.from(offer(Provider.ALPHA, "10.00", chain("2026-11-20T07:35:00Z", 480)),
                new PercentageMarkupPolicy(java.math.BigDecimal.ONE), java.time.Instant.parse("2026-11-14T10:00:00Z"));
        List<Itinerary> source = new java.util.ArrayList<>(List.of(itinerary));

        ItineraryPage page = new ItineraryPage(source, Optional.of(new PageCursor(price("10.10"), itinerary.key())));
        source.clear();

        assertThat(page.items()).containsExactly(itinerary);
    }

    @Test
    void should_reject_cursor_when_price_or_key_is_null() {
        ItineraryKey key = ItineraryKey.fromStored("a".repeat(64));

        assertThatThrownBy(() -> new PageCursor(null, key)).isInstanceOf(InvalidSearchException.class);
        assertThatThrownBy(() -> new PageCursor(price("1.00"), null)).isInstanceOf(InvalidSearchException.class);
    }

    @Test
    void should_accept_empty_outcomes_when_search_result_has_never_been_aggregated() {
        SearchResult result = new SearchResult(ItineraryPage.empty(), Freshness.MISSING, Optional.<Instant>empty(), Map.of());

        assertThat(result.lastOutcomes()).isEmpty();
        assertThat(result.freshness()).isEqualTo(Freshness.MISSING);
    }

    @Test
    void should_reject_search_result_when_optional_is_null() {
        assertThatThrownBy(() -> new SearchResult(ItineraryPage.empty(), Freshness.FRESH, null, Map.of()))
                .isInstanceOf(InvalidSearchException.class);
    }
}
