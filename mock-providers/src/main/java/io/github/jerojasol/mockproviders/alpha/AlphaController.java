package io.github.jerojasol.mockproviders.alpha;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

import io.github.jerojasol.mockproviders.generator.InventoryGenerator;
import io.github.jerojasol.mockproviders.generator.ProviderId;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/alpha")
public class AlphaController {

    private static final Pattern IATA = Pattern.compile("[A-Z]{3}");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd");

    private final InventoryGenerator generator;

    public AlphaController(InventoryGenerator generator) {
        this.generator = generator;
    }

    @GetMapping(path = "/flights", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> flights(@RequestParam(required = false) String from,
                                     @RequestParam(required = false) String to,
                                     @RequestParam(required = false) String date) {
        if (!isIata(from)) {
            return badRequest("Parameter 'from' must be a 3-letter IATA code");
        }
        if (!isIata(to)) {
            return badRequest("Parameter 'to' must be a 3-letter IATA code");
        }
        LocalDate day;
        try {
            day = date == null ? null : LocalDate.parse(date, DATE);
        } catch (DateTimeParseException e) {
            day = null;
        }
        if (day == null) {
            return badRequest("Parameter 'date' must be a date in yyyy-MM-dd format");
        }
        List<AlphaItinerary> body = generator.generate(from, to, day, ProviderId.ALPHA).stream()
                .map(AlphaMapper::toAlpha)
                .toList();
        return ResponseEntity.ok(body);
    }

    private static boolean isIata(String code) {
        return code != null && IATA.matcher(code).matches();
    }

    private static ResponseEntity<AlphaError> badRequest(String message) {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AlphaError("BAD_REQUEST", message, null));
    }
}
