package io.github.jerojasol.mockproviders.gamma;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Writes Gamma's chaos 500 body; public because the chaos filter lives in another package. */
@Component
public class GammaErrorWriter {

    public void writeExportFailed(HttpServletResponse response, String ref) throws IOException {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.getWriter().write("ERR 500 export failed ref=" + ref + "\n");
        response.getWriter().flush();
    }
}
