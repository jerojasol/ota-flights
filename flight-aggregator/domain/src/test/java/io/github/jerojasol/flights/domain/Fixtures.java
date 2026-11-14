package io.github.jerojasol.flights.domain;

import io.github.jerojasol.flights.domain.model.Airport;
import io.github.jerojasol.flights.domain.model.Carrier;
import io.github.jerojasol.flights.domain.model.FlightNumber;
import io.github.jerojasol.flights.domain.model.Offer;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.Segment;
import io.github.jerojasol.flights.domain.model.Price;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

public final class Fixtures {

    public static final Airport MAD = new Airport("MAD");
    public static final Airport JFK = new Airport("JFK");
    public static final Airport LHR = new Airport("LHR");

    public static final Carrier IB = new Carrier("IB");
    public static final Carrier BA = new Carrier("BA");

    public static final Currency EUR = Currency.getInstance("EUR");

    public static final Airport MIA = new Airport("MIA");
    public static final Airport BCN = new Airport("BCN");
    public static final Airport LIS = new Airport("LIS");

    private static final List<Airport> AIRPORT_CHAIN = List.of(MAD, JFK, LHR, MIA, BCN, LIS);

    public static final FlightNumber IB6251 = FlightNumber.of("IB6251");
    public static final FlightNumber IB4120 = FlightNumber.of("IB4120");

    private Fixtures() {
    }

    public static Price price(String amount) {
        return new Price(new BigDecimal(amount), EUR);
    }

    public static Instant instant(String iso) {
        return Instant.parse(iso);
    }

    public static Segment segment(FlightNumber flight, Airport from, String departure, Airport to, String arrival) {
        return new Segment(flight, from, instant(departure), to, instant(arrival));
    }

    /**
     * Segments along MAD, JFK, LHR, MIA, BCN, LIS. {@code minutes} alternates flight time and connection time:
     * [flight1, gap1, flight2, gap2, flight3, ...].
     */
    public static List<Segment> chain(String start, long... minutes) {
        List<Segment> segments = new ArrayList<>();
        Instant clock = instant(start);
        for (int i = 0; i < minutes.length; i += 2) {
            int leg = i / 2;
            Instant arrival = clock.plus(Duration.ofMinutes(minutes[i]));
            segments.add(new Segment(IB6251, AIRPORT_CHAIN.get(leg), clock, AIRPORT_CHAIN.get(leg + 1), arrival));
            clock = i + 1 < minutes.length ? arrival.plus(Duration.ofMinutes(minutes[i + 1])) : arrival;
        }
        return segments;
    }

    public static Offer offer(Provider provider, String amount, List<Segment> segments) {
        return new Offer(provider, segments, price(amount));
    }
}
