package io.github.jerojasol.flights.infrastructure.provider.alpha;

import io.github.jerojasol.flights.infrastructure.provider.ProviderContractViolation;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/** Decodes Alpha's 200 body. Shared by AlphaClient and the fixture tests so both read JSON the same way. */
final class AlphaResponseParser {

    private static final TypeReference<List<AlphaItinerary>> ITINERARIES = new TypeReference<>() { };

    // Unknown fields are ignored so Alpha can add fields without breaking us. Prices are read as text (see AlphaItinerary).
    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private AlphaResponseParser() {
    }

    /** @throws ProviderContractViolation when the body is not an Alpha JSON array */
    static List<AlphaItinerary> parse(byte[] body) {
        try {
            List<AlphaItinerary> itineraries = JSON.readValue(body, ITINERARIES);
            return itineraries == null ? List.of() : itineraries;
        } catch (JacksonException unreadable) {
            throw new ProviderContractViolation("Alpha body is not a JSON array of itineraries", unreadable);
        }
    }
}
