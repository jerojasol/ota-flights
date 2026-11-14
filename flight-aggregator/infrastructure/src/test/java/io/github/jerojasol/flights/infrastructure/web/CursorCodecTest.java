package io.github.jerojasol.flights.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jerojasol.flights.domain.model.ItineraryKey;
import io.github.jerojasol.flights.domain.model.PageCursor;
import io.github.jerojasol.flights.domain.model.Price;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

class CursorCodecTest {

    private static final PageCursor CURSOR = new PageCursor(Price.of("512.30", "EUR"), ItineraryKey.fromStored("a".repeat(64)));

    @Test
    void should_return_the_same_cursor_when_encoded_then_decoded() {
        String encoded = CursorCodec.encode(CURSOR);

        assertThat(CursorCodec.decode(encoded)).isEqualTo(CURSOR);
        assertThat(encoded).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void should_keep_the_currency_when_round_tripping() {
        PageCursor yen = new PageCursor(Price.of("15000", "JPY"), ItineraryKey.fromStored("b".repeat(64)));

        assertThat(CursorCodec.decode(CursorCodec.encode(yen)).lastFinalPrice().currency().getCurrencyCode())
                .isEqualTo("JPY");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not base64 !", "", "dGFtcGVyZWQ"})
    void should_reject_cursor_when_it_is_not_one_this_api_issued(String tampered) {
        assertThatThrownBy(() -> CursorCodec.decode(tampered)).isInstanceOf(InvalidCursorException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc|EUR|" + "a", "512.30|XYZ|" + "a", "512.30|EUR|short", "512.30|EUR"})
    void should_reject_cursor_when_its_parts_are_malformed(String plain) {
        String withKey = plain.endsWith("|a") ? plain.replace("|a", "|" + "a".repeat(64)) : plain;
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(withKey.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> CursorCodec.decode(encoded)).isInstanceOf(InvalidCursorException.class);
    }
}
