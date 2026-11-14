package io.github.jerojasol.flights.infrastructure.provider.gamma;

import io.github.jerojasol.flights.infrastructure.provider.gamma.GammaLine.GammaSegmentField;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits one data line: {@code ref,price_cents,currency,segments}, segments packed with {@code |} and each segment's
 * attributes with {@code ;} (contract: no quoting, fields never contain {@code , ; |}). Only the shape is checked
 * here; a line with the wrong field count, or a segment without exactly six attributes, is a {@link MalformedLine}.
 */
final class GammaLineParser {

    private static final Pattern SEGMENT_SEPARATOR = Pattern.compile("\\|");
    private static final int FIELDS = 4;
    private static final int SEGMENT_ATTRIBUTES = 6;

    private GammaLineParser() {
    }

    static GammaLine parse(String line) {
        String[] fields = line.split(",", -1);
        if (fields.length != FIELDS) {
            throw new MalformedLine("expected " + FIELDS + " fields, got " + fields.length);
        }
        List<GammaSegmentField> segments = new ArrayList<>();
        for (String packed : SEGMENT_SEPARATOR.split(fields[3], -1)) {
            String[] attributes = packed.split(";", -1);
            if (attributes.length != SEGMENT_ATTRIBUTES) {
                throw new MalformedLine("expected " + SEGMENT_ATTRIBUTES + " segment attributes, got " + attributes.length);
            }
            segments.add(new GammaSegmentField(attributes[0], attributes[1], attributes[2], attributes[3],
                    attributes[4], attributes[5]));
        }
        return new GammaLine(fields[0], fields[1], fields[2], segments);
    }

    /** A data line whose shape breaks the contract. It makes that one itinerary unmappable, not the whole export. */
    static final class MalformedLine extends RuntimeException {

        MalformedLine(String message) {
            super(message);
        }
    }
}
