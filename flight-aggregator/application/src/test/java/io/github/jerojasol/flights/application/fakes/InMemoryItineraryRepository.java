package io.github.jerojasol.flights.application.fakes;

import io.github.jerojasol.flights.domain.model.Itinerary;
import io.github.jerojasol.flights.domain.model.ItineraryFilter;
import io.github.jerojasol.flights.domain.model.ItineraryKey;
import io.github.jerojasol.flights.domain.model.ItineraryPage;
import io.github.jerojasol.flights.domain.model.PageCursor;
import io.github.jerojasol.flights.domain.model.SearchCriteria;
import io.github.jerojasol.flights.domain.port.out.ItineraryRepositoryPort;

import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class InMemoryItineraryRepository implements ItineraryRepositoryPort {

    private static final Comparator<Itinerary> BY_FINAL_PRICE_THEN_KEY = Comparator
            .comparing((Itinerary itinerary) -> itinerary.finalPrice().amount())
            .thenComparing(itinerary -> itinerary.key().value());

    private final Map<ItineraryKey, Itinerary> rows = new ConcurrentHashMap<>();
    private final AtomicInteger findByCalls = new AtomicInteger();
    private volatile int lastLimit;
    private volatile Runnable onFindBy = () -> { };

    @Override
    public synchronized int upsertAll(Collection<Itinerary> itineraries) {
        int written = 0;
        for (Itinerary itinerary : itineraries) {
            Itinerary stored = rows.get(itinerary.key());
            if (stored == null || itinerary.finalPrice().isLowerThan(stored.finalPrice())) {
                rows.put(itinerary.key(), itinerary);
                written++;
            }
        }
        return written;
    }

    @Override
    public ItineraryPage findBy(SearchCriteria criteria, ItineraryFilter filter, Optional<PageCursor> cursor,
                                int limit) {
        findByCalls.incrementAndGet();
        onFindBy.run();
        lastLimit = limit;
        List<Itinerary> matching = rows.values().stream()
                .filter(itinerary -> matches(itinerary, criteria, filter))
                .sorted(BY_FINAL_PRICE_THEN_KEY)
                .filter(itinerary -> cursor.map(position -> isAfter(itinerary, position)).orElse(true))
                .toList();
        if (matching.size() <= limit) {
            return new ItineraryPage(matching, Optional.empty());
        }
        List<Itinerary> items = matching.subList(0, limit);
        Itinerary last = items.getLast();
        return new ItineraryPage(items, Optional.of(new PageCursor(last.finalPrice(), last.key())));
    }

    /** Runs on every findBy, before the query; lets a test assert what state the caller is in. */
    public void onFindBy(Runnable hook) {
        this.onFindBy = hook;
    }

    public Collection<Itinerary> all() {
        return List.copyOf(rows.values());
    }

    public int findByCalls() {
        return findByCalls.get();
    }

    public int lastLimit() {
        return lastLimit;
    }

    private static boolean matches(Itinerary itinerary, SearchCriteria criteria, ItineraryFilter filter) {
        return itinerary.origin().equals(criteria.origin())
                && itinerary.destination().equals(criteria.destination())
                && itinerary.segments().getFirst().departureAt().atOffset(ZoneOffset.UTC).toLocalDate()
                        .equals(criteria.departureDate())
                && filter.origin().map(itinerary.origin()::equals).orElse(true)
                && filter.destination().map(itinerary.destination()::equals).orElse(true)
                && filter.maxPrice().map(max -> !max.isLowerThan(itinerary.finalPrice())).orElse(true)
                && filter.carrier().map(itinerary.carriers()::contains).orElse(true);
    }

    private static boolean isAfter(Itinerary itinerary, PageCursor position) {
        int byPrice = itinerary.finalPrice().amount().compareTo(position.lastFinalPrice().amount());
        return byPrice > 0 || (byPrice == 0 && itinerary.key().value().compareTo(position.lastKey().value()) > 0);
    }
}
