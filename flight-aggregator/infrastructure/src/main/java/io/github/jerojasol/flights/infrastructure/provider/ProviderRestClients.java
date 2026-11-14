package io.github.jerojasol.flights.infrastructure.provider;

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
}
