package io.github.jerojasol.flights.domain.model;

/**
 * External GDS source. Declaration order is significant: it is the deterministic
 * tie-break of the DeduplicationPolicy (ALPHA &lt; BETA &lt; GAMMA). Do not reorder.
 */
public enum Provider {
    ALPHA,
    BETA,
    GAMMA
}
