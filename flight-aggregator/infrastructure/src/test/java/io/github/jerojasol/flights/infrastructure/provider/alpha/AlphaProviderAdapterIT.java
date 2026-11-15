package io.github.jerojasol.flights.infrastructure.provider.alpha;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.github.jerojasol.flights.infrastructure.provider.alpha.AlphaOfferMapperTest.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.jerojasol.flights.domain.aggregation.ProviderResponse;
import io.github.jerojasol.flights.domain.aggregation.ProviderStatus;
import io.github.jerojasol.flights.domain.exception.ProviderUnavailableException;
import io.github.jerojasol.flights.domain.model.Airport;
import io.github.jerojasol.flights.domain.search.SearchCriteria;
import io.github.jerojasol.flights.infrastructure.provider.ProviderContractViolation;
import io.github.jerojasol.flights.infrastructure.provider.ProviderRequestRejected;
import io.github.jerojasol.flights.infrastructure.provider.ResilienceSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class AlphaProviderAdapterIT {

    @RegisterExtension
    static WireMockExtension alpha = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final SearchCriteria MAD_JFK =
            new SearchCriteria(Airport.of("MAD"), Airport.of("JFK"), LocalDate.parse("2026-11-20"));
    private static final Duration TIMEOUT = Duration.ofSeconds(1);
    private static final int WINDOW = 4;
    // Production values except the window and the retry pause, shortened so the tests stay fast.
    private static final ResilienceSettings SETTINGS =
            new ResilienceSettings(TIMEOUT, WINDOW, 50f, Duration.ofSeconds(30), 2, Duration.ofMillis(10));

    private ExecutorService executor;
    private AlphaProviderAdapter adapter;

    @BeforeEach
    void newAdapter() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        adapter = new AlphaProviderAdapter(alpha.baseUrl() + "/alpha", SETTINGS, executor);
    }

    @AfterEach
    void stopExecutor() {
        executor.close();
    }

    @Test
    void should_return_offers_when_alpha_answers_200() {
        alpha.stubFor(get(urlPathEqualTo("/alpha/flights")).willReturn(okJson(fixture("contract-example.json"))));

        ProviderResponse response = adapter.fetch(MAD_JFK);

        assertThat(response.offers()).hasSize(2);
        assertThat(response.unmappable()).isZero();
        alpha.verify(1, getRequestedFor(urlPathEqualTo("/alpha/flights"))
                .withQueryParam("from", equalTo("MAD"))
                .withQueryParam("to", equalTo("JFK"))
                .withQueryParam("date", equalTo("2026-11-20")));
    }

    @Test
    void should_report_error_after_one_retry_when_alpha_answers_500() {
        alpha.stubFor(get(urlPathEqualTo("/alpha/flights")).willReturn(aResponse().withStatus(500)
                .withHeader("Content-Type", "application/json").withBody(fixture("error-500.json"))));

        ProviderUnavailableException failure = fetchFailure();

        assertThat(failure.status()).isEqualTo(ProviderStatus.ERROR);
        assertThat(failure.getCause()).hasMessageContaining("HTTP 500").hasMessageContaining("Upstream inventory unavailable");
        alpha.verify(2, getRequestedFor(urlPathEqualTo("/alpha/flights")));
    }

    @Test
    void should_report_error_after_one_request_without_touching_the_breaker_with_alpha_body_when_alpha_answers_400() {
        // A 500 first, so the breaker has recorded failures and the counter is known to work.
        alpha.stubFor(get(urlPathEqualTo("/alpha/flights")).willReturn(aResponse().withStatus(500)));
        fetchFailure();
        int recordedFailures = adapter.circuitFailedCalls();
        assertThat(recordedFailures).isEqualTo(2);
        alpha.resetAll();
        alpha.stubFor(get(urlPathEqualTo("/alpha/flights")).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{ \"error\": \"BAD_REQUEST\", \"message\": \"Parameter 'from' must be a 3-letter IATA code\" }")));

        ProviderUnavailableException failure = fetchFailure();

        assertThat(failure.status()).isEqualTo(ProviderStatus.ERROR);
        assertThat(failure.getCause()).isInstanceOf(ProviderRequestRejected.class)
                .hasMessageContaining("HTTP 400").hasMessageContaining("BAD_REQUEST");
        alpha.verify(1, getRequestedFor(urlPathEqualTo("/alpha/flights")));
        assertThat(adapter.circuitFailedCalls()).isEqualTo(recordedFailures);
    }

    @Test
    void should_report_error_after_exactly_one_request_when_body_is_not_alpha_json() {
        alpha.stubFor(get(urlPathEqualTo("/alpha/flights")).willReturn(okJson("<html>gateway</html>")));

        ProviderUnavailableException failure = fetchFailure();

        assertThat(failure.status()).isEqualTo(ProviderStatus.ERROR);
        assertThat(failure.getCause()).isInstanceOf(ProviderContractViolation.class);
        alpha.verify(1, getRequestedFor(urlPathEqualTo("/alpha/flights")));
    }

    @Test
    void should_report_timeout_within_budget_and_not_retry_when_alpha_is_too_slow() {
        alpha.stubFor(get(urlPathEqualTo("/alpha/flights"))
                .willReturn(okJson(fixture("contract-example.json")).withFixedDelay(3_000)));

        long started = System.nanoTime();
        ProviderUnavailableException failure = fetchFailure();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(failure.status()).isEqualTo(ProviderStatus.TIMEOUT);
        assertThat(elapsed).isLessThan(TIMEOUT.plusMillis(700));
        alpha.verify(1, getRequestedFor(urlPathEqualTo("/alpha/flights")));
    }

    @Test
    void should_report_circuit_open_without_calling_alpha_when_failures_fill_the_window() {
        alpha.stubFor(get(urlPathEqualTo("/alpha/flights")).willReturn(aResponse().withStatus(500)));
        // Each failing fetch is two attempts (one retry), so WINDOW / 2 fetches fill the window with failures.
        for (int i = 0; i < WINDOW / 2; i++) {
            assertThat(fetchFailure().status()).isEqualTo(ProviderStatus.ERROR);
        }
        alpha.verify(WINDOW, getRequestedFor(urlPathEqualTo("/alpha/flights")));

        ProviderUnavailableException failure = fetchFailure();

        assertThat(failure.status()).isEqualTo(ProviderStatus.CIRCUIT_OPEN);
        alpha.verify(WINDOW, getRequestedFor(urlPathEqualTo("/alpha/flights")));
    }

    private ProviderUnavailableException fetchFailure() {
        ProviderUnavailableException failure =
                catchThrowableOfType(ProviderUnavailableException.class, () -> adapter.fetch(MAD_JFK));
        assertThat(failure).as("fetch should fail with ProviderUnavailableException").isNotNull();
        return failure;
    }
}
