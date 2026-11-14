package io.github.jerojasol.flights.domain.model;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record FlightNumber(Carrier carrier, int number) {

    private static final Pattern FORMAT = Pattern.compile("([A-Za-z0-9]{2})([0-9]{1,4})");
    private static final int MIN_NUMBER = 1;
    private static final int MAX_NUMBER = 9999;

    public FlightNumber {
        if (carrier == null || number < MIN_NUMBER || number > MAX_NUMBER) {
            throw new InvalidFlightNumberException((carrier == null ? null : carrier.code()) + String.valueOf(number));
        }
    }

    // Leading zeros disappear with the numeric representation, so IB117 and IB0117 are equal.
    public static FlightNumber of(String flightNumber) {
        if (flightNumber == null) {
            throw new InvalidFlightNumberException(null);
        }
        Matcher matcher = FORMAT.matcher(flightNumber.trim());
        if (!matcher.matches()) {
            throw new InvalidFlightNumberException(flightNumber);
        }
        Carrier carrier = new Carrier(matcher.group(1).toUpperCase(Locale.ROOT));
        return new FlightNumber(carrier, Integer.parseInt(matcher.group(2)));
    }
}
