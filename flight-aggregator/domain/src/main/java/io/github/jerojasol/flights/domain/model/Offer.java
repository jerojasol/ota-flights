package io.github.jerojasol.flights.domain.model;

import io.github.jerojasol.flights.domain.exception.InvalidOfferException;
import java.util.List;

public record Offer(Provider provider, List<Segment> segments, Price basePrice) {

    public Offer {
        if (provider == null) {
            throw new InvalidOfferException("Offer provider is required");
        }
        if (segments == null || segments.isEmpty()) {
            throw new InvalidOfferException("Offer must have at least one segment");
        }
        if (basePrice == null) {
            throw new InvalidOfferException("Offer base price is required");
        }
        segments = List.copyOf(segments);
    }
}
