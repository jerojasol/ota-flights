package io.github.jerojasol.flights.infrastructure.persistence;

import io.github.jerojasol.flights.domain.model.Carrier;
import io.github.jerojasol.flights.domain.model.Itinerary;
import io.github.jerojasol.flights.domain.model.ItineraryFilter;
import io.github.jerojasol.flights.domain.model.ItineraryPage;
import io.github.jerojasol.flights.domain.model.PageCursor;
import io.github.jerojasol.flights.domain.model.Price;
import io.github.jerojasol.flights.domain.model.SearchCriteria;
import io.github.jerojasol.flights.domain.port.out.ItineraryRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

class ItineraryRepositoryAdapter implements ItineraryRepositoryPort {

    private final ItineraryJpaRepository reads;
    private final ItineraryJdbcUpserter writes;
    private final ItineraryPersistenceMapper mapper;

    ItineraryRepositoryAdapter(ItineraryJpaRepository reads, ItineraryJdbcUpserter writes,
                               ItineraryPersistenceMapper mapper) {
        this.reads = reads;
        this.writes = writes;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public int upsertAll(Collection<Itinerary> itineraries) {
        if (itineraries.isEmpty()) {
            return 0;
        }
        return writes.upsert(itineraries.stream().map(mapper::toRow).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ItineraryPage findBy(SearchCriteria criteria, ItineraryFilter filter, Optional<PageCursor> cursor,
                                int limit) {
        // The route filters can only narrow the route already fixed by the criteria.
        if (!filter.origin().map(criteria.origin()::equals).orElse(true)
                || !filter.destination().map(criteria.destination()::equals).orElse(true)) {
            return ItineraryPage.empty();
        }
        // Prices are never compared across currencies (P4): a max price or cursor only matches rows in its own
        // currency, and a max price and cursor in different currencies cannot match anything.
        Optional<Currency> maxPriceCurrency = filter.maxPrice().map(Price::currency);
        Optional<Currency> cursorCurrency = cursor.map(c -> c.lastFinalPrice().currency());
        if (maxPriceCurrency.isPresent() && cursorCurrency.isPresent()
                && !maxPriceCurrency.get().equals(cursorCurrency.get())) {
            return ItineraryPage.empty();
        }
        String currency = maxPriceCurrency.or(() -> cursorCurrency).map(Currency::getCurrencyCode).orElse(null);
        BigDecimal maxPrice = filter.maxPrice().map(Price::amount).orElse(null);
        String carrier = filter.carrier().map(Carrier::code).orElse(null);
        String origin = criteria.origin().code();
        String destination = criteria.destination().code();

        List<ItineraryJpaEntity> fetched = cursor
                .map(position -> reads.findNextPage(origin, destination, criteria.departureDate(), maxPrice,
                        carrier, currency, position.lastFinalPrice().amount(), position.lastKey().value(),
                        limit + 1))
                .orElseGet(() -> reads.findFirstPage(origin, destination, criteria.departureDate(), maxPrice,
                        carrier, currency, limit + 1));
        List<Itinerary> rows = fetched.stream().map(mapper::toDomain).toList();
        if (rows.size() <= limit) {
            return new ItineraryPage(rows, Optional.empty());
        }
        List<Itinerary> items = rows.subList(0, limit);
        Itinerary last = items.getLast();
        return new ItineraryPage(items, Optional.of(new PageCursor(last.finalPrice(), last.key())));
    }
}
