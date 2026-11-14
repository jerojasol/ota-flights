package io.github.jerojasol.flights.infrastructure.config;

import io.github.jerojasol.flights.domain.port.in.RefreshItinerariesUseCase;
import io.github.jerojasol.flights.infrastructure.scheduling.WarmRoutesScheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
class SchedulingConfiguration {

    @Bean
    WarmRoutesScheduler warmRoutesScheduler(RefreshItinerariesUseCase refresh, AggregationProperties aggregation,
                                            Clock clock) {
        return new WarmRoutesScheduler(refresh, aggregation.warmRoutes(), clock);
    }
}
