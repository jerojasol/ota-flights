package io.github.jerojasol.mockproviders.beta;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.dataformat.xml.XmlMapper;

class BetaFaultWriterTest {

    @Test
    void should_write_system_error_fault_when_chaos_fails_request() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new BetaFaultWriter(XmlMapper.builder().build()).writeSystemError(response, "b4c5d6");

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentType()).startsWith("application/xml");
        assertThat(response.getContentAsString()).isEqualTo(
                "<Fault xmlns=\"urn:beta-gds:search:v2\"><Code>SYSTEM_ERROR</Code>"
                        + "<Message>Inventory backend timed out</Message><Reference>b4c5d6</Reference></Fault>");
    }
}
