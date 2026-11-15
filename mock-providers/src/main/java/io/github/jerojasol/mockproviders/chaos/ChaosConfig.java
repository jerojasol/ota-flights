package io.github.jerojasol.mockproviders.chaos;

import java.time.Duration;
import java.util.Objects;

public record ChaosConfig(boolean enabled, Duration latencyMin, Duration latencyMax, double errorRate) {

    public static final Duration MAX_LATENCY = Duration.ofSeconds(30);

    public ChaosConfig {
        Objects.requireNonNull(latencyMin, "latencyMin");
        Objects.requireNonNull(latencyMax, "latencyMax");
        if (latencyMin.isNegative()) {
            throw new IllegalArgumentException("latencyMin must not be negative");
        }
        if (latencyMin.compareTo(latencyMax) > 0) {
            throw new IllegalArgumentException("latencyMin must not exceed latencyMax");
        }
        if (latencyMax.compareTo(MAX_LATENCY) > 0) {
            throw new IllegalArgumentException("latencyMax must not exceed 30s");
        }
        if (!(errorRate >= 0.0 && errorRate <= 1.0)) {
            throw new IllegalArgumentException("errorRate must be in [0, 1]");
        }
    }
}
