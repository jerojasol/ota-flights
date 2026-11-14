package io.github.jerojasol.mockproviders.chaos;

import java.util.Random;

import io.github.jerojasol.mockproviders.alpha.AlphaErrorWriter;
import io.github.jerojasol.mockproviders.beta.BetaFaultWriter;
import io.github.jerojasol.mockproviders.config.MockProperties;
import io.github.jerojasol.mockproviders.gamma.GammaErrorWriter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ChaosConfiguration {

    @Bean
    ChaosState chaosState(MockProperties properties) {
        return new ChaosState(properties.chaos());
    }

    /** Only the provider paths: {@code /actuator/**} and {@code /admin/**} must never see chaos. */
    @Bean
    FilterRegistrationBean<ChaosFilter> chaosFilter(ChaosState state, MockProperties properties,
                                                    AlphaErrorWriter alphaErrors, BetaFaultWriter betaFaults,
                                                    GammaErrorWriter gammaErrors) {
        // Seeded apart from the generator (seed + 1): toggling chaos never changes the inventory.
        ChaosFilter filter = new ChaosFilter(state, new Random(properties.seed() + 1), alphaErrors, betaFaults,
                gammaErrors);
        FilterRegistrationBean<ChaosFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/alpha/*", "/beta/*", "/gamma/*");
        registration.setName("chaosFilter");
        return registration;
    }
}
