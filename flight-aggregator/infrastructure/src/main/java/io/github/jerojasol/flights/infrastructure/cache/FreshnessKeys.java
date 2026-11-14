package io.github.jerojasol.flights.infrastructure.cache;

import io.github.jerojasol.flights.domain.model.SearchCriteria;

import java.time.format.DateTimeFormatter;

/** Redis key names for one route and date. Codes are already uppercase (Airport normalises them). */
final class FreshnessKeys {

    private FreshnessKeys() {
    }

    static String fresh(SearchCriteria criteria) {
        return "fresh:" + route(criteria);
    }

    static String lock(SearchCriteria criteria) {
        return "lock:" + route(criteria);
    }

    static String seen(SearchCriteria criteria) {
        return "seen:" + route(criteria);
    }

    private static String route(SearchCriteria criteria) {
        return criteria.origin().code() + ":" + criteria.destination().code() + ":"
                + criteria.departureDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
