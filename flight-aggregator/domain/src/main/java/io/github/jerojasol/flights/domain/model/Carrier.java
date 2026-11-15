package io.github.jerojasol.flights.domain.model;

import io.github.jerojasol.flights.domain.exception.InvalidCarrierException;
import java.util.Locale;
import java.util.regex.Pattern;

public record Carrier(String code) {

    private static final Pattern IATA = Pattern.compile("[A-Z0-9]{2}");
    private static final Pattern ANY_CASE = Pattern.compile("[A-Za-z0-9]{2}");

    public Carrier {
        if (code == null || !IATA.matcher(code).matches()) {
            throw new InvalidCarrierException(code);
        }
    }

    // Checked before uppercasing: some non-ASCII characters uppercase into ASCII ("ß" -> "SS").
    public static Carrier of(String code) {
        if (code == null || !ANY_CASE.matcher(code.trim()).matches()) {
            throw new InvalidCarrierException(code);
        }
        return new Carrier(code.trim().toUpperCase(Locale.ROOT));
    }
}
