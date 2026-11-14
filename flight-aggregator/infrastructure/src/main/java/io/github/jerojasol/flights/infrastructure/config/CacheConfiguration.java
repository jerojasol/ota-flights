package io.github.jerojasol.flights.infrastructure.config;

import io.github.jerojasol.flights.domain.port.out.FreshnessPort;
import io.github.jerojasol.flights.infrastructure.cache.RedisFreshnessAdapter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(AggregationProperties.class)
class CacheConfiguration {

    @Bean
    FreshnessPort freshnessPort(StringRedisTemplate redis) {
        return new RedisFreshnessAdapter(redis);
    }
}
