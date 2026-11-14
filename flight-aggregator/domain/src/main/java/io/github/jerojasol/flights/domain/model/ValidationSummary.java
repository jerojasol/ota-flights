package io.github.jerojasol.flights.domain.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record ValidationSummary(List<Offer> valid, Map<RejectionReason, Integer> rejectedByReason) {

    public ValidationSummary {
        valid = List.copyOf(valid);
        Map<RejectionReason, Integer> copy = new EnumMap<>(RejectionReason.class);
        copy.putAll(rejectedByReason);
        rejectedByReason = Collections.unmodifiableMap(copy);
    }

    public int rejected() {
        return rejectedByReason.values().stream().mapToInt(Integer::intValue).sum();
    }
}
