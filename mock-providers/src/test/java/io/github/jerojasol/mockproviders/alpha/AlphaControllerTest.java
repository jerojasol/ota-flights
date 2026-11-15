package io.github.jerojasol.mockproviders.alpha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import io.github.jerojasol.mockproviders.generator.GeneratedItinerary;
import io.github.jerojasol.mockproviders.generator.GeneratedSegment;
import io.github.jerojasol.mockproviders.generator.InventoryGenerator;
import io.github.jerojasol.mockproviders.generator.ProviderId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(AlphaController.class)
class AlphaControllerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private InventoryGenerator generator;

    @Test
    void should_return_contract_shaped_json_array_when_query_is_valid() {
        givenInventory(List.of(nonStop(51230)));

        MvcTestResult result = mvc.get().uri("/alpha/flights?from=MAD&to=JFK&date=2026-11-20").exchange();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$").asArray().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$[0].id").asString().matches("ALP-[0-9a-f]{6}");
        assertThat(result).bodyJson().extractingPath("$[0].currency").isEqualTo("EUR");
        assertThat(result).bodyJson().extractingPath("$[0].segments[0].carrier").isEqualTo("IB");
        assertThat(result).bodyJson().extractingPath("$[0].segments[0].flightNo").isEqualTo("6251");
        assertThat(result).bodyJson().extractingPath("$[0].segments[0].from").isEqualTo("MAD");
        assertThat(result).bodyJson().extractingPath("$[0].segments[0].to").isEqualTo("JFK");
        assertThat(result).bodyJson().extractingPath("$[0].segments[0].dep").isEqualTo("2026-11-20T07:35:00Z");
        assertThat(result).bodyJson().extractingPath("$[0].segments[0].arr").isEqualTo("2026-11-20T16:10:00Z");
    }

    @Test
    void should_write_price_as_number_with_two_decimals_when_cents_end_in_zero() throws Exception {
        givenInventory(List.of(nonStop(51230)));

        MvcTestResult result = mvc.get().uri("/alpha/flights?from=MAD&to=JFK&date=2026-11-20").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("\"price\":512.30");
    }

    @Test
    void should_return_bad_request_when_date_is_missing() {
        MvcTestResult result = mvc.get().uri("/alpha/flights?from=MAD&to=JFK").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$.error").isEqualTo("BAD_REQUEST");
        assertThat(result).bodyJson().doesNotHavePath("$.ref");
    }

    @Test
    void should_return_bad_request_when_date_is_malformed() {
        MvcTestResult result = mvc.get().uri("/alpha/flights?from=MAD&to=JFK&date=20261120").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.error").isEqualTo("BAD_REQUEST");
    }

    @Test
    void should_return_bad_request_when_from_is_lowercase() {
        MvcTestResult result = mvc.get().uri("/alpha/flights?from=mad&to=JFK&date=2026-11-20").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.error").isEqualTo("BAD_REQUEST");
        assertThat(result).bodyJson().extractingPath("$.message").asString().contains("'from'");
    }

    @Test
    void should_return_empty_array_when_inventory_is_empty() {
        givenInventory(List.of());

        MvcTestResult result = mvc.get().uri("/alpha/flights?from=MAD&to=JFK&date=2026-11-20").exchange();

        assertThat(result).hasStatusOk().hasBodyTextEqualTo("[]");
    }

    private void givenInventory(List<GeneratedItinerary> inventory) {
        given(generator.generate(eq("MAD"), eq("JFK"), eq(DATE), any(ProviderId.class))).willReturn(inventory);
    }

    private static GeneratedItinerary nonStop(long priceCents) {
        List<GeneratedSegment> segments = List.of(new GeneratedSegment("IB", 6251, "MAD", "JFK",
                Instant.parse("2026-11-20T07:35:00Z"), Instant.parse("2026-11-20T16:10:00Z")));
        return new GeneratedItinerary(GeneratedItinerary.canonicalIdOf(segments), segments, priceCents, "EUR",
                Set.of(ProviderId.ALPHA), null);
    }
}
