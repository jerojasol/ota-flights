package io.github.jerojasol.mockproviders.alpha;

import java.math.BigDecimal;
import java.util.List;

public record AlphaItinerary(String id, BigDecimal price, String currency, List<AlphaSegment> segments) {
}
