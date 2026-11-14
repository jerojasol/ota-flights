package io.github.jerojasol.flights.infrastructure;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * The whole service booted against real Postgres and Redis, with the three providers served by WireMock from the
 * contract fixtures. One test tells the story, because it stops Redis half-way: a cold search aggregates and serves,
 * then with Redis down the service stays alive, keeps serving from Postgres and refuses to refresh.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class FlightAggregatorApplicationIT {

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

    private static final String SEARCH = "/api/itineraries?origin=MAD&destination=JFK&date=2026-11-20";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Test
    void should_serve_aggregated_itineraries_and_keep_serving_without_refreshing_when_redis_goes_down() {
        stubProviders();

        // Healthy start.
        assertThat(call("GET", "/actuator/health", null).status()).isEqualTo(200);
        assertThat(call("GET", "/actuator/health", null).json().get("status").asString()).isEqualTo("UP");

        // Cold miss: aggregates the three providers, deduplicates and serves from Postgres.
        Response first = call("GET", SEARCH, null);
        assertThat(first.status()).isEqualTo(200);
        assertThat(first.json().get("freshness").asString()).isEqualTo("FRESH");
        assertThat(first.json().get("items").size()).isEqualTo(3);
        assertThat(first.json().get("providers").size()).isEqualTo(3);
        int providerCalls = providers.getAllServeEvents().size();
        assertThat(providerCalls).isEqualTo(3);

        redis.stop();

        // Liveness, the container healthcheck, does not depend on Redis; overall health does, for operators.
        Response liveness = call("GET", "/actuator/health/liveness", null);
        assertThat(liveness.status()).isEqualTo(200);
        assertThat(liveness.json().get("status").asString()).isEqualTo("UP");
        assertThat(call("GET", "/actuator/health", null).status()).isEqualTo(503);

        // Searches keep working from Postgres, quickly, marked STALE.
        long started = System.nanoTime();
        Response degraded = call("GET", SEARCH, null);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        assertThat(degraded.status()).isEqualTo(200);
        assertThat(degraded.json().get("freshness").asString()).isEqualTo("STALE");
        assertThat(degraded.json().get("items").size()).isEqualTo(3);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(2));

        // No refresh is possible without the lock: 409, and no provider is called.
        Response refresh = call("POST", "/api/admin/aggregations",
                "{\"origin\":\"MAD\",\"destination\":\"JFK\",\"date\":\"2026-11-20\"}");
        assertThat(refresh.status()).isEqualTo(409);
        assertThat(providers.getAllServeEvents()).hasSize(providerCalls);
    }

    private static void stubProviders() {
        providers.stubFor(get(urlPathEqualTo("/alpha/flights")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody(fixture("alpha/contract-example.json"))));
        providers.stubFor(get(urlPathEqualTo("/beta/search")).willReturn(aResponse()
                .withHeader("Content-Type", "application/xml").withBody(fixture("beta/contract-example.xml"))));
        providers.stubFor(get(urlPathEqualTo("/gamma/export")).willReturn(aResponse()
                .withHeader("Content-Type", "text/csv; charset=UTF-8").withBody(fixture("gamma/contract-example.csv"))));
    }

    private Response call(String method, String path, String jsonBody) {
        RestClient client = RestClient.create("http://localhost:" + port);
        RestClient.RequestBodySpec request = client.method(org.springframework.http.HttpMethod.valueOf(method)).uri(path);
        if (jsonBody != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(jsonBody);
        }
        return request.exchange((req, res) -> new Response(res.getStatusCode().value(),
                new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private static String fixture(String name) {
        try (InputStream in = FlightAggregatorApplicationIT.class.getResourceAsStream("/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Response(int status, String body) {
        JsonNode json() {
            return JSON.readTree(body);
        }
    }
}
