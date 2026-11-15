package io.github.jerojasol.flights.infrastructure.scheduling;

import io.github.jerojasol.flights.domain.model.Airport;
import io.github.jerojasol.flights.domain.port.in.RefreshItinerariesUseCase;
import io.github.jerojasol.flights.domain.search.SearchCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Scheduled ingestion: refreshes the configured warm routes for today and tomorrow (UTC). It goes through
 * RefreshItinerariesUseCase, so it takes the same aggregation lock as searches and admin refreshes and never races them;
 * a route whose lock is held is skipped until the next run. One failing route never stops the others. With no warm
 * routes configured, each run does nothing.
 */
public final class WarmRoutesScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(WarmRoutesScheduler.class);

    private final RefreshItinerariesUseCase refresh;
    private final List<String> routes;
    private final Clock clock;

    public WarmRoutesScheduler(RefreshItinerariesUseCase refresh, List<String> routes, Clock clock) {
        this.refresh = refresh;
        this.routes = List.copyOf(routes);
        this.clock = clock;
        if (this.routes.isEmpty()) {
            LOG.info("scheduler=warm-routes outcome=DISABLED reason=\"no warm routes configured\"");
        }
    }

    @Scheduled(fixedDelayString = "${aggregation.warm-interval:PT10M}")
    public void refreshWarmRoutes() {
        for (SearchCriteria criteria : criteria(LocalDate.now(clock))) {
            String route = criteria.origin().code() + "-" + criteria.destination().code();
            try {
                boolean ran = refresh.refresh(criteria).isPresent();
                LOG.info("scheduler=warm-routes route={} date={} outcome={}", route, criteria.departureDate(),
                        ran ? "REFRESHED" : "SKIPPED_LOCKED");
            } catch (RuntimeException failed) {
                LOG.warn("scheduler=warm-routes route={} date={} outcome=FAILED reason=\"{}\"",
                        route, criteria.departureDate(), failed.getMessage());
            }
        }
    }

    /** Today and tomorrow for each route; routes were validated as AAA-BBB at startup. */
    List<SearchCriteria> criteria(LocalDate today) {
        List<SearchCriteria> all = new ArrayList<>();
        for (String route : routes) {
            String[] airports = route.split("-");
            for (LocalDate date : List.of(today, today.plusDays(1))) {
                all.add(new SearchCriteria(Airport.of(airports[0]), Airport.of(airports[1]), date));
            }
        }
        return all;
    }
}
