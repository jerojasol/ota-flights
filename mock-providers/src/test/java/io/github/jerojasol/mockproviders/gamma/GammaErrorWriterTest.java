package io.github.jerojasol.mockproviders.gamma;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class GammaErrorWriterTest {

    @Test
    void should_write_plain_text_export_failure_when_chaos_fails_request() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new GammaErrorWriter().writeExportFailed(response, "c7d8e9");

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentType()).startsWith("text/plain");
        assertThat(response.getContentAsString()).isEqualTo("ERR 500 export failed ref=c7d8e9\n");
    }
}
