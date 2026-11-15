package io.github.jerojasol.flights.infrastructure.provider;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.util.function.Supplier;

/**
 * Reports one provider's circuit breaker. Always {@code UP}: a provider being down degrades search results but is not
 * a reason to restart this service, so it must never turn overall health DOWN. An open circuit shows as
 * {@code degraded: true}.
 */
public final class ProviderCircuitHealthIndicator implements HealthIndicator {

    private final Supplier<ProviderCircuitState> state;

    public ProviderCircuitHealthIndicator(Supplier<ProviderCircuitState> state) {
        this.state = state;
    }

    @Override
    public Health health() {
        ProviderCircuitState current = state.get();
        return Health.up()
                .withDetail("circuit", current.name())
                .withDetail("degraded", current == ProviderCircuitState.OPEN)
                .build();
    }
}
