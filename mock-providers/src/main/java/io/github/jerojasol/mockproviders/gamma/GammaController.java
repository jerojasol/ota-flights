package io.github.jerojasol.mockproviders.gamma;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

import io.github.jerojasol.mockproviders.generator.GeneratedItinerary;
import io.github.jerojasol.mockproviders.generator.InventoryGenerator;
import io.github.jerojasol.mockproviders.generator.ProviderId;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/gamma")
public class GammaController {

    static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);
    static final int FLUSH_EVERY = 200;

    private static final Pattern IATA = Pattern.compile("[A-Z]{3}");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuuMMdd");

    private final InventoryGenerator generator;

    public GammaController(InventoryGenerator generator) {
        this.generator = generator;
    }

    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(@RequestParam(required = false) String o,
                                                       @RequestParam(required = false) String d,
                                                       @RequestParam(required = false) String dt) {
        if (o == null) {
            throw new GammaBadRequest("missing parameter: o");
        }
        if (!IATA.matcher(o).matches()) {
            throw new GammaBadRequest("invalid parameter: o");
        }
        if (d == null) {
            throw new GammaBadRequest("missing parameter: d");
        }
        if (!IATA.matcher(d).matches()) {
            throw new GammaBadRequest("invalid parameter: d");
        }
        if (dt == null) {
            throw new GammaBadRequest("missing parameter: dt");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(dt, DATE);
        } catch (DateTimeParseException e) {
            throw new GammaBadRequest("invalid parameter: dt");
        }
        List<GeneratedItinerary> inventory = generator.generate(o, d, date, ProviderId.GAMMA);
        StreamingResponseBody body = out -> {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            writeLine(writer, GammaLineFormatter.HEADER);
            int written = 0;
            for (GeneratedItinerary itinerary : inventory) {
                writeLine(writer, GammaLineFormatter.format(itinerary));
                if (++written % FLUSH_EVERY == 0) {
                    writer.flush();
                }
            }
            writeLine(writer, GammaLineFormatter.TRAILER);
            writer.flush();
        };
        return ResponseEntity.ok().contentType(TEXT_CSV).body(body);
    }

    private static void writeLine(BufferedWriter writer, String line) throws IOException {
        writer.write(line);
        writer.write('\n');
    }

    @ExceptionHandler(GammaBadRequest.class)
    ResponseEntity<String> badRequest(GammaBadRequest e) {
        String detail = e.getMessage();
        return ResponseEntity.badRequest()
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body("ERR 400 " + detail + "\n");
    }

    static final class GammaBadRequest extends RuntimeException {
        GammaBadRequest(String detail) {
            super(detail, null, false, false);
        }
    }
}
