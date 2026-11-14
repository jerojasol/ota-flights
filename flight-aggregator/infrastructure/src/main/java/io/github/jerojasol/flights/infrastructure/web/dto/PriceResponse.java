package io.github.jerojasol.flights.infrastructure.web.dto;

import java.math.BigDecimal;

public record PriceResponse(BigDecimal amount, String currency) {
}
