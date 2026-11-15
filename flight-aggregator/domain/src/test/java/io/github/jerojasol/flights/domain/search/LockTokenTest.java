package io.github.jerojasol.flights.domain.search;

import io.github.jerojasol.flights.domain.exception.InvalidSearchException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LockTokenTest {

    @Test
    void should_reject_token_when_value_is_null() {
        assertThatThrownBy(() -> new LockToken(null)).isInstanceOf(InvalidSearchException.class);
    }

    @Test
    void should_reject_token_when_value_is_blank() {
        assertThatThrownBy(() -> new LockToken("  ")).isInstanceOf(InvalidSearchException.class);
    }

    @Test
    void should_be_equal_when_values_are_equal() {
        assertThat(new LockToken("a1")).isEqualTo(new LockToken("a1")).isNotEqualTo(new LockToken("b2"));
    }
}
