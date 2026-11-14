package io.github.jerojasol.flights.application;

import io.github.jerojasol.flights.application.fakes.InMemoryItineraryRepository;
import io.github.jerojasol.flights.application.fakes.StubProvider;
import io.github.jerojasol.flights.domain.model.AggregationResult;
import io.github.jerojasol.flights.domain.model.Itinerary;
import io.github.jerojasol.flights.domain.model.ItineraryDeduplicator;
import io.github.jerojasol.flights.domain.model.ItineraryFactory;
import io.github.jerojasol.flights.domain.model.Offer;
import io.github.jerojasol.flights.domain.model.OfferValidator;
import io.github.jerojasol.flights.domain.model.PercentageMarkupPolicy;
import io.github.jerojasol.flights.domain.model.Price;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.ProviderOutcome;
import io.github.jerojasol.flights.domain.model.ProviderStatus;
import io.github.jerojasol.flights.domain.model.ProviderUnavailableException;
import io.github.jerojasol.flights.domain.port.out.FlightProviderPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.github.jerojasol.flights.application.Fixtures.MAD_JFK;
import static io.github.jerojasol.flights.application.Fixtures.direct;
import static io.github.jerojasol.flights.application.Fixtures.shortConnection;
import static io.github.jerojasol.flights.domain.model.Provider.ALPHA;
import static io.github.jerojasol.flights.domain.model.Provider.BETA;
import static io.github.jerojasol.flights.domain.model.Provider.GAMMA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AggregateFlightsServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration BUDGET = Duration.ofSeconds(5);
    private static final Executor DIRECT = Runnable::run;

    private final InMemoryItineraryRepository repository = new InMemoryItineraryRepository();
    private final ExecutorService threads = Executors.newCachedThreadPool();

    @AfterEach
    void stopThreads() {
        threads.shutdownNow();
    }

    @Test
    void should_persist_one_itinerary_per_distinct_key_when_all_providers_return_overlapping_offers() {
        AggregateFlightsService service = service(DIRECT, BUDGET,
                StubProvider.returning(ALPHA, direct(ALPHA, "IB6251", "500.00"), direct(ALPHA, "IB6253", "620.00")),
                StubProvider.returning(BETA, direct(BETA, "IB6251", "480.00")),
                StubProvider.returning(GAMMA, direct(GAMMA, "IB6251", "510.00"), direct(GAMMA, "IB6253", "600.00"),
                        direct(GAMMA, "UX91", "450.00")));

        AggregationResult result = service.aggregate(MAD_JFK);

        assertThat(result.persisted()).isEqualTo(3);
        assertThat(result.aggregatedAt()).isEqualTo(NOW);
        assertThat(result.outcomes()).containsOnlyKeys(ALPHA, BETA, GAMMA);
        assertThat(result.outcomes().values()).extracting(ProviderOutcome::status).containsOnly(ProviderStatus.OK);
        assertThat(result.outcomes().get(ALPHA).offersReceived()).isEqualTo(2);
        assertThat(result.outcomes().get(BETA).offersReceived()).isEqualTo(1);
        assertThat(result.outcomes().get(GAMMA).offersReceived()).isEqualTo(3);
        assertThat(result.outcomes().values()).extracting(ProviderOutcome::offersRejected).containsOnly(0);
        assertThat(repository.all()).hasSize(3);
    }

    @Test
    void should_persist_cheapest_offer_with_markup_when_providers_overlap() {
        AggregateFlightsService service = service(DIRECT, BUDGET,
                StubProvider.returning(ALPHA, direct(ALPHA, "IB6251", "500.00")),
                StubProvider.returning(BETA, direct(BETA, "IB6251", "480.00")));

        service.aggregate(MAD_JFK);

        assertThat(repository.all()).singleElement().satisfies(itinerary -> {
            assertThat(itinerary.source()).isEqualTo(BETA);
            assertThat(itinerary.basePrice()).isEqualTo(Price.of("480.00", "EUR"));
            assertThat(itinerary.finalPrice()).isEqualTo(Price.of("504.00", "EUR"));
        });
    }

    @Test
    void should_persist_other_providers_and_report_error_when_one_provider_is_unavailable() {
        AggregateFlightsService service = service(DIRECT, BUDGET,
                StubProvider.returning(ALPHA, direct(ALPHA, "IB6251", "500.00")),
                StubProvider.failing(BETA, new ProviderUnavailableException(BETA, ProviderStatus.ERROR)),
                StubProvider.returning(GAMMA, direct(GAMMA, "IB6253", "600.00")));

        AggregationResult result = service.aggregate(MAD_JFK);

        assertThat(result.persisted()).isEqualTo(2);
        assertThat(result.outcomes().get(BETA).status()).isEqualTo(ProviderStatus.ERROR);
        assertThat(result.outcomes().get(BETA).offersReceived()).isZero();
        assertThat(result.outcomes().get(ALPHA).status()).isEqualTo(ProviderStatus.OK);
        assertThat(result.outcomes().get(GAMMA).status()).isEqualTo(ProviderStatus.OK);
        assertThat(repository.all()).extracting(Itinerary::source).containsExactlyInAnyOrder(ALPHA, GAMMA);
    }

    @Test
    void should_report_status_carried_by_exception_when_provider_circuit_is_open() {
        AggregateFlightsService service = service(DIRECT, BUDGET,
                StubProvider.returning(ALPHA, direct(ALPHA, "IB6251", "500.00")),
                StubProvider.failing(BETA, new ProviderUnavailableException(BETA, ProviderStatus.CIRCUIT_OPEN)));

        AggregationResult result = service.aggregate(MAD_JFK);

        assertThat(result.outcomes().get(BETA).status()).isEqualTo(ProviderStatus.CIRCUIT_OPEN);
        assertThat(result.persisted()).isEqualTo(1);
    }

    @Test
    void should_report_timeout_and_return_within_budget_when_provider_exceeds_budget() {
        Duration budget = Duration.ofMillis(300);
        AggregateFlightsService service = service(threads, budget,
                StubProvider.returning(ALPHA, direct(ALPHA, "IB6251", "500.00")),
                StubProvider.returning(BETA, direct(BETA, "IB6253", "600.00")),
                StubProvider.returning(GAMMA, direct(GAMMA, "UX91", "450.00")).delayedBy(Duration.ofSeconds(5)));

        long startedAt = System.nanoTime();
        AggregationResult result = service.aggregate(MAD_JFK);
        Duration took = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(took).isGreaterThanOrEqualTo(Duration.ofMillis(250)).isLessThan(Duration.ofSeconds(2));
        assertThat(result.outcomes().get(GAMMA))
                .isEqualTo(new ProviderOutcome(ProviderStatus.TIMEOUT, 0, 0, budget));
        assertThat(result.outcomes().get(ALPHA).status()).isEqualTo(ProviderStatus.OK);
        assertThat(result.outcomes().get(BETA).status()).isEqualTo(ProviderStatus.OK);
        assertThat(result.persisted()).isEqualTo(2);
        assertThat(repository.all()).extracting(Itinerary::source).containsExactlyInAnyOrder(ALPHA, BETA);
    }

    @Test
    void should_return_normal_result_with_nothing_persisted_when_all_providers_fail() {
        AggregateFlightsService service = service(DIRECT, BUDGET,
                StubProvider.failing(ALPHA, new ProviderUnavailableException(ALPHA, ProviderStatus.TIMEOUT)),
                StubProvider.failing(BETA, new ProviderUnavailableException(BETA, ProviderStatus.ERROR)),
                StubProvider.failing(GAMMA, new ProviderUnavailableException(GAMMA, ProviderStatus.CIRCUIT_OPEN)));

        AggregationResult result = service.aggregate(MAD_JFK);

        assertThat(result.persisted()).isZero();
        assertThat(result.allFailed()).isTrue();
        assertThat(result.outcomes().values()).extracting(ProviderOutcome::status).containsExactly(
                ProviderStatus.TIMEOUT, ProviderStatus.ERROR, ProviderStatus.CIRCUIT_OPEN);
        assertThat(repository.all()).isEmpty();
    }

    @Test
    void should_count_rejections_per_provider_and_not_persist_them_when_offers_are_invalid() {
        AggregateFlightsService service = service(DIRECT, BUDGET,
                StubProvider.returning(ALPHA, direct(ALPHA, "IB6251", "500.00"),
                        shortConnection(ALPHA, "300.00"), shortConnection(ALPHA, "310.00")),
                StubProvider.returning(BETA, direct(BETA, "IB6253", "600.00")));

        AggregationResult result = service.aggregate(MAD_JFK);

        assertThat(result.outcomes().get(ALPHA)).satisfies(alpha -> {
            assertThat(alpha.status()).isEqualTo(ProviderStatus.OK);
            assertThat(alpha.offersReceived()).isEqualTo(3);
            assertThat(alpha.offersRejected()).isEqualTo(2);
        });
        assertThat(result.outcomes().get(BETA).offersRejected()).isZero();
        assertThat(result.persisted()).isEqualTo(2);
        assertThat(repository.all()).allSatisfy(itinerary -> assertThat(itinerary.connections()).isZero());
    }

    @Test
    void should_count_unmappable_itineraries_as_received_and_rejected_when_adapter_reports_them() {
        AggregateFlightsService service = service(DIRECT, BUDGET,
                StubProvider.returning(ALPHA, direct(ALPHA, "IB6251", "500.00"), shortConnection(ALPHA, "300.00"))
                        .withUnmappable(3),
                StubProvider.returning(BETA, direct(BETA, "IB6253", "600.00")));

        AggregationResult result = service.aggregate(MAD_JFK);

        assertThat(result.outcomes().get(ALPHA)).satisfies(alpha -> {
            assertThat(alpha.status()).isEqualTo(ProviderStatus.OK);
            assertThat(alpha.offersReceived()).isEqualTo(5);
            assertThat(alpha.offersRejected()).isEqualTo(4);
        });
        assertThat(result.outcomes().get(BETA).offersReceived()).isEqualTo(1);
        assertThat(result.outcomes().get(BETA).offersRejected()).isZero();
        assertThat(result.persisted()).isEqualTo(2);
    }

    @Test
    void should_report_error_and_continue_when_provider_throws_unexpected_exception() {
        AggregateFlightsService service = service(DIRECT, BUDGET,
                StubProvider.failing(ALPHA, new IllegalStateException("boom")),
                StubProvider.returning(BETA, direct(BETA, "IB6253", "600.00")));

        AggregationResult result = service.aggregate(MAD_JFK);

        assertThat(result.outcomes().get(ALPHA).status()).isEqualTo(ProviderStatus.ERROR);
        assertThat(result.outcomes().get(ALPHA).offersReceived()).isZero();
        assertThat(result.outcomes().get(BETA).status()).isEqualTo(ProviderStatus.OK);
        assertThat(result.persisted()).isEqualTo(1);
    }

    @Test
    void should_throw_when_provider_throws_an_error() {
        AggregateFlightsService service = service(threads, BUDGET,
                StubProvider.failing(ALPHA, new StackOverflowError("boom")),
                StubProvider.returning(BETA, direct(BETA, "IB6253", "600.00")));

        assertThatThrownBy(() -> service.aggregate(MAD_JFK)).isInstanceOf(StackOverflowError.class);
        assertThat(repository.all()).isEmpty();
    }

    @Test
    void should_reject_and_count_offer_before_deduplication_when_provider_quotes_another_currency() { // O1
        Offer eur = direct(ALPHA, "IB6251", "500.00");
        Offer sameKeyInUsd = new Offer(BETA, eur.segments(), Price.of("100.00", "USD"));
        AggregateFlightsService service = service(DIRECT, BUDGET,
                StubProvider.returning(ALPHA, eur),
                StubProvider.returning(BETA, sameKeyInUsd));

        AggregationResult result = service.aggregate(MAD_JFK);

        assertThat(result.outcomes().get(BETA))
                .extracting(ProviderOutcome::status, ProviderOutcome::offersReceived, ProviderOutcome::offersRejected)
                .containsExactly(ProviderStatus.OK, 1, 1);
        assertThat(result.outcomes().get(ALPHA).offersRejected()).isZero();
        assertThat(result.persisted()).isEqualTo(1);
        assertThat(repository.all()).singleElement().satisfies(itinerary -> {
            assertThat(itinerary.source()).isEqualTo(ALPHA);
            assertThat(itinerary.basePrice()).isEqualTo(Price.of("500.00", "EUR"));
        });
    }

    @Test
    void should_report_error_and_continue_when_executor_rejects_the_fan_out() {
        threads.shutdown();
        AggregateFlightsService service = service(threads, BUDGET,
                StubProvider.returning(ALPHA, direct(ALPHA, "IB6251", "500.00")));

        AggregationResult result = service.aggregate(MAD_JFK);

        assertThat(result.outcomes().get(ALPHA).status()).isEqualTo(ProviderStatus.ERROR);
        assertThat(result.allFailed()).isTrue();
    }

    @Test
    void should_call_providers_concurrently_when_each_one_is_slow() {
        Duration delay = Duration.ofMillis(300);
        AggregateFlightsService service = service(threads, BUDGET,
                StubProvider.returning(ALPHA, direct(ALPHA, "IB6251", "500.00")).delayedBy(delay),
                StubProvider.returning(BETA, direct(BETA, "IB6253", "600.00")).delayedBy(delay),
                StubProvider.returning(GAMMA, direct(GAMMA, "UX91", "450.00")).delayedBy(delay));

        long startedAt = System.nanoTime();
        AggregationResult result = service.aggregate(MAD_JFK);
        Duration took = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(took).isLessThan(Duration.ofMillis(750));
        assertThat(result.persisted()).isEqualTo(3);
        assertThat(result.outcomes().values()).allSatisfy(outcome -> {
            assertThat(outcome.status()).isEqualTo(ProviderStatus.OK);
            assertThat(outcome.elapsed()).isGreaterThanOrEqualTo(Duration.ofMillis(250));
        });
    }

    @Test
    void should_reject_construction_when_a_provider_is_registered_twice() {
        assertThatThrownBy(() -> service(DIRECT, BUDGET,
                StubProvider.returning(ALPHA), StubProvider.returning(ALPHA)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AggregateFlightsService service(Executor executor, Duration budget, FlightProviderPort... providers) {
        return new AggregateFlightsService(
                List.of(providers),
                new OfferValidator(Currency.getInstance("EUR")),
                new ItineraryDeduplicator(),
                new ItineraryFactory(new PercentageMarkupPolicy(new BigDecimal("5")), CLOCK),
                repository,
                executor,
                budget,
                CLOCK);
    }
}
