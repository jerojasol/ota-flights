package io.github.jerojasol.mockproviders.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "mock")
public record MockProperties(
        @DefaultValue("42") long seed,
        @DefaultValue("500") @Positive int inventorySize,
        @DefaultValue("0.30") @DecimalMin("0.0") @DecimalMax("1.0") double overlapRatio,
        @DefaultValue("0.05") @DecimalMin("0.0") @DecimalMax("1.0") double invalidRatio,
        @DefaultValue @Valid @NotNull Chaos chaos) {

    public record Chaos(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("100ms") @NotNull Duration latencyMin,
            @DefaultValue("5000ms") @NotNull Duration latencyMax,
            @DefaultValue("0.10") @DecimalMin("0.0") @DecimalMax("1.0") double errorRate) {
    }
}
