package io.github.jerojasol.mockproviders.generator;

public enum InvalidKind {
    DEAD_LEG_SAME_AIRPORT,
    DEAD_LEG_NON_POSITIVE_DURATION,
    CONNECTION_AIRPORT_MISMATCH,
    CONNECTION_OVERLAP,
    CONNECTION_TOO_SHORT;

    boolean needsConnection() {
        return this == CONNECTION_AIRPORT_MISMATCH || this == CONNECTION_OVERLAP || this == CONNECTION_TOO_SHORT;
    }
}
