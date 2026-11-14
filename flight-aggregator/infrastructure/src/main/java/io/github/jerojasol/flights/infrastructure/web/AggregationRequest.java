package io.github.jerojasol.flights.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

/** Body of {@code POST /api/admin/aggregations}. Codes are accepted in any case, as for the search. */
public record AggregationRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3-letter IATA airport code") String origin,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3-letter IATA airport code") String destination,
        @NotNull LocalDate date) {
}
