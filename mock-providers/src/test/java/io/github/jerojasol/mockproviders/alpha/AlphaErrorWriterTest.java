package io.github.jerojasol.mockproviders.alpha;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class AlphaErrorWriterTest {

    @Test
    void should_write_internal_json_error_when_chaos_fails_request() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new AlphaErrorWriter(JsonMapper.builder().build()).writeInternalError(response, "a1b2c3");

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).isEqualTo(
                "{\"error\":\"INTERNAL\",\"message\":\"Upstream inventory unavailable\",\"ref\":\"a1b2c3\"}");
    }
}
