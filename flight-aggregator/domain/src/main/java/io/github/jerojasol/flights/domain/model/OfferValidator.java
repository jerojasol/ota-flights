package io.github.jerojasol.flights.domain.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Applies O1 (base price quoted in the reference currency) and then the itinerary-level rules I1, I2, I4–I9 to an
 * Offer. Total: every non-null Offer yields a result, the first failing reason being O1 and then the order
 * documented on {@code Itinerary.findViolation}.
 *
 * <p>Segment-level rules S1–S3 are enforced by the {@link Segment} constructor, so an Offer can never hold an
 * invalid Segment and this validator never raises DEAD_LEG_* or SEGMENT_DURATION_EXCEEDED. Those reasons are
 * reported by adapters through {@link InvalidSegmentException#reason()}. I1 cannot occur either, because
 * {@link Offer} rejects empty segment lists.
 */
public final class OfferValidator {

    private final Currency referenceCurrency;

    public OfferValidator(Currency referenceCurrency) {
        if (referenceCurrency == null) {
            throw new InvalidOfferException("Offer validator requires a reference currency");
        }
        this.referenceCurrency = referenceCurrency;
    }

    public OfferValidation validate(Offer offer) {
        if (!offer.basePrice().currency().equals(referenceCurrency)) {
            return new OfferValidation.Rejected(offer, RejectionReason.CURRENCY_MISMATCH);
        }
        Optional<Itinerary.Violation> violation = Itinerary.findViolation(offer.segments());
        return violation.<OfferValidation>map(v -> new OfferValidation.Rejected(offer, v.reason()))
                .orElseGet(() -> new OfferValidation.Valid(offer));
    }

    public ValidationSummary validateAll(Collection<Offer> offers) {
        List<Offer> valid = new ArrayList<>();
        Map<RejectionReason, Integer> rejectedByReason = new EnumMap<>(RejectionReason.class);
        for (Offer offer : offers) {
            switch (validate(offer)) {
                case OfferValidation.Valid v -> valid.add(v.offer());
                case OfferValidation.Rejected r -> rejectedByReason.merge(r.reason(), 1, Integer::sum);
            }
        }
        return new ValidationSummary(valid, rejectedByReason);
    }
}
