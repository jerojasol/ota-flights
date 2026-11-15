package io.github.jerojasol.mockproviders.beta;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import io.github.jerojasol.mockproviders.beta.BetaXml.Fault;
import io.github.jerojasol.mockproviders.config.MockProperties;
import io.github.jerojasol.mockproviders.generator.InventoryGenerator;
import io.github.jerojasol.mockproviders.generator.ProviderId;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/beta")
public class BetaController {

    private static final Pattern IATA = Pattern.compile("[A-Z]{3}");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd");
    private static final DateTimeFormatter REQUEST_DAY = DateTimeFormatter.ofPattern("uuuuMMdd");
    private static final String CURRENCY = "EUR";

    private final InventoryGenerator generator;
    private final BetaMapper mapper;
    private final AtomicInteger requestCounter = new AtomicInteger();

    public BetaController(InventoryGenerator generator, MockProperties properties) {
        this.generator = generator;
        this.mapper = new BetaMapper(properties.seed());
    }

    @GetMapping(path = "/search", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<?> search(@RequestParam(required = false) String origin,
                                    @RequestParam(required = false) String destination,
                                    @RequestParam(required = false) String departureDate) {
        if (origin == null) {
            return invalid("Parameter 'origin' is required");
        }
        if (!IATA.matcher(origin).matches()) {
            return invalid("Parameter 'origin' must be a 3-letter IATA code");
        }
        if (destination == null) {
            return invalid("Parameter 'destination' is required");
        }
        if (!IATA.matcher(destination).matches()) {
            return invalid("Parameter 'destination' must be a 3-letter IATA code");
        }
        if (departureDate == null) {
            return invalid("Parameter 'departureDate' is required");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(departureDate, DATE);
        } catch (DateTimeParseException e) {
            return invalid("Parameter 'departureDate' must be a date in yyyy-MM-dd format");
        }
        String requestId = "BET-%s-%04d".formatted(REQUEST_DAY.format(date),
                requestCounter.getAndUpdate(n -> (n + 1) % 10_000));
        return ResponseEntity.ok(mapper.toSearchResponse(
                generator.generate(origin, destination, date, ProviderId.BETA), requestId, CURRENCY));
    }

    private static ResponseEntity<Fault> invalid(String message) {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_XML)
                .body(new Fault("INVALID_REQUEST", message, null));
    }
}
