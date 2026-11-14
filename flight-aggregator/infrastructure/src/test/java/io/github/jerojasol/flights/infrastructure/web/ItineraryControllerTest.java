package io.github.jerojasol.flights.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.jerojasol.flights.domain.model.Airport;
import io.github.jerojasol.flights.domain.model.Carrier;
import io.github.jerojasol.flights.domain.model.FlightNumber;
import io.github.jerojasol.flights.domain.model.Freshness;
import io.github.jerojasol.flights.domain.model.InvalidItineraryException;
import io.github.jerojasol.flights.domain.model.Itinerary;
import io.github.jerojasol.flights.domain.model.ItineraryFilter;
import io.github.jerojasol.flights.domain.model.ItineraryPage;
import io.github.jerojasol.flights.domain.model.Offer;
import io.github.jerojasol.flights.domain.model.PageCursor;
import io.github.jerojasol.flights.domain.model.PercentageMarkupPolicy;
import io.github.jerojasol.flights.domain.model.Price;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.ProviderOutcome;
import io.github.jerojasol.flights.domain.model.ProviderStatus;
import io.github.jerojasol.flights.domain.model.SearchCriteria;
import io.github.jerojasol.flights.domain.model.SearchResult;
import io.github.jerojasol.flights.domain.model.Segment;
import io.github.jerojasol.flights.domain.port.in.SearchItinerariesUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@WebMvcTest(ItineraryController.class)
@Import(ItineraryControllerTest.WebBeans.class)
class ItineraryControllerTest {

    @TestConfiguration
    static class WebBeans {
        @Bean
        SearchRequestMapper searchRequestMapper() {
            return new SearchRequestMapper(Currency.getInstance("EUR"));
        }
    }

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");
    private static final Instant DEPARTURE = Instant.parse("2026-11-20T07:35:00Z");
    private static final Itinerary DIRECT = Itinerary.from(new Offer(Provider.BETA,
                    List.of(new Segment(FlightNumber.of("IB6251"), Airport.of("MAD"), DEPARTURE, Airport.of("JFK"),
                            DEPARTURE.plus(Duration.ofHours(8).plusMinutes(35)))),
                    Price.of("100.00", "EUR")),
            new PercentageMarkupPolicy(new BigDecimal("5")), NOW);
    private static final PageCursor NEXT = new PageCursor(DIRECT.finalPrice(), DIRECT.key());
    private static final Map<Provider, ProviderOutcome> OUTCOMES = Map.of(
            Provider.ALPHA, new ProviderOutcome(ProviderStatus.OK, 4, 1, Duration.ofMillis(412)),
            Provider.GAMMA, new ProviderOutcome(ProviderStatus.TIMEOUT, 0, 0, Duration.ofSeconds(3)));
    private static final String QUERY = "/api/itineraries?origin=MAD&destination=JFK&date=2026-11-20";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private SearchItinerariesUseCase searchItineraries;

    @Test
    void should_return_the_full_page_shape_with_next_cursor_when_search_has_a_next_page() {
        given(searchItineraries.search(any(), any(), any(), anyInt())).willReturn(new SearchResult(
                new ItineraryPage(List.of(DIRECT), Optional.of(NEXT)), Freshness.FRESH, Optional.of(NOW), OUTCOMES));

        MvcTestResult result = mvc.get().uri(QUERY).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items[0].id").isEqualTo(DIRECT.key().value());
        assertThat(result).bodyJson().extractingPath("$.items[0].origin").isEqualTo("MAD");
        assertThat(result).bodyJson().extractingPath("$.items[0].segments[0].flightNumber").isEqualTo("IB6251");
        assertThat(result).bodyJson().extractingPath("$.items[0].segments[0].departureAt").isEqualTo("2026-11-20T07:35:00Z");
        assertThat(result).bodyJson().extractingPath("$.items[0].basePrice.amount").isEqualTo(100.00);
        assertThat(result).bodyJson().extractingPath("$.items[0].finalPrice.amount").isEqualTo(105.00);
        assertThat(result).bodyJson().extractingPath("$.items[0].finalPrice.currency").isEqualTo("EUR");
        assertThat(result).bodyJson().extractingPath("$.items[0].source").isEqualTo("BETA");
        assertThat(result).bodyJson().extractingPath("$.items[0].connections").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.items[0].totalDuration").isEqualTo("PT8H35M");
        assertThat(result).bodyJson().extractingPath("$.nextCursor").isEqualTo(CursorCodec.encode(NEXT));
        assertThat(result).bodyJson().extractingPath("$.freshness").isEqualTo("FRESH");
        assertThat(result).bodyJson().extractingPath("$.aggregatedAt").isEqualTo("2026-11-14T10:00:00Z");
        assertThat(result).bodyJson().extractingPath("$.providers.ALPHA.status").isEqualTo("OK");
        assertThat(result).bodyJson().extractingPath("$.providers.ALPHA.received").isEqualTo(4);
        assertThat(result).bodyJson().extractingPath("$.providers.ALPHA.rejected").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.providers.ALPHA.elapsedMs").isEqualTo(412);
        assertThat(result).bodyJson().extractingPath("$.providers.GAMMA.status").isEqualTo("TIMEOUT");
    }

    @Test
    void should_return_null_cursor_when_page_is_the_last() {
        given(searchItineraries.search(any(), any(), any(), anyInt())).willReturn(new SearchResult(
                new ItineraryPage(List.of(DIRECT), Optional.empty()), Freshness.STALE, Optional.empty(), Map.of()));

        MvcTestResult result = mvc.get().uri(QUERY).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.nextCursor").isNull();
        assertThat(result).bodyJson().extractingPath("$.aggregatedAt").isNull();
    }

    @Test
    void should_normalise_lowercase_codes_and_apply_defaults_when_calling_the_use_case() {
        given(searchItineraries.search(any(), any(), any(), anyInt())).willReturn(emptyResult());
        ArgumentCaptor<SearchCriteria> criteria = ArgumentCaptor.forClass(SearchCriteria.class);
        ArgumentCaptor<ItineraryFilter> filter = ArgumentCaptor.forClass(ItineraryFilter.class);

        assertThat(mvc.get().uri("/api/itineraries?origin=mad&destination=jfk&date=2026-11-20&maxPrice=800&carrier=ib"))
                .hasStatusOk();

        verify(searchItineraries).search(criteria.capture(), filter.capture(), any(), eq(20));
        assertThat(criteria.getValue()).isEqualTo(
                new SearchCriteria(Airport.of("MAD"), Airport.of("JFK"), LocalDate.parse("2026-11-20")));
        assertThat(filter.getValue().maxPrice()).contains(Price.of("800", "EUR"));
        assertThat(filter.getValue().carrier()).contains(Carrier.of("IB"));
    }

    @Test
    void should_return_400_with_date_error_when_date_is_missing() {
        MvcTestResult result = mvc.get().uri("/api/itineraries?origin=MAD&destination=JFK").exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.errors.date").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(400);
        verifyNoInteractions(searchItineraries);
    }

    @Test
    void should_return_400_when_date_is_not_iso() {
        MvcTestResult result = mvc.get().uri("/api/itineraries?origin=MAD&destination=JFK&date=20261120").exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.errors.date").isEqualTo("has an invalid value");
    }

    @Test
    void should_return_400_with_cursor_error_when_cursor_is_not_valid() {
        MvcTestResult result = mvc.get().uri(QUERY + "&cursor=not-a-cursor").exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.errors.cursor").isNotNull();
        verifyNoInteractions(searchItineraries);
    }

    @Test
    void should_return_400_with_limit_error_when_limit_exceeds_100() {
        MvcTestResult result = mvc.get().uri(QUERY + "&limit=500").exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.errors.limit").isNotNull();
        verifyNoInteractions(searchItineraries);
    }

    @Test
    void should_return_400_when_codes_have_the_wrong_shape() {
        MvcTestResult result = mvc.get().uri("/api/itineraries?origin=MADX&destination=JFK&date=2026-11-20&carrier=IBX").exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.errors.origin").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.errors.carrier").isNotNull();
    }

    @Test
    void should_return_500_without_domain_message_when_the_port_fails_with_a_domain_exception()
            throws UnsupportedEncodingException {
        // A domain rule broken behind the port (e.g. a corrupt stored row) is a server fault, not the client's.
        given(searchItineraries.search(any(), any(), any(), anyInt()))
                .willThrow(new InvalidItineraryException("Prices have different currencies: EUR and USD"));

        MvcTestResult result = mvc.get().uri(QUERY).exchange();

        assertThat(result).hasStatus(500);
        assertThat(result).bodyJson().extractingPath("$.correlationId").isNotNull();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("currencies", "EUR and USD");
    }

    @Test
    void should_return_400_with_cursor_error_when_cursor_is_in_another_currency() {
        String usdCursor = CursorCodec.encode(new PageCursor(Price.of("105.00", "USD"), DIRECT.key()));

        MvcTestResult result = mvc.get().uri(QUERY + "&cursor=" + usdCursor).exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.errors.cursor").isNotNull();
        verifyNoInteractions(searchItineraries);
    }

    @Test
    void should_return_422_with_domain_message_when_origin_equals_destination() {
        MvcTestResult result = mvc.get().uri("/api/itineraries?origin=MAD&destination=mad&date=2026-11-20").exchange();

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.detail").isNotNull();
        verifyNoInteractions(searchItineraries);
    }

    @Test
    void should_return_500_with_correlation_id_and_no_stack_trace_when_something_unexpected_fails()
            throws UnsupportedEncodingException {
        given(searchItineraries.search(any(), any(), any(), anyInt())).willThrow(new IllegalStateException("boom"));

        MvcTestResult result = mvc.get().uri(QUERY).exchange();

        assertThat(result).hasStatus(500);
        assertThat(result).bodyJson().extractingPath("$.correlationId").isNotNull();
        assertThat(result).bodyJson().doesNotHavePath("$.trace");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("boom", "IllegalStateException");
    }

    private static SearchResult emptyResult() {
        return new SearchResult(ItineraryPage.empty(), Freshness.FRESH, Optional.empty(), Map.of());
    }
}
