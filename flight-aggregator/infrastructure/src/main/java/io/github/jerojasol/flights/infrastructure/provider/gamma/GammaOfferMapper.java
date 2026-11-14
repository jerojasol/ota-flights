package io.github.jerojasol.flights.infrastructure.provider.gamma;

import io.github.jerojasol.flights.domain.model.Airport;
import io.github.jerojasol.flights.domain.model.DomainException;
import io.github.jerojasol.flights.domain.model.FlightNumber;
import io.github.jerojasol.flights.domain.model.Offer;
import io.github.jerojasol.flights.domain.model.Price;
import io.github.jerojasol.flights.domain.model.Provider;
import io.github.jerojasol.flights.domain.model.ProviderResponse;
import io.github.jerojasol.flights.domain.model.Segment;
import io.github.jerojasol.flights.infrastructure.provider.gamma.GammaLine.GammaSegmentField;
import io.github.jerojasol.flights.infrastructure.provider.gamma.GammaLineParser.MalformedLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

/**
 * Gamma → domain, while the export streams in. {@code price_cents} is an integer in the currency's minor units
 * ({@code 51230} EUR → {@code 512.30}, {@code 15000} JPY → {@code 15000}); timestamps are {@code yyyyMMddHHmm} in UTC; {@code flightNo} is zero-padded to four digits, so
 * {@code BA} + {@code 0117} becomes {@code BA117}.
 *
 * <p>A line that cannot be built is skipped and counted as unmappable: wrong shape ({@link MalformedLine}), a
 * DomainException (dead leg, bad code, negative price), or the format's own parse errors (NumberFormatException for
 * cents, DateTimeParseException for timestamps). Nothing broader is caught. Header, {@code #END} and truncation are
 * GammaStreamReader's job and fail the whole export. Connection rules are left to OfferValidator.
 */
final class GammaOfferMapper {

    private static final Logger LOG = LoggerFactory.getLogger(GammaOfferMapper.class);

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuuMMddHHmm").withResolverStyle(ResolverStyle.STRICT);

    ProviderResponse map(BufferedReader body) throws IOException {
        List<Offer> offers = new ArrayList<>();
        int[] unmappable = {0};
        GammaStreamReader.read(body, line -> {
            try {
                offers.add(toOffer(GammaLineParser.parse(line)));
            } catch (MalformedLine | DomainException | NumberFormatException | DateTimeParseException invalid) {
                unmappable[0]++;
                LOG.debug("provider=gamma itinerary_ref={} outcome=UNMAPPABLE reason=\"{}\"",
                        refOf(line), invalid.getMessage());
            }
        });
        return new ProviderResponse(offers, unmappable[0]);
    }

    private static Offer toOffer(GammaLine line) {
        List<Segment> segments = line.segments().stream().map(GammaOfferMapper::toSegment).toList();
        // Normalised the way Price.of does, so "eur" means EUR here as it does for Alpha and Beta.
        String currency = line.currency().strip().toUpperCase(Locale.ROOT);
        BigDecimal amount = BigDecimal.valueOf(Long.parseLong(line.priceCents()), minorUnitDigits(currency));
        return new Offer(Provider.GAMMA, segments, Price.of(amount.toPlainString(), currency));
    }

    /**
     * {@code price_cents} is in the currency's minor units: 2 decimals for EUR, 0 for JPY, 3 for KWD. A code that is
     * not ISO 4217, or a pseudo-currency without minor units (XXX, XAU: -1), makes the line unmappable.
     */
    private static int minorUnitDigits(String currencyCode) {
        Currency currency;
        try {
            currency = Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException notIso4217) {
            throw new MalformedLine("currency is not an ISO 4217 code: " + currencyCode);
        }
        int digits = currency.getDefaultFractionDigits();
        if (digits < 0) {
            throw new MalformedLine("currency has no minor units: " + currencyCode);
        }
        return digits;
    }

    private static Segment toSegment(GammaSegmentField segment) {
        return new Segment(FlightNumber.of(segment.carrier() + segment.flightNo()),
                Airport.of(segment.from()), instant(segment.dep()),
                Airport.of(segment.to()), instant(segment.arr()));
    }

    private static Instant instant(String value) {
        return LocalDateTime.parse(value, TIMESTAMP).toInstant(ZoneOffset.UTC);
    }

    /** The ref for the log line; the whole line is never logged. */
    private static String refOf(String line) {
        int comma = line.indexOf(',');
        String ref = comma < 0 ? "" : line.substring(0, comma);
        return ref.isBlank() ? "none" : ref;
    }
}
