package io.github.jerojasol.flights.infrastructure.provider;

import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.ProviderStatus;
import io.github.jerojasol.flights.domain.model.ProviderUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Retry(CircuitBreaker(TimeLimiter(call))) for one provider, with every failure translated to
 * {@link ProviderUnavailableException}:
 * <ul>
 *   <li>the attempt exceeds the timeout, or the HTTP client times out → {@code TIMEOUT} (recorded by the breaker,
 *       never retried);</li>
 *   <li>the breaker is open → {@code CIRCUIT_OPEN} (the provider is not called, never retried);</li>
 *   <li>a 2xx body that breaks the contract ({@link ProviderContractViolation}) → {@code ERROR}, not retried, but
 *       recorded by the breaker;</li>
 *   <li>anything else (HTTP error status, connection failure, truncated stream) → {@code ERROR}, retried.</li>
 * </ul>
 * Each provider gets its own instance, so one provider's breaker never affects another.
 */
public final class ProviderResilience {

    private final Provider provider;
    private final TimeLimiter timeLimiter;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Executor executor;

    public ProviderResilience(Provider provider, ResilienceSettings settings, Executor executor) {
        this.provider = provider;
        this.executor = executor;
        String name = provider.name().toLowerCase();
        this.timeLimiter = TimeLimiter.of(name, TimeLimiterConfig.custom()
                .timeoutDuration(settings.timeout())
                .cancelRunningFuture(true)
                .build());
        this.circuitBreaker = CircuitBreaker.of(name, CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.slidingWindowSize())
                .minimumNumberOfCalls(settings.slidingWindowSize())
                .failureRateThreshold(settings.failureRatePercent())
                .waitDurationInOpenState(settings.openStateWait())
                .build());
        this.retry = Retry.of(name, RetryConfig.custom()
                .maxAttempts(settings.maxAttempts())
                .waitDuration(settings.retryWait())
                .retryOnException(ProviderResilience::isRetryable)
                .build());
    }

    /** Only transport-level ERRORs are retried; a contract breach would fail the same way again. */
    private static boolean isRetryable(Throwable failure) {
        return failure instanceof ProviderUnavailableException unavailable
                && unavailable.status() == ProviderStatus.ERROR
                && !(unavailable.getCause() instanceof ProviderContractViolation);
    }

    public <T> T call(Supplier<T> attempt) {
        Callable<T> timeLimited = TimeLimiter.decorateFutureSupplier(timeLimiter,
                () -> CompletableFuture.supplyAsync(attempt, executor));
        Callable<T> guarded = CircuitBreaker.decorateCallable(circuitBreaker, () -> translateTimeout(timeLimited));
        Callable<T> retried = Retry.decorateCallable(retry, () -> translateOpenCircuit(guarded));
        try {
            return retried.call();
        } catch (ProviderUnavailableException unavailable) {
            throw unavailable;
        } catch (Exception unexpected) {
            throw new ProviderUnavailableException(provider, ProviderStatus.ERROR, unexpected);
        }
    }

    private <T> T translateTimeout(Callable<T> timeLimited) {
        try {
            return timeLimited.call();
        } catch (TimeoutException timedOut) {
            throw new ProviderUnavailableException(provider, ProviderStatus.TIMEOUT, timedOut);
        } catch (ExecutionException failed) {
            throw unavailable(failed.getCause());
        } catch (Exception failed) {
            throw unavailable(failed);
        }
    }

    private <T> T translateOpenCircuit(Callable<T> guarded) throws Exception {
        try {
            return guarded.call();
        } catch (CallNotPermittedException open) {
            throw new ProviderUnavailableException(provider, ProviderStatus.CIRCUIT_OPEN, open);
        }
    }

    private RuntimeException unavailable(Throwable cause) {
        if (cause instanceof ProviderUnavailableException unavailable) {
            return unavailable;
        }
        if (cause instanceof CompletionException completion && completion.getCause() != null) {
            return unavailable(completion.getCause());
        }
        if (cause instanceof Error error) {
            throw error;
        }
        if (isTimeout(cause)) {
            return new ProviderUnavailableException(provider, ProviderStatus.TIMEOUT, cause);
        }
        return new ProviderUnavailableException(provider, ProviderStatus.ERROR, cause);
    }

    /** An HTTP-level timeout (connect or read) is a TIMEOUT too, wherever it sits in the cause chain. */
    private static boolean isTimeout(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof HttpTimeoutException || current instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
