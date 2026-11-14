package io.github.jerojasol.mockproviders.gamma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(GammaController.class)
class GammaControllerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);
    private static final String URI = "/gamma/export?o=MAD&d=JFK&dt=20261120";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private InventoryGenerator generator;

    @Test
    void should_stream_header_lines_and_trailer_when_query_is_valid() throws Exception {
        givenInventory(List.of(oneSegment(6251), twoSegments()));

        MvcResult result = export(URI);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(MediaType.parseMediaType(result.getResponse().getContentType()))
                .satisfies(type -> assertThat(type.isCompatibleWith(MediaType.parseMediaType("text/csv"))).isTrue())
                .satisfies(type -> assertThat(type.getCharset()).isEqualTo(StandardCharsets.UTF_8));
        List<String> lines = lines(result);
        assertThat(lines.getFirst()).isEqualTo("ref,price_cents,currency,segments");
        assertThat(lines.getLast()).isEqualTo("#END");
        assertThat(lines).hasSize(4);
    }

    @Test
    void should_pack_segments_positionally_when_writing_lines() throws Exception {
        givenInventory(List.of(oneSegment(117), twoSegments()));

        List<String> lines = lines(export(URI));

        for (String line : lines.subList(1, lines.size() - 1)) {
            String[] fields = line.split(",", -1);
            assertThat(fields).hasSize(4);
            String[] segments = fields[3].split("\\|", -1);
            assertThat(segments.length).isBetween(1, 3);
            for (String segment : segments) {
                String[] parts = segment.split(";", -1);
                assertThat(parts).hasSize(6);
                assertThat(parts[1]).matches("\\d{4}");
                assertThat(parts[4]).matches("\\d{12}");
                assertThat(parts[5]).matches("\\d{12}");
            }
        }
    }

    @Test
    void should_write_every_line_when_inventory_exceeds_flush_interval() throws Exception {
        givenInventory(IntStream.rangeClosed(1, 450).mapToObj(GammaControllerTest::oneSegment).toList());

        List<String> lines = lines(export(URI));

        assertThat(lines).hasSize(452);
        assertThat(lines.getLast()).isEqualTo("#END");
    }

    @Test
    void should_return_plain_text_400_when_date_has_wrong_format() throws Exception {
        MvcResult result = mvc.perform(get("/gamma/export?o=MAD&d=JFK&dt=2026-11-20")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getResponse().getContentType()).startsWith("text/plain");
        assertThat(result.getResponse().getContentAsString()).startsWith("ERR 400").contains("dt");
    }

    @Test
    void should_return_contract_message_when_origin_is_missing() throws Exception {
        MvcResult result = mvc.perform(get("/gamma/export?d=JFK&dt=20261120")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getResponse().getContentAsString()).isEqualTo("ERR 400 missing parameter: o\n");
    }

    @Test
    void should_write_header_and_trailer_only_when_inventory_is_empty() throws Exception {
        givenInventory(List.of());

        assertThat(lines(export(URI))).containsExactly("ref,price_cents,currency,segments", "#END");
    }

    private MvcResult export(String uri) throws Exception {
        MvcResult started = mvc.perform(get(uri)).andReturn();
        assertThat(started.getRequest().isAsyncStarted()).isTrue();
        return mvc.perform(asyncDispatch(started)).andReturn();
    }

    private static List<String> lines(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8).lines().toList();
    }

    private void givenInventory(List<GeneratedItinerary> inventory) {
        given(generator.generate(eq("MAD"), eq("JFK"), eq(DATE), any(ProviderId.class))).willReturn(inventory);
    }

    private static GeneratedItinerary oneSegment(int flightNumber) {
        return itinerary(List.of(new GeneratedSegment("IB", flightNumber, "MAD", "JFK",
                Instant.parse("2026-11-20T07:35:00Z"), Instant.parse("2026-11-20T16:10:00Z"))), 51230);
    }

    private static GeneratedItinerary twoSegments() {
        return itinerary(List.of(
                new GeneratedSegment("IB", 3166, "MAD", "LHR",
                        Instant.parse("2026-11-20T06:00:00Z"), Instant.parse("2026-11-20T08:25:00Z")),
                new GeneratedSegment("BA", 117, "LHR", "JFK",
                        Instant.parse("2026-11-20T10:40:00Z"), Instant.parse("2026-11-20T18:55:00Z"))), 43890);
    }

    private static GeneratedItinerary itinerary(List<GeneratedSegment> segments, long priceCents) {
        return new GeneratedItinerary(GeneratedItinerary.canonicalIdOf(segments), segments, priceCents, "EUR",
                Set.of(ProviderId.GAMMA), null);
    }
}
