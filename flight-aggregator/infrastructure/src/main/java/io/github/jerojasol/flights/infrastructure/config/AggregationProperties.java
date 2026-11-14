package io.github.jerojasol.flights.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Binds {@code aggregation.*} from application.yaml and fails startup with a clear message when the values cannot work
 * together.
 *
 * @param markupPercentage  Markup Policy percentage applied to the Base Price (default 5)
 * @param referenceCurrency the one currency an aggregation works in (domain-model.md §10); also the search API's
 *                          {@code maxPrice} currency. The only currency property: do not add a second one
 * @param freshnessTtl      how long an aggregation counts as FRESH
 * @param lockTtl           how long a crashed holder can keep the aggregation lock
 * @param providerBudget    the fan-out budget: how long an aggregation waits for all providers
 * @param warmRoutes        routes aggregated on a schedule, {@code AAA-BBB}; empty disables the scheduler
 * @param warmInterval      pause between two scheduled runs
 */
@ConfigurationProperties("aggregation")
public record AggregationProperties(BigDecimal markupPercentage, Currency referenceCurrency, Duration freshnessTtl,
                                    Duration lockTtl, Duration providerBudget, List<String> warmRoutes,
                                    Duration warmInterval) {

    private static final Pattern ROUTE = Pattern.compile("[A-Za-z]{3}-[A-Za-z]{3}");

    public AggregationProperties {
        require(markupPercentage != null && markupPercentage.signum() >= 0,
                "aggregation.markup-percentage must be zero or more");
        require(referenceCurrency != null, "aggregation.reference-currency is required");
        requirePositive(freshnessTtl, "aggregation.freshness-ttl");
        requirePositive(lockTtl, "aggregation.lock-ttl");
        requirePositive(providerBudget, "aggregation.provider-budget");
        requirePositive(warmInterval, "aggregation.warm-interval");
        // The lock must outlive a whole aggregation (fan-out + validation + upsert + markFresh), or a second
        // aggregation of the same route can start while the first one still runs.
        require(lockTtl.compareTo(providerBudget.multipliedBy(2)) >= 0,
                "aggregation.lock-ttl (" + lockTtl + ") must be at least twice aggregation.provider-budget ("
                        + providerBudget + ")");
        require(freshnessTtl.compareTo(lockTtl) > 0,
                "aggregation.freshness-ttl (" + freshnessTtl + ") must be longer than aggregation.lock-ttl ("
                        + lockTtl + ")");
        warmRoutes = warmRoutes == null ? List.of() : warmRoutes.stream().map(String::strip)
                .filter(route -> !route.isEmpty()).toList();
        for (String route : warmRoutes) {
            require(ROUTE.matcher(route).matches(),
                    "aggregation.warm-routes entry '" + route + "' must look like MAD-JFK");
        }
        // Refreshing more often than data goes stale only calls the providers for nothing. Checked only when the
        // scheduler runs: with no warm routes the interval is unused.
        require(warmRoutes.isEmpty() || warmInterval.compareTo(freshnessTtl) >= 0,
                "aggregation.warm-interval (" + warmInterval + ") must be at least aggregation.freshness-ttl ("
                        + freshnessTtl + ") when aggregation.warm-routes is set");
    }

    private static void requirePositive(Duration value, String name) {
        require(value != null && !value.isNegative() && !value.isZero(), name + " must be positive");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
