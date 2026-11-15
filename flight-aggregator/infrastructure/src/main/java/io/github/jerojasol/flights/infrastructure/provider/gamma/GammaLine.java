package io.github.jerojasol.flights.infrastructure.provider.gamma;

import java.util.List;

/**
 * One data line of Gamma's CSV export, in Gamma's vocabulary, split but not interpreted: every value is raw text, and
 * GammaOfferMapper parses cents and timestamps per line.
 */
record GammaLine(String ref, String priceCents, String currency, List<GammaSegmentField> segments) {

    /** One {@code carrier;flightNo;from;to;dep;arr} group from the packed {@code segments} field. */
    record GammaSegmentField(String carrier, String flightNo, String from, String to, String dep, String arr) {
    }
}
