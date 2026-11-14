package io.github.jerojasol.flights.domain;

import io.github.jerojasol.flights.domain.model.Airport;
import io.github.jerojasol.flights.domain.model.Carrier;
import io.github.jerojasol.flights.domain.model.FlightNumber;
import io.github.jerojasol.flights.domain.model.Segment;
import io.github.jerojasol.flights.domain.model.Price;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

public final class Fixtures {

    public static final Airport MAD = new Airport("MAD");
    public static final Airport JFK = new Airport("JFK");
    public static final Airport LHR = new Airport("LHR");

    public static final Carrier IB = new Carrier("IB");
    public static final Carrier BA = new Carrier("BA");

    public static final Currency EUR = Currency.getInstance("EUR");

    public static final Airport MIA = new Airport("MIA");

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
}
