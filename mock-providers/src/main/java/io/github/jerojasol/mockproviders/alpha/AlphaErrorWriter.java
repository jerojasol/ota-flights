package io.github.jerojasol.mockproviders.alpha;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Writes Alpha's chaos 500 body; public because the chaos filter lives in another package. */
@Component
public class AlphaErrorWriter {

    private final JsonMapper jsonMapper;

    public AlphaErrorWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void writeInternalError(HttpServletResponse response, String ref) throws IOException {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(),
                new AlphaError("INTERNAL", "Upstream inventory unavailable", ref));
    }
}
