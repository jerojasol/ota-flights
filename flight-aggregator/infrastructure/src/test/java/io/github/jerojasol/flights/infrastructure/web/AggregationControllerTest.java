package io.github.jerojasol.flights.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.jerojasol.flights.domain.model.AggregationResult;
import io.github.jerojasol.flights.domain.model.Airport;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.ProviderOutcome;
import io.github.jerojasol.flights.domain.model.ProviderStatus;
import io.github.jerojasol.flights.domain.model.SearchCriteria;
import io.github.jerojasol.flights.domain.port.in.RefreshItinerariesUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Map;
import java.util.Optional;

@WebMvcTest(AggregationController.class)
@Import(AggregationControllerTest.WebBeans.class)
class AggregationControllerTest {

    @TestConfiguration
    static class WebBeans {
        @Bean
        SearchRequestMapper searchRequestMapper() {
            return new SearchRequestMapper(Currency.getInstance("EUR"));
        }
    }

    private static final String BODY = "{\"origin\":\"mad\",\"destination\":\"JFK\",\"date\":\"2026-11-20\"}";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private RefreshItinerariesUseCase refreshItineraries;

    @Test
    void should_return_200_with_outcome_per_provider_when_aggregation_finishes() {
        given(refreshItineraries.refresh(any())).willReturn(Optional.of(new AggregationResult(7, Map.of(
                Provider.ALPHA, new ProviderOutcome(ProviderStatus.OK, 5, 1, Duration.ofMillis(300)),
                Provider.BETA, new ProviderOutcome(ProviderStatus.CIRCUIT_OPEN, 0, 0, Duration.ZERO)),
                Instant.parse("2026-11-14T10:00:00Z"))));

        MvcTestResult result = mvc.post().uri("/api/admin/aggregations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"origin\":\"mad\",\"destination\":\"JFK\",\"date\":\"2026-11-20\"}").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.persisted").isEqualTo(7);
        assertThat(result).bodyJson().extractingPath("$.aggregatedAt").isEqualTo("2026-11-14T10:00:00Z");
        assertThat(result).bodyJson().extractingPath("$.providers.ALPHA.received").isEqualTo(5);
        assertThat(result).bodyJson().extractingPath("$.providers.BETA.status").isEqualTo("CIRCUIT_OPEN");
        verify(refreshItineraries).refresh(
                new SearchCriteria(Airport.of("MAD"), Airport.of("JFK"), LocalDate.parse("2026-11-20")));
    }

    @Test
    void should_return_400_with_field_errors_when_body_is_invalid() {
        MvcTestResult result = mvc.post().uri("/api/admin/aggregations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"origin\":\"MADRID\",\"destination\":\"JFK\"}").exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.errors.origin").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.errors.date").isNotNull();
        verifyNoInteractions(refreshItineraries);
    }

    @Test
    void should_return_409_when_the_aggregation_lock_cannot_be_taken() {
        given(refreshItineraries.refresh(any())).willReturn(Optional.empty());

        MvcTestResult result = mvc.post().uri("/api/admin/aggregations").contentType(MediaType.APPLICATION_JSON)
                .content(BODY).exchange();

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.detail").isNotNull();
    }

    @Test
    void should_return_422_when_origin_equals_destination_in_the_body() {
        MvcTestResult result = mvc.post().uri("/api/admin/aggregations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"origin\":\"MAD\",\"destination\":\"mad\",\"date\":\"2026-11-20\"}").exchange();

        assertThat(result).hasStatus(422);
        verifyNoInteractions(refreshItineraries);
    }
}
