package io.github.jerojasol.mockproviders.beta;

import java.io.IOException;

import io.github.jerojasol.mockproviders.beta.BetaXml.Fault;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.dataformat.xml.XmlMapper;

/** Writes Beta's chaos 500 body; public because the chaos filter lives in another package. */
@Component
public class BetaFaultWriter {

    private final XmlMapper xmlMapper;

    public BetaFaultWriter(XmlMapper xmlMapper) {
        this.xmlMapper = xmlMapper;
    }

    public void writeSystemError(HttpServletResponse response, String reference) throws IOException {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setContentType(MediaType.APPLICATION_XML_VALUE);
        xmlMapper.writeValue(response.getOutputStream(),
                new Fault("SYSTEM_ERROR", "Inventory backend timed out", reference));
    }
}
