package io.github.jerojasol.flights.infrastructure.provider.alpha;

import java.util.List;

/**
 * One element of Alpha's flat JSON array, in Alpha's vocabulary. Every value is kept as raw text, the price included
 * (Jackson keeps a JSON number's original text), so AlphaOfferMapper parses it per itinerary and one bad value is
 * counted as unmappable instead of failing the whole body.
 */
record AlphaItinerary(String id, String price, String currency, List<AlphaSegment> segments) {

    record AlphaSegment(String carrier, String flightNo, String from, String to, String dep, String arr) {
    }
}
