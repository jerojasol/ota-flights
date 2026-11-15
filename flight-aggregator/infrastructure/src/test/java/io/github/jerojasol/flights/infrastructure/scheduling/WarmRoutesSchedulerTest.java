package io.github.jerojasol.flights.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jerojasol.flights.domain.aggregation.AggregationResult;
import io.github.jerojasol.flights.domain.aggregation.ProviderOutcome;
import io.github.jerojasol.flights.domain.aggregation.ProviderStatus;
import io.github.jerojasol.flights.domain.model.Airport;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.search.SearchCriteria;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class WarmRoutesSchedulerTest {

    private static final Clock LATE_EVENING_UTC = Clock.fixed(Instant.parse("2026-11-20T23:30:00Z"), ZoneOffset.UTC);
    private static final AggregationResult RESULT = new AggregationResult(1,
            Map.of(Provider.ALPHA, new ProviderOutcome(ProviderStatus.OK, 1, 0, Duration.ofMillis(10))),
            Instant.parse("2026-11-20T23:30:00Z"));

    private final List<SearchCriteria> refreshed = new ArrayList<>();

    @Test
    void should_refresh_each_route_for_today_and_tomorrow_in_utc() {
        new WarmRoutesScheduler(this::record, List.of("MAD-JFK", "bcn-jfk"), LATE_EVENING_UTC).refreshWarmRoutes();

        assertThat(refreshed).containsExactly(
                criteria("MAD", "JFK", "2026-11-20"), criteria("MAD", "JFK", "2026-11-21"),
                criteria("BCN", "JFK", "2026-11-20"), criteria("BCN", "JFK", "2026-11-21"));
    }

    @Test
    void should_keep_going_when_one_route_fails() {
        WarmRoutesScheduler scheduler = new WarmRoutesScheduler(criteria -> {
            refreshed.add(criteria);
            if (criteria.origin().equals(Airport.of("MAD"))) {
                throw new IllegalStateException("providers down");
            }
            return Optional.of(RESULT);
        }, List.of("MAD-JFK", "BCN-JFK"), LATE_EVENING_UTC);

        scheduler.refreshWarmRoutes();

        assertThat(refreshed).hasSize(4);
    }

    @Test
    void should_do_nothing_when_no_routes_are_configured() {
        new WarmRoutesScheduler(this::record, List.of(), LATE_EVENING_UTC).refreshWarmRoutes();

        assertThat(refreshed).isEmpty();
    }

    private Optional<AggregationResult> record(SearchCriteria criteria) {
        refreshed.add(criteria);
        return Optional.empty();
    }

    private static SearchCriteria criteria(String origin, String destination, String date) {
        return new SearchCriteria(Airport.of(origin), Airport.of(destination), LocalDate.parse(date));
    }
}
