package io.github.jerojasol.flights.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;

public record Price(BigDecimal amount, Currency currency) {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    public Price {
        if (amount == null) {
            throw new InvalidPriceException("Price amount is required");
        }
        if (currency == null) {
            throw new InvalidPriceException("Price currency is required");
        }
        if (amount.signum() < 0) {
            throw new InvalidPriceException("Price amount must be non-negative: " + amount);
        }
        amount = amount.setScale(SCALE, ROUNDING);
    }

    public static Price of(String amount, String currencyCode) {
        if (amount == null || currencyCode == null) {
            throw new InvalidPriceException("Price amount and currency are required");
        }
        BigDecimal parsedAmount;
        try {
            parsedAmount = new BigDecimal(amount.trim());
        } catch (NumberFormatException e) {
            throw new InvalidPriceException("Price amount is not a number: " + amount);
        }
        Currency parsedCurrency;
        try {
            parsedCurrency = Currency.getInstance(currencyCode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidPriceException("Price currency is not an ISO 4217 code: " + currencyCode);
        }
        return new Price(parsedAmount, parsedCurrency);
    }

    public Price plus(Price other) {
        requireSameCurrency(other);
        return new Price(amount.add(other.amount), currency);
    }

    public boolean isLowerThan(Price other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) < 0;
    }

    public Price multiply(BigDecimal factor) {
        if (factor == null) {
            throw new InvalidPriceException("Price factor is required");
        }
        return new Price(amount.multiply(factor), currency);
    }

    private void requireSameCurrency(Price other) {
        if (other == null) {
            throw new InvalidPriceException("Price to operate with is required");
        }
        if (!currency.equals(other.currency)) {
            throw new InvalidPriceException(
                    "Prices have different currencies: " + currency + " and " + other.currency);
        }
    }
}
