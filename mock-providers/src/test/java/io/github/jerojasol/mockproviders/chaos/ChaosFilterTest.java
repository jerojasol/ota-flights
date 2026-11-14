package io.github.jerojasol.mockproviders.chaos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest(properties = {
        "mock.inventory-size=20",
        "mock.chaos.enabled=true",
        "mock.chaos.latency-min=0ms",
        "mock.chaos.latency-max=0ms",
        "mock.chaos.error-rate=0.0"})
@AutoConfigureMockMvc
class ChaosFilterTest {

    private static final String ALPHA = "/alpha/flights?from=MAD&to=JFK&date=2026-11-20";
    private static final String BETA = "/beta/search?origin=MAD&destination=JFK&departureDate=2026-11-20";
    private static final String GAMMA = "/gamma/export?o=MAD&d=JFK&dt=20261120";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ChaosState state;

    @AfterEach
    void reset() {
        configure(0, 0, 0.0);
    }

    @Test
    void should_fail_alpha_with_json_error_when_error_rate_is_one() {
        configure(0, 0, 1.0);

        for (int i = 0; i < 5; i++) {
            MvcTestResult result = mvc.get().uri(ALPHA).exchange();

            assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
            assertThat(result).bodyJson().extractingPath("$.error").isEqualTo("INTERNAL");
            assertThat(result).bodyJson().extractingPath("$.ref").asString().matches("[0-9a-f]{6}");
        }
    }

    @Test
    void should_fail_beta_with_xml_fault_when_error_rate_is_one() {
        configure(0, 0, 1.0);

        MvcTestResult result = mvc.get().uri(BETA).exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_XML);
        assertThat(result).body().asString()
                .contains("<Fault xmlns=\"urn:beta-gds:search:v2\">")
                .contains("<Code>SYSTEM_ERROR</Code>");
    }

    @Test
    void should_fail_gamma_with_plain_text_when_error_rate_is_one() {
        configure(0, 0, 1.0);

        MvcTestResult result = mvc.get().uri(GAMMA).exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .hasContentTypeCompatibleWith(MediaType.TEXT_PLAIN);
        assertThat(result).body().asString().matches("ERR 500 export failed ref=[0-9a-f]{6}\\n");
    }

    @Test
    void should_pass_requests_through_when_error_rate_is_zero() {
        configure(0, 0, 0.0);

        for (int i = 0; i < 5; i++) {
            assertThat(mvc.get().uri(ALPHA).exchange()).hasStatusOk();
            assertThat(mvc.get().uri(BETA).exchange()).hasStatusOk();
        }
    }

    @Test
    void should_pass_requests_through_when_chaos_is_disabled() {
        state.update(c -> new ChaosConfig(false, c.latencyMin(), c.latencyMax(), 1.0));

        assertThat(mvc.get().uri(ALPHA).exchange()).hasStatusOk();
    }

    @Test
    void should_delay_request_when_latency_is_fixed() {
        configure(200, 200, 0.0);

        long start = System.nanoTime();
        MvcTestResult result = mvc.get().uri(ALPHA).exchange();
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(result).hasStatusOk();
        assertThat(elapsedMs).isGreaterThanOrEqualTo(200);
    }

    @Test
    void should_delay_error_responses_too_when_request_fails() {
        configure(150, 150, 1.0);

        long start = System.nanoTime();
        MvcTestResult result = mvc.get().uri(ALPHA).exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(Duration.ofNanos(System.nanoTime() - start).toMillis()).isGreaterThanOrEqualTo(150);
    }

    @Test
    void should_keep_health_green_when_error_rate_is_one() {
        configure(500, 500, 1.0);

        long start = System.nanoTime();
        MvcTestResult result = mvc.get().uri("/actuator/health").exchange();

        assertThat(result).hasStatusOk();
        assertThat(Duration.ofNanos(System.nanoTime() - start).toMillis()).isLessThan(500);
    }

    @Test
    void should_keep_admin_reachable_when_error_rate_is_one() {
        configure(0, 0, 1.0);

        assertThat(mvc.get().uri("/admin/chaos").exchange()).hasStatusOk();
    }

    @Test
    void should_merge_patch_and_keep_other_fields_when_error_rate_is_updated() {
        configure(100, 300, 0.1);

        MvcTestResult put = mvc.put().uri("/admin/chaos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"errorRate\":0.5}").exchange();

        assertThat(put).hasStatusOk();
        assertThat(put).bodyJson().extractingPath("$.errorRate").isEqualTo(0.5);
        MvcTestResult get = mvc.get().uri("/admin/chaos").exchange();
        assertThat(get).bodyJson().extractingPath("$.errorRate").isEqualTo(0.5);
        assertThat(get).bodyJson().extractingPath("$.enabled").isEqualTo(true);
        assertThat(get).bodyJson().extractingPath("$.latencyMin").isEqualTo("100ms");
        assertThat(get).bodyJson().extractingPath("$.latencyMax").isEqualTo("300ms");
    }

    @Test
    void should_accept_simple_and_iso_durations_when_latency_is_updated() {
        MvcTestResult put = mvc.put().uri("/admin/chaos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"latencyMin\":\"PT0.1S\",\"latencyMax\":\"2s\"}").exchange();

        assertThat(put).hasStatusOk();
        assertThat(put).bodyJson().extractingPath("$.latencyMin").isEqualTo("100ms");
        assertThat(put).bodyJson().extractingPath("$.latencyMax").isEqualTo("2000ms");
    }

    @Test
    void should_reject_and_keep_config_when_error_rate_is_out_of_range() {
        configure(0, 0, 0.2);

        MvcTestResult put = mvc.put().uri("/admin/chaos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"errorRate\":2}").exchange();

        assertThat(put).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(put).bodyJson().extractingPath("$.error").isEqualTo("BAD_REQUEST");
        assertThat(state.get().errorRate()).isEqualTo(0.2);
    }

    @Test
    void should_reject_when_latency_bounds_are_invalid() {
        assertThat(mvc.put().uri("/admin/chaos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"latencyMin\":\"3s\",\"latencyMax\":\"1s\"}").exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.put().uri("/admin/chaos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"latencyMax\":\"31s\"}").exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.put().uri("/admin/chaos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"latencyMax\":\"soon\"}").exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    private void configure(long minMs, long maxMs, double errorRate) {
        state.update(c -> new ChaosConfig(true, Duration.ofMillis(minMs), Duration.ofMillis(maxMs), errorRate));
    }
}
