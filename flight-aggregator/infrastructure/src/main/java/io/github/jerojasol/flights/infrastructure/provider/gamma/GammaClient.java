package io.github.jerojasol.flights.infrastructure.provider.gamma;

import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.SearchCriteria;
import io.github.jerojasol.flights.infrastructure.provider.ProviderRestClients;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;

/**
 * {@code GET {baseUrl}/export?o=&d=&dt=yyyyMMdd}. The 200 body is handed to {@code reader} while it streams, inside
 * the exchange, so the connection is always closed when reading ends. Any non-2xx is ERROR with Gamma's text kept.
 */
final class GammaClient {

    /** Consumes Gamma's streamed body; runs while the connection is open. */
    @FunctionalInterface
    interface BodyReader<T> {
        T read(BufferedReader body) throws IOException;
    }

    private static final int MAX_ERROR_BODY_BYTES = 2_048;

    private final RestClient http;

    GammaClient(String baseUrl, Duration timeout) {
        this.http = ProviderRestClients.create(baseUrl, timeout);
    }

    <T> T fetch(SearchCriteria criteria, BodyReader<T> reader) {
        return http.get()
                .uri(uri -> uri.path("/export")
                        .queryParam("o", criteria.origin().code())
                        .queryParam("d", criteria.destination().code())
                        .queryParam("dt", criteria.departureDate().format(DateTimeFormatter.BASIC_ISO_DATE))
                        .build())
                .accept(MediaType.parseMediaType("text/csv"))
                .exchange((request, response) -> {
                    if (response.getStatusCode().isError()) {
                        byte[] text = response.getBody().readNBytes(MAX_ERROR_BODY_BYTES);
                        throw ProviderRestClients.httpError(Provider.GAMMA, response.getStatusCode().value(),
                                new String(text, StandardCharsets.UTF_8));
                    }
                    try (BufferedReader body = new BufferedReader(
                            new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        return reader.read(body);
                    }
                });
    }
}
