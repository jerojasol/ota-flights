package io.github.jerojasol.flights.infrastructure.provider.alpha;

import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.SearchCriteria;
import io.github.jerojasol.flights.infrastructure.provider.ProviderRestClients;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/** {@code GET {baseUrl}/flights?from=&to=&date=}. Any non-2xx is ERROR, with Alpha's body kept in the cause. */
final class AlphaClient {

    private final RestClient http;

    AlphaClient(String baseUrl, Duration timeout) {
        this.http = ProviderRestClients.create(baseUrl, timeout);
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
                        throw ProviderRestClients.httpError(Provider.ALPHA, response.getStatusCode().value(),
                                new String(bytes, StandardCharsets.UTF_8));
                    }
                    return bytes;
                });
        return AlphaResponseParser.parse(body);
    }
}
