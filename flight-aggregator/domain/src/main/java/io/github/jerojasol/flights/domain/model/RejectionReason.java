package io.github.jerojasol.flights.domain.model;

/** Typed reason why an Offer cannot become an Itinerary (domain-model.md §6). */
public enum RejectionReason {
    DEAD_LEG_SAME_AIRPORT,
    DEAD_LEG_NON_POSITIVE_DURATION,
    SEGMENT_DURATION_EXCEEDED,
    NO_SEGMENTS,
    TOO_MANY_SEGMENTS,
    ORIGIN_EQUALS_DESTINATION,
    AIRPORT_MISMATCH_AT_CONNECTION,
    CONNECTION_OVERLAP,
    CONNECTION_TOO_SHORT,
    CONNECTION_TOO_LONG,
    TOTAL_DURATION_EXCEEDED
}
