package io.github.jerojasol.flights.application.fakes;

import io.github.jerojasol.flights.domain.model.Offer;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.SearchCriteria;
import io.github.jerojasol.flights.domain.port.out.FlightProviderPort;

import java.time.Duration;
import java.util.List;

public final class StubProvider implements FlightProviderPort {

    private final Provider provider;
    private final List<Offer> offers;
    private final Throwable failure;
    private final Duration delay;

    private StubProvider(Provider provider, List<Offer> offers, Throwable failure, Duration delay) {
        this.provider = provider;
        this.offers = offers;
        this.failure = failure;
        this.delay = delay;
    }

    public static StubProvider returning(Provider provider, Offer... offers) {
        return new StubProvider(provider, List.of(offers), null, Duration.ZERO);
    }

    public static StubProvider failing(Provider provider, RuntimeException failure) {
        return new StubProvider(provider, List.of(), failure, Duration.ZERO);
    }

    public static StubProvider failing(Provider provider, Error failure) {
        return new StubProvider(provider, List.of(), failure, Duration.ZERO);
    }

    public StubProvider delayedBy(Duration delay) {
        return new StubProvider(provider, offers, failure, delay);
    }

    @Override
    public Provider provider() {
        return provider;
    }

    @Override
    public List<Offer> fetch(SearchCriteria criteria) {
        if (!delay.isZero()) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Stub provider interrupted", e);
            }
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
        return offers;
    }
}
