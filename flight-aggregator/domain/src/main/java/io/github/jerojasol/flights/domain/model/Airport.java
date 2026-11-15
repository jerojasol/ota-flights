package io.github.jerojasol.flights.domain.model;

import io.github.jerojasol.flights.domain.exception.InvalidAirportException;
import java.util.Locale;
import java.util.regex.Pattern;

public record Airport(String code) {

    private static final Pattern IATA = Pattern.compile("[A-Z]{3}");
    private static final Pattern ANY_CASE = Pattern.compile("[A-Za-z]{3}");

    public Airport {
        if (code == null || !IATA.matcher(code).matches()) {
            throw new InvalidAirportException(code);
        }
    }

    // Checked before uppercasing: some non-ASCII characters uppercase into ASCII ("ß" -> "SS").
    public static Airport of(String code) {
        if (code == null || !ANY_CASE.matcher(code.trim()).matches()) {
            throw new InvalidAirportException(code);
        }
        return new Airport(code.trim().toUpperCase(Locale.ROOT));
    }
}
