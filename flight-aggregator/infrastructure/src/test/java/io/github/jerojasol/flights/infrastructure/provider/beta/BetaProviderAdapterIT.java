package io.github.jerojasol.flights.infrastructure.provider.beta;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okXml;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.github.jerojasol.flights.infrastructure.provider.beta.BetaOfferMapperTest.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.jerojasol.flights.domain.model.Airport;
import io.github.jerojasol.flights.domain.model.ProviderResponse;
import io.github.jerojasol.flights.domain.model.ProviderStatus;
import io.github.jerojasol.flights.domain.model.ProviderUnavailableException;
import io.github.jerojasol.flights.domain.model.SearchCriteria;
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

class BetaProviderAdapterIT {

    @RegisterExtension
    static WireMockExtension beta = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final SearchCriteria MAD_JFK =
            new SearchCriteria(Airport.of("MAD"), Airport.of("JFK"), LocalDate.parse("2026-11-20"));
    private static final Duration TIMEOUT = Duration.ofSeconds(1);
    private static final int WINDOW = 4;
    // Production values except the window and the retry pause, shortened so the tests stay fast.
    private static final ResilienceSettings SETTINGS =
            new ResilienceSettings(TIMEOUT, WINDOW, 50f, Duration.ofSeconds(30), 2, Duration.ofMillis(10));

    private ExecutorService executor;
    private BetaProviderAdapter adapter;

    @BeforeEach
    void newAdapter() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        adapter = new BetaProviderAdapter(beta.baseUrl(), SETTINGS, executor);
    }

    @AfterEach
    void stopExecutor() {
        executor.close();
    }

    @Test
    void should_return_offers_when_beta_answers_200() {
        beta.stubFor(get(urlPathEqualTo("/search")).willReturn(okXml(fixture("contract-example.xml"))));

        ProviderResponse response = adapter.fetch(MAD_JFK);

        assertThat(response.offers()).hasSize(2);
        assertThat(response.unmappable()).isZero();
        beta.verify(1, getRequestedFor(urlPathEqualTo("/search"))
                .withQueryParam("origin", equalTo("MAD"))
                .withQueryParam("destination", equalTo("JFK"))
                .withQueryParam("departureDate", equalTo("2026-11-20")));
    }

    @Test
    void should_report_error_after_one_retry_when_beta_answers_500() {
        beta.stubFor(get(urlPathEqualTo("/search")).willReturn(aResponse().withStatus(500)
                .withHeader("Content-Type", "application/xml").withBody(fixture("error-500.xml"))));

        ProviderUnavailableException failure = fetchFailure();

        assertThat(failure.status()).isEqualTo(ProviderStatus.ERROR);
        assertThat(failure.getCause()).hasMessageContaining("HTTP 500").hasMessageContaining("SYSTEM_ERROR");
        beta.verify(2, getRequestedFor(urlPathEqualTo("/search")));
    }

    @Test
    void should_report_error_after_one_request_without_touching_the_breaker_with_beta_fault_when_beta_answers_400() {
        // A 500 first, so the breaker has recorded failures and the counter is known to work.
        beta.stubFor(get(urlPathEqualTo("/search")).willReturn(aResponse().withStatus(500)));
        fetchFailure();
        int recordedFailures = adapter.circuitFailedCalls();
        assertThat(recordedFailures).isEqualTo(2);
        beta.resetAll();
        beta.stubFor(get(urlPathEqualTo("/search")).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/xml")
                .withBody("<Fault xmlns=\"urn:beta-gds:search:v2\"><Code>INVALID_REQUEST</Code>"
                        + "<Message>Parameter 'origin' is required</Message></Fault>")));

        ProviderUnavailableException failure = fetchFailure();

        assertThat(failure.status()).isEqualTo(ProviderStatus.ERROR);
        assertThat(failure.getCause()).isInstanceOf(ProviderRequestRejected.class)
                .hasMessageContaining("HTTP 400").hasMessageContaining("INVALID_REQUEST");
        beta.verify(1, getRequestedFor(urlPathEqualTo("/search")));
        assertThat(adapter.circuitFailedCalls()).isEqualTo(recordedFailures);
    }

    @Test
    void should_report_error_after_exactly_one_request_when_body_has_no_itineraries_element() {
        beta.stubFor(get(urlPathEqualTo("/search")).willReturn(okXml("<html>gateway</html>")));

        ProviderUnavailableException failure = fetchFailure();

        assertThat(failure.status()).isEqualTo(ProviderStatus.ERROR);
        assertThat(failure.getCause()).isInstanceOf(ProviderContractViolation.class);
        beta.verify(1, getRequestedFor(urlPathEqualTo("/search")));
    }

    @Test
    void should_report_error_after_exactly_one_request_when_body_is_not_readable_xml() {
        beta.stubFor(get(urlPathEqualTo("/search")).willReturn(okXml("<SearchResponse><Itineraries")));

        ProviderUnavailableException failure = fetchFailure();

        assertThat(failure.status()).isEqualTo(ProviderStatus.ERROR);
        assertThat(failure.getCause()).isInstanceOf(ProviderContractViolation.class);
        beta.verify(1, getRequestedFor(urlPathEqualTo("/search")));
    }

    @Test
    void should_report_timeout_within_budget_and_not_retry_when_beta_is_too_slow() {
        beta.stubFor(get(urlPathEqualTo("/search"))
                .willReturn(okXml(fixture("contract-example.xml")).withFixedDelay(3_000)));

        long started = System.nanoTime();
        ProviderUnavailableException failure = fetchFailure();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(failure.status()).isEqualTo(ProviderStatus.TIMEOUT);
        assertThat(elapsed).isLessThan(TIMEOUT.plusMillis(700));
        beta.verify(1, getRequestedFor(urlPathEqualTo("/search")));
    }

    @Test
    void should_report_circuit_open_without_calling_beta_when_failures_fill_the_window() {
        beta.stubFor(get(urlPathEqualTo("/search")).willReturn(aResponse().withStatus(500)));
        // Each failing fetch is two attempts (one retry), so WINDOW / 2 fetches fill the window with failures.
        for (int i = 0; i < WINDOW / 2; i++) {
            assertThat(fetchFailure().status()).isEqualTo(ProviderStatus.ERROR);
        }
        beta.verify(WINDOW, getRequestedFor(urlPathEqualTo("/search")));

        assertThat(fetchFailure().status()).isEqualTo(ProviderStatus.CIRCUIT_OPEN);
        beta.verify(WINDOW, getRequestedFor(urlPathEqualTo("/search")));
    }

    private ProviderUnavailableException fetchFailure() {
        ProviderUnavailableException failure =
                catchThrowableOfType(ProviderUnavailableException.class, () -> adapter.fetch(MAD_JFK));
        assertThat(failure).as("fetch should fail with ProviderUnavailableException").isNotNull();
        return failure;
    }
}
