package io.github.jerojasol.flights.infrastructure;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * The whole system, from an HTTP search to three providers and back, read top to bottom.
 *
 * <p>The providers (WireMock) sell overlapping inventory for MAD → JFK on 2026-11-20:
 * <ul>
 *   <li>KL1898 direct: Alpha 367.62, Beta 364.88, Gamma 359.13 EUR;</li>
 *   <li>BA4451 + DL3942 + AA9646 via AMS and FRA: Alpha 1465.90, Beta 1422.83, Gamma 1401.02 EUR;</li>
 *   <li>IB6251 direct, sold by Beta only: 519.00 EUR.</li>
 * </ul>
 * So 7 offers become 3 itineraries, each at its cheapest provider plus the 5% markup.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class EndToEndIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @RegisterExtension
    static WireMockExtension providers = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void providerUrls(DynamicPropertyRegistry registry) {
        registry.add("providers.alpha.base-url", () -> providers.baseUrl() + "/alpha");
        registry.add("providers.beta.base-url", () -> providers.baseUrl() + "/beta");
        registry.add("providers.gamma.base-url", () -> providers.baseUrl() + "/gamma");
    }

    private static final String ROUTE = "/api/itineraries?origin=MAD&destination=JFK&date=2026-11-20";
    private static final String FRESH_KEY = "fresh:MAD:JFK:2026-11-20";
    private static final BigDecimal MARKUP = new BigDecimal("1.05");
    private static final int DISTINCT_ITINERARIES = 3;
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    @LocalServerPort
    private int port;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void should_aggregate_deduplicate_page_filter_survive_a_provider_failure_and_keep_the_cheapest() {
        stubAlpha(fixture("alpha/two-itineraries.json"));
        stubBeta(200, fixture("beta/overlap-plus-beta-only.xml"));
        stubGamma(fixture("gamma/two-itineraries.csv"));

        // 1. Cold miss: the first search aggregates the three providers synchronously, then serves page 1.
        JsonNode firstPage = search(ROUTE + "&limit=1");
        assertThat(text(firstPage, "freshness")).isEqualTo("FRESH");
        assertThat(firstPage.get("providers").properties()).hasSize(3)
                .allSatisfy(outcome -> assertThat(outcome.getValue().get("status").asString()).isEqualTo("OK"));
        assertThat(firstPage.get("items")).hasSize(1);
        assertThat(firstPage.get("nextCursor").isNull()).isFalse();
        int requestsAfterAggregation = providers.getAllServeEvents().size();
        assertThat(requestsAfterAggregation).as("one request per provider").isEqualTo(3);

        // 2. Keyset paging: following nextCursor visits every itinerary once, cheapest first, and calls no provider.
        List<JsonNode> all = allPages(1);
        assertThat(all).hasSize(DISTINCT_ITINERARIES);
        assertThat(new HashSet<>(all.stream().map(item -> text(item, "id")).toList())).hasSize(DISTINCT_ITINERARIES);
        assertThat(all).extracting(item -> amount(item, "finalPrice"))
                .containsExactly(new BigDecimal("377.09"), new BigDecimal("544.95"), new BigDecimal("1471.07"));
        assertThat(all).allSatisfy(item -> assertThat(amount(item, "finalPrice"))
                .isEqualByComparingTo(amount(item, "basePrice").multiply(MARKUP).setScale(2, RoundingMode.HALF_EVEN)));
        assertThat(all).extracting(item -> text(item, "source")).containsExactly("GAMMA", "BETA", "GAMMA");
        assertThat(providers.getAllServeEvents()).hasSize(requestsAfterAggregation);

        // 3. Filter: a max price below the cheapest itinerary returns an empty page; still FRESH, still no provider call.
        JsonNode tooCheap = search(ROUTE + "&maxPrice=100");
        assertThat(tooCheap.get("items")).isEmpty();
        assertThat(text(tooCheap, "freshness")).isEqualTo("FRESH");
        assertThat(providers.getAllServeEvents()).hasSize(requestsAfterAggregation);

        // 4. Provider failure during a refresh: Beta starts failing and the data goes stale.
        stubBeta(500, "<Fault xmlns=\"urn:beta-gds:search:v2\"><Code>SYSTEM_ERROR</Code></Fault>");
        expireFreshness();
        //    The stale search answers at once from Postgres and schedules a background refresh.
        JsonNode stale = search(ROUTE);
        assertThat(text(stale, "freshness")).isEqualTo("STALE");
        assertThat(stale.get("items")).hasSize(DISTINCT_ITINERARIES);
        //    Once the refresh has run, the route is FRESH again: Beta failed, Alpha and Gamma answered.
        JsonNode refreshed = awaitFresh();
        assertThat(text(refreshed.get("providers").get("BETA"), "status")).isEqualTo("ERROR");
        assertThat(text(refreshed.get("providers").get("ALPHA"), "status")).isEqualTo("OK");
        assertThat(text(refreshed.get("providers").get("GAMMA"), "status")).isEqualTo("OK");
        //    The itinerary only Beta sells is still served: an upsert never deletes.
        assertThat(allPages(20)).hasSize(DISTINCT_ITINERARIES)
                .anySatisfy(item -> assertThat(flightNumbers(item)).containsExactly("IB6251"));

        // 5. Cheaper wins across runs: Alpha now sells KL1898 below Gamma's price, and the next refresh keeps it.
        stubAlpha(fixture("alpha/two-itineraries.json").replace("\"price\":367.62", "\"price\":300.00"));
        expireFreshness();
        search(ROUTE);
        awaitFresh();
        JsonNode cheapest = allPages(20).getFirst();
        assertThat(flightNumbers(cheapest)).containsExactly("KL1898");
        assertThat(amount(cheapest, "finalPrice")).isEqualByComparingTo("315.00");
        assertThat(text(cheapest, "source")).isEqualTo("ALPHA");

        // 6. Manual aggregation: an operator refresh runs at once and reports each provider. Every itinerary the
        //    providers still offer is rewritten with the price they quote now, cheaper or not, so `persisted` counts
        //    the itineraries upserted. Beta is still failing, so that is every distinct itinerary except the one only
        //    Beta sells; its row survives untouched because an upsert never deletes.
        JsonNode manual = post("/api/admin/aggregations",
                "{\"origin\":\"MAD\",\"destination\":\"JFK\",\"date\":\"2026-11-20\"}");
        assertThat(manual.get("persisted").asInt()).isEqualTo(DISTINCT_ITINERARIES - 1);
        assertThat(text(manual.get("providers").get("ALPHA"), "status")).isEqualTo("OK");
        assertThat(text(manual.get("providers").get("BETA"), "status")).isEqualTo("ERROR");
        assertThat(text(manual.get("providers").get("GAMMA"), "status")).isEqualTo("OK");

        // 7. A price increase is followed, not ignored. Alpha puts KL1898 back up to its original price, so the
        //    cheapest offer for that key is Gamma's again and the stored row must rise from 315.00 to 377.09.
        //    Under a "cheapest ever seen" upsert the row would have kept 315.00 for good: a price no provider sells.
        stubAlpha(fixture("alpha/two-itineraries.json"));
        expireFreshness();
        search(ROUTE);
        awaitFresh();
        JsonNode increased = allPages(20).getFirst();
        assertThat(flightNumbers(increased)).containsExactly("KL1898");
        assertThat(amount(increased, "finalPrice")).isEqualByComparingTo("377.09");
        assertThat(text(increased, "source")).isEqualTo("GAMMA");
    }

    // --- Steps -------------------------------------------------------------------------------------------------

    /** Every itinerary of the route, following nextCursor from the first page. */
    private List<JsonNode> allPages(int limit) {
        List<JsonNode> items = new ArrayList<>();
        String cursor = null;
        do {
            JsonNode page = cursor == null
                    ? search(ROUTE + "&limit=" + limit)
                    : search(ROUTE + "&limit=" + limit + "&cursor=" + cursor);
            page.get("items").forEach(items::add);
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asString();
        } while (cursor != null);
        return items;
    }

    /** The freshness TTL is minutes long; deleting the marker makes the route STALE now. */
    private void expireFreshness() {
        redisTemplate.delete(FRESH_KEY);
    }

    private JsonNode awaitFresh() {
        JsonNode[] latest = new JsonNode[1];
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100)).until(() -> {
            latest[0] = search(ROUTE);
            return text(latest[0], "freshness").equals("FRESH");
        });
        return latest[0];
    }

    // --- HTTP ---------------------------------------------------------------------------------------------------

    private JsonNode search(String path) {
        EntityExchangeResult<String> result = client().get().uri(path).exchange().returnResult(String.class);
        assertThat(result.getStatus().value()).as("GET " + path).isEqualTo(200);
        return JSON.readTree(result.getResponseBody());
    }

    private JsonNode post(String path, String body) {
        EntityExchangeResult<String> result = client().post().uri(path).contentType(MediaType.APPLICATION_JSON)
                .body(body).exchange().returnResult(String.class);
        assertThat(result.getStatus().value()).as("POST " + path).isEqualTo(200);
        return JSON.readTree(result.getResponseBody());
    }

    private RestTestClient client() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    // --- Providers ----------------------------------------------------------------------------------------------

    private static void stubAlpha(String body) {
        providers.stubFor(get(urlPathEqualTo("/alpha/flights"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(body)));
    }

    private static void stubBeta(int status, String body) {
        providers.stubFor(get(urlPathEqualTo("/beta/search")).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/xml").withBody(body)));
    }

    private static void stubGamma(String body) {
        providers.stubFor(get(urlPathEqualTo("/gamma/export"))
                .willReturn(aResponse().withHeader("Content-Type", "text/csv; charset=UTF-8").withBody(body)));
    }

    private static String fixture(String name) {
        try (InputStream in = EndToEndIT.class.getResourceAsStream("/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // --- JSON ---------------------------------------------------------------------------------------------------

    private static String text(JsonNode node, String field) {
        return node.get(field).asString();
    }

    private static BigDecimal amount(JsonNode item, String price) {
        return item.get(price).get("amount").decimalValue();
    }

    private static List<String> flightNumbers(JsonNode item) {
        List<String> numbers = new ArrayList<>();
        item.get("segments").forEach(segment -> numbers.add(segment.get("flightNumber").asString()));
        return numbers;
    }
}
