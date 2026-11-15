package io.github.jerojasol.mockproviders.alpha;

import java.time.Instant;

public record AlphaSegment(String carrier, String flightNo, String from, String to, Instant dep, Instant arr) {
}
