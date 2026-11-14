package io.github.jerojasol.flights.infrastructure.provider;

import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.ProviderStatus;
import io.github.jerojasol.flights.domain.model.ProviderUnavailableException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** The HTTP client every provider adapter uses, so all three share the same timeout behaviour. */
public final class ProviderRestClients {

    private ProviderRestClients() {
    }

    public static RestClient create(String baseUrl, Duration timeout) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        // Backstop only: the TimeLimiter must fire first so a slow provider is reported as TIMEOUT once, not
        // retried as an I/O error. The read timeout then stops the abandoned HTTP call instead of leaving it open.
        requests.setReadTimeout(timeout.multipliedBy(2));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
    }

    /**
     * The failure for a non-2xx answer, with the provider's own text kept in the cause. A 4xx other than 408 (request
     * timeout) and 429 (too many requests) means our request was wrong: {@link ProviderRequestRejected}, never retried.
     * 408, 429 and 5xx are the provider's transient trouble and stay retryable.
     */
    public static ProviderUnavailableException httpError(Provider provider, int status, String body) {
        String message = provider + " answered HTTP " + status + ": " + body.strip();
        RuntimeException cause = isRejection(status)
                ? new ProviderRequestRejected(message)
                : new IllegalStateException(message);
        return new ProviderUnavailableException(provider, ProviderStatus.ERROR, cause);
    }

    private static boolean isRejection(int status) {
        return status >= 400 && status < 500 && status != 408 && status != 429;
    }
}
