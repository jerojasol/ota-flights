package io.github.jerojasol.flights.domain.exception;

import io.github.jerojasol.flights.domain.model.RejectionReason;
import java.util.Optional;

/**
 * Carries a {@link RejectionReason} only for violations that are pipeline rejections (§6). Other violations,
 * such as I11 or missing arguments, have no reason.
 */
public final class InvalidItineraryException extends DomainException {

    private final RejectionReason reason;

    public InvalidItineraryException(RejectionReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /** For violations that are not pipeline rejections (e.g. I11), which have no typed reason. */
    public InvalidItineraryException(String message) {
        super(message);
        this.reason = null;
    }

    public Optional<RejectionReason> reason() {
        return Optional.ofNullable(reason);
    }
}
