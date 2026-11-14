package io.github.jerojasol.mockproviders.alpha;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AlphaError(String error, String message, String ref) {
}
