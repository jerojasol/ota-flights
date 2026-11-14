package io.github.jerojasol.flights.infrastructure.config;

import io.github.jerojasol.flights.application.AggregateFlightsService;
import io.github.jerojasol.flights.application.RefreshItinerariesService;
import io.github.jerojasol.flights.application.SearchItinerariesService;
import io.github.jerojasol.flights.domain.model.ItineraryDeduplicator;
import io.github.jerojasol.flights.domain.model.ItineraryFactory;
import io.github.jerojasol.flights.domain.model.MarkupPolicy;
import io.github.jerojasol.flights.domain.model.OfferValidator;
import io.github.jerojasol.flights.domain.model.PercentageMarkupPolicy;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.port.in.AggregateFlightsUseCase;
import io.github.jerojasol.flights.domain.port.in.RefreshItinerariesUseCase;
import io.github.jerojasol.flights.domain.port.in.SearchItinerariesUseCase;
import io.github.jerojasol.flights.domain.port.out.FlightProviderPort;
import io.github.jerojasol.flights.domain.port.out.FreshnessPort;
import io.github.jerojasol.flights.domain.port.out.ItineraryRepositoryPort;
import io.github.jerojasol.flights.infrastructure.observability.ProviderOutcomeLogger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Composition root for the domain and application layers, which carry no Spring annotations. Every value comes from
 * {@link AggregationProperties}, the only place {@code aggregation.*} is bound, so both services share one set of TTLs
 * and the reference currency has a single source.
 */
@Configuration
@EnableConfigurationProperties(AggregationProperties.class)
class UseCaseConfiguration {

    static final int REFRESH_THREADS = 4;
    static final int REFRESH_QUEUE = 64;

    /** The one clock: ingestedAt (ItineraryFactory) and aggregatedAt (AggregateFlightsService) come from it. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    OfferValidator offerValidator(AggregationProperties aggregation) {
        return new OfferValidator(aggregation.referenceCurrency());
    }

    @Bean
    ItineraryDeduplicator itineraryDeduplicator() {
        return new ItineraryDeduplicator();
    }

    @Bean
    MarkupPolicy markupPolicy(AggregationProperties aggregation) {
        return new PercentageMarkupPolicy(aggregation.markupPercentage());
    }

    @Bean
    ItineraryFactory itineraryFactory(MarkupPolicy markupPolicy, Clock clock) {
        return new ItineraryFactory(markupPolicy, clock);
    }

    /** Fan-out to providers: one virtual thread per provider call of an aggregation. */
    @Bean(destroyMethod = "close")
    ExecutorService providerFanOutExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Background refreshes of STALE routes: few threads and a bounded queue, so a burst of stale searches cannot
     * stampede the providers. A refused task is fine: SearchItinerariesService releases the lock and the route is
     * refreshed by a later search.
     */
    @Bean(destroyMethod = "shutdown")
    ExecutorService refreshExecutor() {
        return new ThreadPoolExecutor(REFRESH_THREADS, REFRESH_THREADS, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(REFRESH_QUEUE), Thread.ofVirtual().name("refresh-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    AggregateFlightsUseCase aggregateFlightsUseCase(List<FlightProviderPort> providers, OfferValidator validator,
                                                    ItineraryDeduplicator deduplicator, ItineraryFactory factory,
                                                    ItineraryRepositoryPort repository,
                                                    @Qualifier("providerFanOutExecutor") ExecutorService fanOut,
                                                    AggregationProperties aggregation, ProviderProperties providerProperties,
                                                    Clock clock) {
        requireOneAdapterPerProvider(providers);
        // A provider's own timeout must fire before the fan-out budget, or its breaker never sees the slowness.
        if (providerProperties.timeout().compareTo(aggregation.providerBudget()) >= 0) {
            throw new IllegalStateException("providers.timeout (" + providerProperties.timeout()
                    + ") must be shorter than aggregation.provider-budget (" + aggregation.providerBudget() + ")");
        }
        return new ProviderOutcomeLogger(new AggregateFlightsService(providers, validator, deduplicator, factory,
                repository, fanOut, aggregation.providerBudget(), clock));
    }

    @Bean
    SearchItinerariesUseCase searchItinerariesUseCase(ItineraryRepositoryPort repository, FreshnessPort freshness,
                                                      AggregateFlightsUseCase aggregateFlights,
                                                      @Qualifier("refreshExecutor") ExecutorService refreshExecutor,
                                                      AggregationProperties aggregation) {
        return new SearchItinerariesService(repository, freshness, aggregateFlights, refreshExecutor,
                aggregation.freshnessTtl(), aggregation.lockTtl());
    }

    @Bean
    RefreshItinerariesUseCase refreshItinerariesUseCase(AggregateFlightsUseCase aggregateFlights,
                                                        FreshnessPort freshness, AggregationProperties aggregation) {
        return new RefreshItinerariesService(aggregateFlights, freshness, aggregation.freshnessTtl(),
                aggregation.lockTtl());
    }

    private static void requireOneAdapterPerProvider(List<FlightProviderPort> providers) {
        Set<Provider> registered = providers.stream().map(FlightProviderPort::provider)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Provider.class)));
        Set<Provider> missing = EnumSet.allOf(Provider.class);
        missing.removeAll(registered);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("No provider adapter registered for " + missing);
        }
        if (registered.size() != providers.size()) {
            throw new IllegalStateException("More than one provider adapter registered for the same Provider");
        }
    }
}
