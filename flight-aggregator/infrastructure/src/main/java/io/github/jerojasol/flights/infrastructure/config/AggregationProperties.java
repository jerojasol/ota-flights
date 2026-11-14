package io.github.jerojasol.flights.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Currency;

/**
 * Binds {@code aggregation.*} from application.yaml. {@code freshnessTtl} is how long an aggregation counts as FRESH;
 * {@code lockTtl} bounds how long a crashed holder can keep the aggregation lock; {@code referenceCurrency} is the one
 * currency an aggregation works in (domain-model.md §10), also used for the search API's {@code maxPrice}.
 */
@ConfigurationProperties("aggregation")
public record AggregationProperties(Duration freshnessTtl, Duration lockTtl, Currency referenceCurrency) {

    public AggregationProperties {
        if (referenceCurrency == null) {
            throw new IllegalArgumentException("aggregation.reference-currency is required");
        }
        if (freshnessTtl == null || freshnessTtl.isNegative() || freshnessTtl.isZero()) {
            throw new IllegalArgumentException("aggregation.freshness-ttl must be positive");
        }
        if (lockTtl == null || lockTtl.isNegative() || lockTtl.isZero()) {
            throw new IllegalArgumentException("aggregation.lock-ttl must be positive");
        }
    }
}
