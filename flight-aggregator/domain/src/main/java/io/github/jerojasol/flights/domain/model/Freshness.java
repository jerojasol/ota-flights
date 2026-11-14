package io.github.jerojasol.flights.domain.model;

/** Read-path state of the data held for one SearchCriteria. See domain-model.md §8. */
public enum Freshness {
    FRESH,
    STALE,
    REFRESHING,
    MISSING
}
