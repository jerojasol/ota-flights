package io.github.jerojasol.flights.infrastructure.provider.beta;

import io.github.jerojasol.flights.infrastructure.provider.ProviderContractViolation;
import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.databind.DeserializationFeature;

/** Decodes Beta's 200 body. Shared by BetaClient and the fixture tests so both read XML the same way. */
final class BetaResponseParser {

    private static final XmlMapper XML = XmlMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private BetaResponseParser() {
    }

    /** @throws ProviderContractViolation when the body is not readable Beta XML */
    static BetaSearchResponse parse(byte[] body) {
        try {
            return XML.readValue(body, BetaSearchResponse.class);
        } catch (JacksonException unreadable) {
            throw new ProviderContractViolation("Beta body is not readable SearchResponse XML", unreadable);
        }
    }
}
