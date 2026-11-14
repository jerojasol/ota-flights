package io.github.jerojasol.flights.domain.model;

/** Identifies one holder of the aggregation lock, so a caller can only release the lock it still holds (§8). */
public record LockToken(String value) {

    public LockToken {
        if (value == null || value.isBlank()) {
            throw new InvalidSearchException("Lock token must not be blank");
        }
    }
}
