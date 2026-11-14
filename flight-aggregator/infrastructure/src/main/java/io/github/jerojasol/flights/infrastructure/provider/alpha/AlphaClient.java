package io.github.jerojasol.flights.infrastructure.provider.alpha;

import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.ProviderStatus;
import io.github.jerojasol.flights.domain.model.ProviderUnavailableException;
import io.github.jerojasol.flights.domain.model.SearchCriteria;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/** {@code GET {baseUrl}/flights?from=&to=&date=}. Any non-2xx is ERROR, with Alpha's body kept in the cause. */
final class AlphaClient {

    private final RestClient http;

    AlphaClient(String baseUrl, Duration timeout) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        // Backstop only: the TimeLimiter must fire first so a slow provider is reported as TIMEOUT once, not
        // retried as an I/O error. The read timeout then stops the abandoned HTTP call instead of leaving it open.
        requests.setReadTimeout(timeout.multipliedBy(2));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
    }

    List<AlphaItinerary> fetch(SearchCriteria criteria) {
        byte[] body = http.get()
                .uri(uri -> uri.path("/flights")
                        .queryParam("from", criteria.origin().code())
                        .queryParam("to", criteria.destination().code())
                        .queryParam("date", criteria.departureDate().toString())
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange((request, response) -> {
                    byte[] bytes = response.getBody().readAllBytes();
                    if (response.getStatusCode().isError()) {
                        throw new ProviderUnavailableException(Provider.ALPHA, ProviderStatus.ERROR,
                                new IllegalStateException("Alpha answered HTTP " + response.getStatusCode().value()
                                        + ": " + new String(bytes, StandardCharsets.UTF_8)));
                    }
                    return bytes;
                });
        return AlphaResponseParser.parse(body);
    }
}
