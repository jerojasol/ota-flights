package io.github.jerojasol.flights.infrastructure.config;

import io.github.jerojasol.flights.infrastructure.web.SearchRequestMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class WebConfiguration {

    @Bean
    SearchRequestMapper searchRequestMapper(AggregationProperties aggregation) {
        return new SearchRequestMapper(aggregation.referenceCurrency());
    }
}
