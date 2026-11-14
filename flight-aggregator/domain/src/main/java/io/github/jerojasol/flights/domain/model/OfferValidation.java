package io.github.jerojasol.flights.domain.model;

/** Outcome of validating an Offer. Validation is total: it returns one of these, it never throws. */
public sealed interface OfferValidation permits OfferValidation.Valid, OfferValidation.Rejected {

    Offer offer();

    record Valid(Offer offer) implements OfferValidation {
    }

    record Rejected(Offer offer, RejectionReason reason) implements OfferValidation {
    }
}
