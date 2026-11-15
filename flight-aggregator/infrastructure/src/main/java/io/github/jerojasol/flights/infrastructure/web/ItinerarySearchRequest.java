package io.github.jerojasol.flights.infrastructure.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Query parameters of {@code GET /api/itineraries}. Airport and carrier codes are accepted in any case and normalised
 * to uppercase by the domain factories (Airport.of, Carrier.of), so {@code origin=mad} means MAD. {@code maxPrice} is
 * in the reference currency. {@code limit} defaults to 20 when absent.
 */
public record ItinerarySearchRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3-letter IATA airport code") String origin,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3-letter IATA airport code") String destination,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @Positive BigDecimal maxPrice,
        @Pattern(regexp = "[A-Za-z0-9]{2}", message = "must be a 2-character IATA carrier code") String carrier,
        String cursor,
        @Min(1) @Max(100) Integer limit) {

    static final int DEFAULT_LIMIT = 20;
}
