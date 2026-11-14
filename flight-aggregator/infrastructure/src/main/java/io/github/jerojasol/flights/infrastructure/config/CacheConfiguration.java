package io.github.jerojasol.flights.infrastructure.config;

import io.github.jerojasol.flights.domain.port.out.FreshnessPort;
import io.github.jerojasol.flights.infrastructure.cache.RedisFreshnessAdapter;
import io.lettuce.core.ClientOptions;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
class CacheConfiguration {

    @Bean
    FreshnessPort freshnessPort(StringRedisTemplate redis) {
        return new RedisFreshnessAdapter(redis);
    }

    /**
     * While Redis is disconnected, Lettuce queues commands by default and each one waits for the command timeout.
     * Rejecting them at once lets RedisFreshnessAdapter degrade immediately, so a search during an outage costs no
     * Redis wait at all.
     */
    @Bean
    LettuceClientConfigurationBuilderCustomizer rejectCommandsWhileDisconnected() {
        return builder -> builder.clientOptions(ClientOptions.builder()
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build());
    }
}
