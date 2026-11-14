package io.github.jerojasol.flights.infrastructure.config;

import io.github.jerojasol.flights.infrastructure.provider.ProviderCircuitHealthIndicator;
import io.github.jerojasol.flights.infrastructure.provider.ResilienceSettings;
import io.github.jerojasol.flights.infrastructure.provider.alpha.AlphaProviderAdapter;
import io.github.jerojasol.flights.infrastructure.provider.beta.BetaProviderAdapter;
import io.github.jerojasol.flights.infrastructure.provider.gamma.GammaProviderAdapter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The three provider adapters, one per Provider, and a health indicator per circuit breaker (bean names end in
 * {@code HealthIndicator}, so they show as {@code alphaProvider}, {@code betaProvider}, {@code gammaProvider}).
 */
@Configuration
@EnableConfigurationProperties(ProviderProperties.class)
class ProviderConfiguration {

    /** Runs provider HTTP calls under the TimeLimiter; one virtual thread per call. */
    @Bean(destroyMethod = "close")
    ExecutorService providerCallExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    AlphaProviderAdapter alphaProvider(ProviderProperties properties,
                                       @Qualifier("providerCallExecutor") ExecutorService providerCallExecutor) {
        return new AlphaProviderAdapter(properties.alpha().baseUrl(),
                ResilienceSettings.defaults(properties.timeout()), providerCallExecutor);
    }

    @Bean
    BetaProviderAdapter betaProvider(ProviderProperties properties,
                                     @Qualifier("providerCallExecutor") ExecutorService providerCallExecutor) {
        return new BetaProviderAdapter(properties.beta().baseUrl(),
                ResilienceSettings.defaults(properties.timeout()), providerCallExecutor);
    }

    @Bean
    GammaProviderAdapter gammaProvider(ProviderProperties properties,
                                       @Qualifier("providerCallExecutor") ExecutorService providerCallExecutor) {
        return new GammaProviderAdapter(properties.gamma().baseUrl(),
                ResilienceSettings.defaults(properties.timeout()), providerCallExecutor);
    }

    @Bean
    ProviderCircuitHealthIndicator alphaProviderHealthIndicator(AlphaProviderAdapter alpha) {
        return new ProviderCircuitHealthIndicator(alpha::circuitState);
    }

    @Bean
    ProviderCircuitHealthIndicator betaProviderHealthIndicator(BetaProviderAdapter beta) {
        return new ProviderCircuitHealthIndicator(beta::circuitState);
    }

    @Bean
    ProviderCircuitHealthIndicator gammaProviderHealthIndicator(GammaProviderAdapter gamma) {
        return new ProviderCircuitHealthIndicator(gamma::circuitState);
    }
}
