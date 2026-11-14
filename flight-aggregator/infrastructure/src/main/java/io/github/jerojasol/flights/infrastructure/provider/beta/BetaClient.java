package io.github.jerojasol.flights.infrastructure.provider.beta;

import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.ProviderStatus;
import io.github.jerojasol.flights.domain.model.ProviderUnavailableException;
import io.github.jerojasol.flights.domain.model.SearchCriteria;
import io.github.jerojasol.flights.infrastructure.provider.ProviderRestClients;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** {@code GET {baseUrl}/search?origin=&destination=&departureDate=}. Any non-2xx is ERROR, with Beta's Fault kept in the cause. */
final class BetaClient {

    private final RestClient http;

    BetaClient(String baseUrl, Duration timeout) {
        this.http = ProviderRestClients.create(baseUrl, timeout);
    }

    BetaSearchResponse fetch(SearchCriteria criteria) {
        byte[] body = http.get()
                .uri(uri -> uri.path("/search")
                        .queryParam("origin", criteria.origin().code())
                        .queryParam("destination", criteria.destination().code())
                        .queryParam("departureDate", criteria.departureDate().toString())
                        .build())
                .accept(MediaType.APPLICATION_XML)
                .exchange((request, response) -> {
                    byte[] bytes = response.getBody().readAllBytes();
                    if (response.getStatusCode().isError()) {
                        throw new ProviderUnavailableException(Provider.BETA, ProviderStatus.ERROR,
                                new IllegalStateException("Beta answered HTTP " + response.getStatusCode().value()
                                        + ": " + new String(bytes, StandardCharsets.UTF_8)));
                    }
                    return bytes;
                });
        return BetaResponseParser.parse(body);
    }
}
