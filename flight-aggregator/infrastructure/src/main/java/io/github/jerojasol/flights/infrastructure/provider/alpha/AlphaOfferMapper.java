package io.github.jerojasol.flights.infrastructure.provider.alpha;

import io.github.jerojasol.flights.domain.aggregation.ProviderResponse;
import io.github.jerojasol.flights.domain.exception.DomainException;
import io.github.jerojasol.flights.domain.model.Airport;
import io.github.jerojasol.flights.domain.model.FlightNumber;
import io.github.jerojasol.flights.domain.model.Offer;
import io.github.jerojasol.flights.domain.model.Price;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.Segment;
import io.github.jerojasol.flights.infrastructure.provider.alpha.AlphaItinerary.AlphaSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Alpha → domain. Alpha's timestamps are already UTC with a {@code Z}; {@code flightNo} is digits only with the
 * carrier separate, so {@code BA} + {@code 0117} becomes {@code BA117}. An itinerary that cannot be built (a dead
 * leg failing S1–S3, a malformed field, or a null entry) is skipped and counted as unmappable; it never fails the
 * whole response. Nulls are checked explicitly; only DomainException and the timestamp parser's exception are caught.
 * Connection rules are not checked here: that is OfferValidator's job, after mapping.
 */
final class AlphaOfferMapper {

    private static final Logger LOG = LoggerFactory.getLogger(AlphaOfferMapper.class);

    ProviderResponse map(List<AlphaItinerary> itineraries) {
        List<Offer> offers = new ArrayList<>(itineraries.size());
        int unmappable = 0;
        for (AlphaItinerary itinerary : itineraries) {
            if (itinerary == null) {
                unmappable++;
                LOG.debug("provider=alpha itinerary_id=none outcome=UNMAPPABLE reason=\"null itinerary entry\"");
                continue;
            }
            if (itinerary.segments() != null && itinerary.segments().stream().anyMatch(Objects::isNull)) {
                unmappable++;
                LOG.debug("provider=alpha itinerary_id={} outcome=UNMAPPABLE reason=\"null segment entry\"",
                        itinerary.id());
                continue;
            }
            try {
                offers.add(toOffer(itinerary));
            } catch (DomainException | DateTimeParseException invalid) {
                unmappable++;
                LOG.debug("provider=alpha itinerary_id={} outcome=UNMAPPABLE reason=\"{}\"",
                        itinerary.id(), invalid.getMessage());
            }
        }
        return new ProviderResponse(offers, unmappable);
    }

    private static Offer toOffer(AlphaItinerary itinerary) {
        List<Segment> segments = itinerary.segments() == null
                ? List.of()
                : itinerary.segments().stream().map(AlphaOfferMapper::toSegment).toList();
        Price price = Price.of(itinerary.price(), itinerary.currency());
        return new Offer(Provider.ALPHA, segments, price);
    }

    private static Segment toSegment(AlphaSegment segment) {
        String flightNumber = segment.carrier() == null || segment.flightNo() == null
                ? null
                : segment.carrier() + segment.flightNo();
        return new Segment(FlightNumber.of(flightNumber), Airport.of(segment.from()), instant(segment.dep()),
                Airport.of(segment.to()), instant(segment.arr()));
    }

    private static Instant instant(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
