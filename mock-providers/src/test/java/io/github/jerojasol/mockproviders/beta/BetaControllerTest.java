package io.github.jerojasol.mockproviders.beta;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import io.github.jerojasol.mockproviders.config.MockProperties;
import io.github.jerojasol.mockproviders.config.XmlConfig;
import io.github.jerojasol.mockproviders.generator.GeneratedItinerary;
import io.github.jerojasol.mockproviders.generator.GeneratedSegment;
import io.github.jerojasol.mockproviders.generator.InventoryGenerator;
import io.github.jerojasol.mockproviders.generator.ProviderId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@WebMvcTest(BetaController.class)
@Import({XmlConfig.class, BetaControllerTest.Config.class})
class BetaControllerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);
    private static final String URI = "/beta/search?origin=MAD&destination=JFK&departureDate=2026-11-20";

    @TestConfiguration
    @EnableConfigurationProperties(MockProperties.class)
    static class Config {
    }

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private InventoryGenerator generator;

    @Test
    void should_return_namespaced_search_response_when_query_is_valid() throws Exception {
        givenInventory(List.of(nonStop()));

        MvcTestResult result = mvc.get().uri(URI).exchange();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_XML);
        String body = body(result);
        assertThat(body).startsWith("<?xml");
        Document doc = parse(body, true);
        assertThat(doc.getDocumentElement().getLocalName()).isEqualTo("SearchResponse");
        assertThat(doc.getDocumentElement().getNamespaceURI()).isEqualTo("urn:beta-gds:search:v2");
        assertThat(doc.getElementsByTagNameNS("urn:beta-gds:search:v2", "Leg").getLength()).isEqualTo(1);
    }

    @Test
    void should_apply_beta_traps_when_rendering_itineraries() throws Exception {
        givenInventory(List.of(nonStop(), twoLegs()));

        Document doc = parse(body(mvc.get().uri(URI).exchange()), false);

        assertThat(xpath(doc, "//Itinerary[1]/Pricing/Total/@amount")).matches("\\d+\\.\\d{2}");
        assertThat(xpath(doc, "//Itinerary[1]/Pricing/Total/@currency")).isEqualTo("EUR");
        assertThat(xpath(doc, "(//Leg)[1]/Departure/LocalDateTime"))
                .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")
                .doesNotContain("Z", "+");
        assertThat(xpath(doc, "(//Leg)[1]/Departure/TimeZone")).isEqualTo("Europe/Madrid");
        NodeList numbers = nodes(doc, "//Leg/Flight/Number");
        for (int i = 0; i < numbers.getLength(); i++) {
            assertThat(numbers.item(i).getTextContent()).doesNotStartWith("0");
        }
        assertThat(xpath(doc, "//Itinerary[2]//Leg[Flight/Airline/@code='BA']/Flight/Number")).isEqualTo("117");
    }

    @Test
    void should_emit_legs_out_of_sequence_when_itinerary_has_two_legs() throws Exception {
        givenInventory(List.of(twoLegs()));

        Document doc = parse(body(mvc.get().uri(URI).exchange()), false);

        NodeList legs = nodes(doc, "//Itinerary[1]/Legs/Leg/@sequence");
        List<String> order = new ArrayList<>();
        for (int i = 0; i < legs.getLength(); i++) {
            order.add(legs.item(i).getNodeValue());
        }
        assertThat(order).containsExactly("2", "1");
    }

    @Test
    void should_return_invalid_request_fault_when_origin_is_missing() throws Exception {
        MvcTestResult result = mvc.get().uri("/beta/search?destination=JFK&departureDate=2026-11-20").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentTypeCompatibleWith(MediaType.APPLICATION_XML);
        Document doc = parse(body(result), true);
        assertThat(doc.getDocumentElement().getLocalName()).isEqualTo("Fault");
        assertThat(doc.getDocumentElement().getNamespaceURI()).isEqualTo("urn:beta-gds:search:v2");
        Document plain = parse(body(result), false);
        assertThat(xpath(plain, "/Fault/Code")).isEqualTo("INVALID_REQUEST");
        assertThat(xpath(plain, "/Fault/Message")).contains("'origin'");
        assertThat(nodes(plain, "/Fault/Reference").getLength()).isZero();
    }

    @Test
    void should_return_zero_count_when_inventory_is_empty() throws Exception {
        givenInventory(List.of());

        Document doc = parse(body(mvc.get().uri(URI).exchange()), false);

        assertThat(xpath(doc, "/SearchResponse/Itineraries/@count")).isEqualTo("0");
        assertThat(nodes(doc, "//Itinerary").getLength()).isZero();
    }

    private void givenInventory(List<GeneratedItinerary> inventory) {
        given(generator.generate(eq("MAD"), eq("JFK"), eq(DATE), any(ProviderId.class))).willReturn(inventory);
    }

    private static GeneratedItinerary nonStop() {
        return itinerary(List.of(new GeneratedSegment("IB", 6251, "MAD", "JFK",
                Instant.parse("2026-11-20T07:35:00Z"), Instant.parse("2026-11-20T16:10:00Z"))), 51900);
    }

    private static GeneratedItinerary twoLegs() {
        return itinerary(List.of(
                new GeneratedSegment("IB", 3166, "MAD", "LHR",
                        Instant.parse("2026-11-20T06:00:00Z"), Instant.parse("2026-11-20T08:25:00Z")),
                new GeneratedSegment("BA", 117, "LHR", "JFK",
                        Instant.parse("2026-11-20T10:40:00Z"), Instant.parse("2026-11-20T18:55:00Z"))), 45540);
    }

    private static GeneratedItinerary itinerary(List<GeneratedSegment> segments, long priceCents) {
        return new GeneratedItinerary(GeneratedItinerary.canonicalIdOf(segments), segments, priceCents, "EUR",
                Set.of(ProviderId.BETA), null);
    }

    private static String body(MvcTestResult result) throws Exception {
        return result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static Document parse(String xml, boolean namespaceAware) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(namespaceAware);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static String xpath(Document doc, String expression) throws Exception {
        return XPathFactory.newInstance().newXPath().evaluate(expression, doc);
    }

    private static NodeList nodes(Document doc, String expression) throws Exception {
        return (NodeList) XPathFactory.newInstance().newXPath().evaluate(expression, doc, XPathConstants.NODESET);
    }
}
