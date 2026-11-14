package io.github.jerojasol.flights.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.ProviderStatus;
import io.github.jerojasol.flights.domain.model.ProviderUnavailableException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProviderRestClientsTest {

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 422})
    void should_mark_request_as_rejected_when_provider_answers_4xx_other_than_408_and_429(int status) {
        ProviderUnavailableException failure = ProviderRestClients.httpError(Provider.BETA, status, "fault");

        assertThat(failure.status()).isEqualTo(ProviderStatus.ERROR);
        assertThat(failure.getCause()).isInstanceOf(ProviderRequestRejected.class)
                .hasMessageContaining("HTTP " + status).hasMessageContaining("fault");
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 429, 500, 502, 503})
    void should_keep_failure_retryable_when_provider_answers_408_429_or_5xx(int status) {
        ProviderUnavailableException failure = ProviderRestClients.httpError(Provider.BETA, status, "busy");

        assertThat(failure.status()).isEqualTo(ProviderStatus.ERROR);
        assertThat(failure.getCause()).isNotInstanceOf(ProviderRequestRejected.class)
                .hasMessageContaining("HTTP " + status);
    }
}
