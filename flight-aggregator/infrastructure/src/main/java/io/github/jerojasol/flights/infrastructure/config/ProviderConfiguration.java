package io.github.jerojasol.flights.infrastructure.config;

import io.github.jerojasol.flights.domain.port.out.FlightProviderPort;
import io.github.jerojasol.flights.infrastructure.provider.ResilienceSettings;
import io.github.jerojasol.flights.infrastructure.provider.alpha.AlphaProviderAdapter;
import io.github.jerojasol.flights.infrastructure.provider.beta.BetaProviderAdapter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableConfigurationProperties(ProviderProperties.class)
class ProviderConfiguration {

    /** Runs provider HTTP calls under the TimeLimiter; one virtual thread per call. */
    @Bean(destroyMethod = "close")
    ExecutorService providerCallExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    FlightProviderPort alphaProvider(ProviderProperties properties,
                                     @Qualifier("providerCallExecutor") ExecutorService providerCallExecutor) {
        return new AlphaProviderAdapter(properties.alpha().baseUrl(),
                ResilienceSettings.defaults(properties.timeout()), providerCallExecutor);
    }

    @Bean
    FlightProviderPort betaProvider(ProviderProperties properties,
                                    @Qualifier("providerCallExecutor") ExecutorService providerCallExecutor) {
        return new BetaProviderAdapter(properties.beta().baseUrl(),
                ResilienceSettings.defaults(properties.timeout()), providerCallExecutor);
    }
}
