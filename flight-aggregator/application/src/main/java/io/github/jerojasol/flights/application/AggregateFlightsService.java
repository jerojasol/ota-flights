package io.github.jerojasol.flights.application;

import io.github.jerojasol.flights.domain.model.AggregationResult;
import io.github.jerojasol.flights.domain.model.Itinerary;
import io.github.jerojasol.flights.domain.model.ItineraryDeduplicator;
import io.github.jerojasol.flights.domain.model.ItineraryFactory;
import io.github.jerojasol.flights.domain.model.Offer;
import io.github.jerojasol.flights.domain.model.OfferValidator;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.ProviderOutcome;
import io.github.jerojasol.flights.domain.model.ProviderStatus;
import io.github.jerojasol.flights.domain.model.ProviderUnavailableException;
import io.github.jerojasol.flights.domain.model.SearchCriteria;
import io.github.jerojasol.flights.domain.model.ValidationSummary;
import io.github.jerojasol.flights.domain.port.in.AggregateFlightsUseCase;
import io.github.jerojasol.flights.domain.port.out.FlightProviderPort;
import io.github.jerojasol.flights.domain.port.out.ItineraryRepositoryPort;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Runs one Aggregation: fan-out to every Provider, validate, deduplicate, apply markup, persist. A failing or slow
 * Provider yields an outcome and no Offers; it never aborts the pipeline (domain-model.md §9). Only a JVM
 * {@link Error} thrown by a provider propagates out of {@link #aggregate}.
 */
public final class AggregateFlightsService implements AggregateFlightsUseCase {

    private final List<FlightProviderPort> providers;
    private final OfferValidator validator;
    private final ItineraryDeduplicator deduplicator;
    private final ItineraryFactory factory;
    private final ItineraryRepositoryPort repository;
    private final Executor executor;
    private final Duration providerBudget;
    private final Clock clock;

    public AggregateFlightsService(List<FlightProviderPort> providers,
                                   OfferValidator validator,
                                   ItineraryDeduplicator deduplicator,
                                   ItineraryFactory factory,
                                   ItineraryRepositoryPort repository,
                                   Executor executor,
                                   Duration providerBudget,
                                   Clock clock) {
        this.providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
        this.validator = Objects.requireNonNull(validator, "validator");
        this.deduplicator = Objects.requireNonNull(deduplicator, "deduplicator");
        this.factory = Objects.requireNonNull(factory, "factory");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.providerBudget = Objects.requireNonNull(providerBudget, "providerBudget");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (this.providers.isEmpty()) {
            throw new IllegalArgumentException("At least one provider is required");
        }
        if (this.providers.stream().map(FlightProviderPort::provider).distinct().count() != this.providers.size()) {
            throw new IllegalArgumentException("Each provider may be registered only once");
        }
        if (providerBudget.isNegative() || providerBudget.isZero()) {
            throw new IllegalArgumentException("Provider budget must be positive");
        }
    }

    @Override
    public AggregationResult aggregate(SearchCriteria criteria) {
        Objects.requireNonNull(criteria, "criteria");
        Map<Provider, ProviderFetch> fetches = fanOut(criteria);

        Map<Provider, ProviderOutcome> outcomes = new EnumMap<>(Provider.class);
        List<Offer> valid = new ArrayList<>();
        fetches.forEach((provider, fetch) -> {
            ValidationSummary summary = validator.validateAll(fetch.offers());
            valid.addAll(summary.valid());
            outcomes.put(provider, new ProviderOutcome(
                    fetch.status(), fetch.offers().size(), summary.rejected(), fetch.elapsed()));
        });

        List<Itinerary> itineraries = deduplicator.deduplicate(valid).stream().map(factory::create).toList();
        int persisted = itineraries.isEmpty() ? 0 : repository.upsertAll(itineraries);
        return new AggregationResult(persisted, outcomes, clock.instant());
    }

    private Map<Provider, ProviderFetch> fanOut(SearchCriteria criteria) {
        Map<Provider, CompletableFuture<ProviderFetch>> futures = new EnumMap<>(Provider.class);
        for (FlightProviderPort provider : providers) {
            futures.put(provider.provider(), submit(provider, criteria));
        }

        CompletableFuture.allOf(futures.values().toArray(CompletableFuture[]::new))
                .orTimeout(providerBudget.toNanos(), TimeUnit.NANOSECONDS)
                .exceptionally(budgetExceeded -> null)
                .join();

        // A provider that answers after the budget is ignored: its future is simply never read again.
        Map<Provider, ProviderFetch> fetches = new EnumMap<>(Provider.class);
        futures.forEach((provider, future) -> fetches.put(provider, read(future)));
        return fetches;
    }

    private ProviderFetch read(CompletableFuture<ProviderFetch> future) {
        try {
            return future.getNow(ProviderFetch.failed(ProviderStatus.TIMEOUT, providerBudget));
        } catch (CompletionException failed) {
            // fetch() turns every Exception into an outcome, so only an Error can get here: let it propagate.
            if (failed.getCause() instanceof Error error) {
                throw error;
            }
            throw failed;
        }
    }

    private CompletableFuture<ProviderFetch> submit(FlightProviderPort provider, SearchCriteria criteria) {
        long startedAt = System.nanoTime();
        try {
            return CompletableFuture.supplyAsync(() -> fetch(provider, criteria), executor);
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.completedFuture(
                    ProviderFetch.failed(ProviderStatus.ERROR, elapsedSince(startedAt)));
        }
    }

    private static ProviderFetch fetch(FlightProviderPort provider, SearchCriteria criteria) {
        long startedAt = System.nanoTime();
        try {
            List<Offer> offers = List.copyOf(provider.fetch(criteria));
            return new ProviderFetch(ProviderStatus.OK, offers, elapsedSince(startedAt));
        } catch (ProviderUnavailableException unavailable) {
            return ProviderFetch.failed(unavailable.status(), elapsedSince(startedAt));
        } catch (RuntimeException unexpected) {
            return ProviderFetch.failed(ProviderStatus.ERROR, elapsedSince(startedAt));
        }
    }

    private static Duration elapsedSince(long startedAtNanos) {
        return Duration.ofNanos(Math.max(0, System.nanoTime() - startedAtNanos));
    }

    private record ProviderFetch(ProviderStatus status, List<Offer> offers, Duration elapsed) {

        static ProviderFetch failed(ProviderStatus status, Duration elapsed) {
            return new ProviderFetch(status, List.of(), elapsed);
        }
    }
}
