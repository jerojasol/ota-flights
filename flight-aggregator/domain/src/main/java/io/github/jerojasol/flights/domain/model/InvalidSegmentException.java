package io.github.jerojasol.flights.domain.model;

import java.util.Optional;

/**
 * Carries the matching {@link RejectionReason} (DEAD_LEG_* / SEGMENT_DURATION_EXCEEDED) so that an adapter that
 * fails to build a Segment can count the rejected offer under the documented reason. Empty for missing fields.
 */
public final class InvalidSegmentException extends DomainException {

    private final RejectionReason reason;

    public InvalidSegmentException(RejectionReason reason, String message) {
        super(message);
        this.reason = java.util.Objects.requireNonNull(reason, "reason");
    }

    public InvalidSegmentException(String message) {
        super(message);
        this.reason = null;
    }

    public Optional<RejectionReason> reason() {
        return Optional.ofNullable(reason);
    }
}
