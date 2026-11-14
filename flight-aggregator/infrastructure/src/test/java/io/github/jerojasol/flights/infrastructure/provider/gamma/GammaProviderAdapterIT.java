package io.github.jerojasol.flights.infrastructure.provider.gamma;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.github.jerojasol.flights.infrastructure.provider.gamma.GammaOfferMapperTest.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.jerojasol.flights.domain.model.Airport;
import io.github.jerojasol.flights.domain.model.ProviderResponse;
import io.github.jerojasol.flights.domain.model.ProviderStatus;
import io.github.jerojasol.flights.domain.model.ProviderUnavailableException;
import io.github.jerojasol.flights.domain.model.SearchCriteria;
import io.github.jerojasol.flights.infrastructure.provider.ProviderContractViolation;
import io.github.jerojasol.flights.infrastructure.provider.ProviderRequestRejected;
import io.github.jerojasol.flights.infrastructure.provider.ResilienceSettings;
import io.github.jerojasol.flights.infrastructure.provider.gamma.GammaStreamReader.TruncatedStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class GammaProviderAdapterIT {

    @RegisterExtension
    static WireMockExtension gamma = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final String EXPORT = "/export";
    private static final SearchCriteria MAD_JFK =
            new SearchCriteria(Airport.of("MAD"), Airport.of("JFK"), LocalDate.parse("2026-11-20"));
    private static final Duration TIMEOUT = Duration.ofSeconds(1);
    private static final int WINDOW = 4;
    // Production values except the window and the retry pause, shortened so the tests stay fast.
    private static final ResilienceSettings SETTINGS =
            new ResilienceSettings(TIMEOUT, WINDOW, 50f, Duration.ofSeconds(30), 2, Duration.ofMillis(10));

    private ExecutorService executor;
    private GammaProviderAdapter adapter;

    @BeforeEach
    void newAdapter() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        adapter = new GammaProviderAdapter(gamma.baseUrl(), SETTINGS, executor);
    }

    @AfterEach
    void stopExecutor() {
        executor.close();
    }

    @Test
    void should_return_offers_with_compact_date_parameter_when_gamma_answers_200() {
        gamma.stubFor(get(urlPathEqualTo(EXPORT)).willReturn(csv(fixture("contract-example.csv"))));

        ProviderResponse response = adapter.fetch(MAD_JFK);

        assertThat(response.offers()).hasSize(3);
        assertThat(response.unmappable()).isZero();
        gamma.verify(1, getRequestedFor(urlPathEqualTo(EXPORT))
                .withQueryParam("o", equalTo("MAD"))
                .withQueryParam("d", equalTo("JFK"))
                .withQueryParam("dt", equalTo("20261120")));
    }

    @Test
    void should_read_the_whole_export_when_gamma_streams_it_in_chunks() {
        gamma.stubFor(get(urlPathEqualTo(EXPORT))
                .willReturn(csv(fixture("contract-example.csv")).withChunkedDribbleDelay(5, 200)));

        ProviderResponse response = adapter.fetch(MAD_JFK);

        assertThat(response.offers()).hasSize(3);
    }

    @Test
    void should_report_error_after_one_retry_when_gamma_answers_500() {
        gamma.stubFor(get(urlPathEqualTo(EXPORT)).willReturn(aResponse().withStatus(500)
                .withHeader("Content-Type", "text/plain").withBody(fixture("error-500.txt"))));

        ProviderUnavailableException failure = fetchFailure();

        assertThat(failure.status()).isEqualTo(ProviderStatus.ERROR);
        assertThat(failure.getCause()).hasMessageContaining("HTTP 500").hasMessageContaining("ERR 500 export failed");
        gamma.verify(2, getRequestedFor(urlPathEqualTo(EXPORT)));
    }

    @Test
    void should_report_error_after_one_request_without_touching_the_breaker_with_gamma_text_when_gamma_answers_400() {
        // A 500 first, so the breaker has recorded failures and the counter is known to work.
        gamma.stubFor(get(urlPathEqualTo(EXPORT)).willReturn(aResponse().withStatus(500)));
        fetchFailure();
        int recordedFailures = adapter.circuitFailedCalls();
        assertThat(recordedFailures).isEqualTo(2);
        gamma.resetAll();
        gamma.stubFor(get(urlPathEqualTo(EXPORT)).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "text/plain")
                .withBody("ERR 400 missing parameter: o\n")));

        ProviderUnavailableException failure = fetchFailure();

        assertThat(failure.status()).isEqualTo(ProviderStatus.ERROR);
        assertThat(failure.getCause()).isInstanceOf(ProviderRequestRejected.class)
                .hasMessageContaining("HTTP 400").hasMessageContaining("missing parameter: o");
        gamma.verify(1, getRequestedFor(urlPathEqualTo(EXPORT)));
        assertThat(adapter.circuitFailedCalls()).isEqualTo(recordedFailures);
    }

    @Test
    void should_report_error_after_one_retry_when_export_is_truncated() {
        gamma.stubFor(get(urlPathEqualTo(EXPORT)).willReturn(csv(fixture("truncated-no-end-marker.csv"))));

        ProviderUnavailableException failure = fetchFailure();

        assertThat(failure.status()).isEqualTo(ProviderStatus.ERROR);
        assertThat(failure.getCause()).isInstanceOf(TruncatedStream.class);
        gamma.verify(2, getRequestedFor(urlPathEqualTo(EXPORT)));
    }

    @Test
    void should_report_error_after_exactly_one_request_when_header_breaks_the_contract() {
        gamma.stubFor(get(urlPathEqualTo(EXPORT)).willReturn(csv("<html>gateway</html>\n#END\n")));

        ProviderUnavailableException failure = fetchFailure();

        assertThat(failure.status()).isEqualTo(ProviderStatus.ERROR);
        assertThat(failure.getCause()).isInstanceOf(ProviderContractViolation.class);
        gamma.verify(1, getRequestedFor(urlPathEqualTo(EXPORT)));
    }

    @Test
    void should_report_timeout_within_budget_and_not_retry_when_gamma_is_too_slow() {
        gamma.stubFor(get(urlPathEqualTo(EXPORT))
                .willReturn(csv(fixture("contract-example.csv")).withFixedDelay(3_000)));

        long started = System.nanoTime();
        ProviderUnavailableException failure = fetchFailure();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(failure.status()).isEqualTo(ProviderStatus.TIMEOUT);
        assertThat(elapsed).isLessThan(TIMEOUT.plusMillis(700));
        gamma.verify(1, getRequestedFor(urlPathEqualTo(EXPORT)));
    }

    @Test
    void should_report_circuit_open_without_calling_gamma_when_failures_fill_the_window() {
        gamma.stubFor(get(urlPathEqualTo(EXPORT)).willReturn(aResponse().withStatus(500)));
        // Each failing fetch is two attempts (one retry), so WINDOW / 2 fetches fill the window with failures.
        for (int i = 0; i < WINDOW / 2; i++) {
            assertThat(fetchFailure().status()).isEqualTo(ProviderStatus.ERROR);
        }
        gamma.verify(WINDOW, getRequestedFor(urlPathEqualTo(EXPORT)));

        assertThat(fetchFailure().status()).isEqualTo(ProviderStatus.CIRCUIT_OPEN);
        gamma.verify(WINDOW, getRequestedFor(urlPathEqualTo(EXPORT)));
    }

    private static ResponseDefinitionBuilder csv(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "text/csv; charset=UTF-8").withBody(body);
    }

    private ProviderUnavailableException fetchFailure() {
        ProviderUnavailableException failure =
                catchThrowableOfType(ProviderUnavailableException.class, () -> adapter.fetch(MAD_JFK));
        assertThat(failure).as("fetch should fail with ProviderUnavailableException").isNotNull();
        return failure;
    }
}
