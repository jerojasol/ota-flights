package io.github.jerojasol.flights.domain.model;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps one Offer per {@link ItineraryKey}: the lowest base price, ties broken by Provider declaration order
 * (ALPHA &lt; BETA &lt; GAMMA), then by earlier final arrival. Grouping is a single hash pass, O(n); only the distinct survivors are sorted
 * by key so the output order is reproducible. All offers must be quoted in the same currency (§10).
 */
public final class ItineraryDeduplicator {

    public List<Offer> deduplicate(Collection<Offer> validOffers) {
        Map<ItineraryKey, Offer> cheapestByKey = new HashMap<>();
        for (Offer offer : validOffers) {
            cheapestByKey.merge(ItineraryKey.of(offer.segments()), offer, ItineraryDeduplicator::cheaper);
        }
        return cheapestByKey.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().value()))
                .map(Map.Entry::getValue)
                .toList();
    }

    private static Offer cheaper(Offer current, Offer candidate) {
        if (candidate.basePrice().isLowerThan(current.basePrice())) {
            return candidate;
        }
        if (current.basePrice().isLowerThan(candidate.basePrice())) {
            return current;
        }
        int byProvider = candidate.provider().compareTo(current.provider());
        if (byProvider != 0) {
            return byProvider < 0 ? candidate : current;
        }
        // Same provider, same price, same key: arrival times are outside the key, so pick the earlier final
        // arrival to keep the result independent of input order.
        return candidate.segments().getLast().arrivalAt().isBefore(current.segments().getLast().arrivalAt())
                ? candidate : current;
    }
}
