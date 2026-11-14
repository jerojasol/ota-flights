package io.github.jerojasol.flights.domain.model;

import java.math.BigDecimal;

/** {@code final = base × (1 + percentage/100)}, rounded HALF_EVEN to scale 2 by {@link Price#multiply}. */
public record PercentageMarkupPolicy(BigDecimal percentage) implements MarkupPolicy {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public PercentageMarkupPolicy {
        if (percentage == null || percentage.signum() < 0) {
            throw new InvalidPriceException("Markup percentage must be non-negative: " + percentage);
        }
    }

    @Override
    public Price apply(Price basePrice) {
        // Division by 100 is always exact in BigDecimal, so the factor carries no rounding error.
        BigDecimal factor = BigDecimal.ONE.add(percentage.divide(HUNDRED));
        return basePrice.multiply(factor);
    }
}
