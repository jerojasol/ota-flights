package io.github.jerojasol.flights.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Binds {@code providers.*} from application.yaml; base URLs come from PROVIDER_*_URL environment variables. */
@ConfigurationProperties("providers")
public record ProviderProperties(Endpoint alpha, Endpoint beta, Endpoint gamma, Duration timeout) {

    public record Endpoint(String baseUrl) {
    }
}
