package io.github.jerojasol.flights.domain.model;

/** Rule that derives the Final Price from the Base Price. */
public interface MarkupPolicy {

    Price apply(Price basePrice);
}
