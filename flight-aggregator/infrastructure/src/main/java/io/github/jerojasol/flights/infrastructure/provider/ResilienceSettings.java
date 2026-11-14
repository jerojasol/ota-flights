package io.github.jerojasol.flights.infrastructure.provider;

import java.time.Duration;

/**
 * Resilience4j settings for one provider. {@link #defaults(Duration)} holds the agreed production values; tests pass
 * shorter waits so they stay fast.
 *
 * @param timeout            TimeLimiter budget for one attempt; also the HTTP read timeout
 * @param slidingWindowSize  CircuitBreaker count-based window, and the minimum calls before it can open
 * @param failureRatePercent CircuitBreaker failure rate that opens it
 * @param openStateWait      how long the CircuitBreaker stays open before letting a probe through
 * @param maxAttempts        Retry attempts in total (first call included), only for ERROR
 * @param retryWait          pause between Retry attempts
 */
public record ResilienceSettings(Duration timeout, int slidingWindowSize, float failureRatePercent,
                                 Duration openStateWait, int maxAttempts, Duration retryWait) {

    public ResilienceSettings {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Provider timeout must be positive");
        }
        if (slidingWindowSize < 1 || maxAttempts < 1) {
            throw new IllegalArgumentException("Window size and attempts must be at least 1");
        }
        if (failureRatePercent <= 0 || failureRatePercent > 100) {
            throw new IllegalArgumentException("Failure rate must be in (0, 100]");
        }
        if (openStateWait == null || openStateWait.isNegative() || retryWait == null || retryWait.isNegative()) {
            throw new IllegalArgumentException("Waits must not be negative");
        }
    }

    public static ResilienceSettings defaults(Duration timeout) {
        return new ResilienceSettings(timeout, 10, 50f, Duration.ofSeconds(30), 2, Duration.ofMillis(200));
    }
}
