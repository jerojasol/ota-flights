package io.github.jerojasol.flights.infrastructure.web;

import io.github.jerojasol.flights.domain.model.DomainException;
import io.github.jerojasol.flights.domain.model.ItineraryKey;
import io.github.jerojasol.flights.domain.model.PageCursor;
import io.github.jerojasol.flights.domain.model.Price;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * PageCursor ↔ opaque string: URL-safe Base64 (no padding) of {@code <amount>|<currency>|<itineraryKey>}. The currency
 * travels with the amount because PageCursor holds a full Price and prices are never compared across currencies.
 * The cursor is opaque, not secret or signed: a crafted cursor can only move its own caller within the result set.
 */
public final class CursorCodec {

    private static final String SEPARATOR = "|";
    private static final int PARTS = 3;

    private CursorCodec() {
    }

    public static String encode(PageCursor cursor) {
        String plain = cursor.lastFinalPrice().amount().toPlainString() + SEPARATOR
                + cursor.lastFinalPrice().currency().getCurrencyCode() + SEPARATOR
                + cursor.lastKey().value();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    /** @throws InvalidCursorException when {@code encoded} was not produced by {@link #encode} */
    public static PageCursor decode(String encoded) {
        String plain;
        try {
            plain = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException notBase64) {
            throw new InvalidCursorException("cursor is not valid");
        }
        String[] parts = plain.split("\\|", -1);
        if (parts.length != PARTS) {
            throw new InvalidCursorException("cursor is not valid");
        }
        try {
            return new PageCursor(Price.of(parts[0], parts[1]), ItineraryKey.fromStored(parts[2]));
        } catch (DomainException malformed) {
            throw new InvalidCursorException("cursor is not valid");
        }
    }
}
