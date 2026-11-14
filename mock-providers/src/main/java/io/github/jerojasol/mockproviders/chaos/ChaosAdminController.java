package io.github.jerojasol.mockproviders.chaos;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.convert.DurationStyle;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/chaos")
public class ChaosAdminController {

    private final ChaosState state;

    public ChaosAdminController(ChaosState state) {
        this.state = state;
    }

    @GetMapping
    public ChaosView get() {
        return ChaosView.of(state.get());
    }

    @PutMapping
    public ChaosView update(@RequestBody ChaosPatch patch) {
        Duration latencyMin = parse(patch.latencyMin(), "latencyMin");
        Duration latencyMax = parse(patch.latencyMax(), "latencyMax");
        return ChaosView.of(state.update(current -> new ChaosConfig(
                patch.enabled() != null ? patch.enabled() : current.enabled(),
                latencyMin != null ? latencyMin : current.latencyMin(),
                latencyMax != null ? latencyMax : current.latencyMax(),
                patch.errorRate() != null ? patch.errorRate() : current.errorRate())));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<AdminError> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new AdminError("BAD_REQUEST", e.getMessage()));
    }

    private static Duration parse(String value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return DurationStyle.detectAndParse(value, ChronoUnit.MILLIS);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " is not a valid duration: " + value, e);
        }
    }

    public record ChaosPatch(Boolean enabled, String latencyMin, String latencyMax, Double errorRate) {
    }

    public record ChaosView(boolean enabled, String latencyMin, String latencyMax, double errorRate) {

        static ChaosView of(ChaosConfig config) {
            return new ChaosView(config.enabled(), config.latencyMin().toMillis() + "ms",
                    config.latencyMax().toMillis() + "ms", config.errorRate());
        }
    }

    public record AdminError(String error, String message) {
    }
}
