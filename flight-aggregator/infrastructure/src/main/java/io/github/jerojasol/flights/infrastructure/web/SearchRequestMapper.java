package io.github.jerojasol.flights.infrastructure.web;

import io.github.jerojasol.flights.domain.exception.DomainException;
import io.github.jerojasol.flights.domain.model.Airport;
import io.github.jerojasol.flights.domain.model.Carrier;
import io.github.jerojasol.flights.domain.model.Price;
import io.github.jerojasol.flights.domain.search.ItineraryFilter;
import io.github.jerojasol.flights.domain.search.PageCursor;
import io.github.jerojasol.flights.domain.search.SearchCriteria;

import java.time.LocalDate;
import java.util.Currency;
import java.util.Optional;

/**
 * Validated request values → domain query types. {@code maxPrice} is read in the reference currency. A domain rule the
 * request breaks here (e.g. origin equals destination) becomes {@link InvalidRequestException}: it is about the
 * client's input. A cursor is accepted only in the reference currency, the only currency this API issues.
 */
public final class SearchRequestMapper {

    private final Currency referenceCurrency;

    public SearchRequestMapper(Currency referenceCurrency) {
        this.referenceCurrency = referenceCurrency;
    }

    public SearchCriteria criteria(ItinerarySearchRequest request) {
        return criteria(request.origin(), request.destination(), request.date());
    }

    public SearchCriteria criteria(AggregationRequest request) {
        return criteria(request.origin(), request.destination(), request.date());
    }

    public ItineraryFilter filter(ItinerarySearchRequest request) {
        try {
            ItineraryFilter filter = ItineraryFilter.none();
            if (request.maxPrice() != null) {
                filter = filter.withMaxPrice(
                        Price.of(request.maxPrice().toPlainString(), referenceCurrency.getCurrencyCode()));
            }
            if (request.carrier() != null) {
                filter = filter.withCarrier(Carrier.of(request.carrier()));
            }
            return filter;
        } catch (DomainException rejected) {
            throw new InvalidRequestException(rejected.getMessage(), rejected);
        }
    }

    public Optional<PageCursor> cursor(ItinerarySearchRequest request) {
        return Optional.ofNullable(request.cursor()).map(CursorCodec::decode).map(this::inReferenceCurrency);
    }

    public int limit(ItinerarySearchRequest request) {
        return request.limit() == null ? ItinerarySearchRequest.DEFAULT_LIMIT : request.limit();
    }

    private SearchCriteria criteria(String origin, String destination, LocalDate date) {
        try {
            return new SearchCriteria(Airport.of(origin), Airport.of(destination), date);
        } catch (DomainException rejected) {
            throw new InvalidRequestException(rejected.getMessage(), rejected);
        }
    }

    /** The repository would answer an empty page, which looks like the end of the results; reject it instead. */
    private PageCursor inReferenceCurrency(PageCursor cursor) {
        if (!cursor.lastFinalPrice().currency().equals(referenceCurrency)) {
            throw new InvalidCursorException("cursor is not valid");
        }
        return cursor;
    }
}
