package io.github.jerojasol.mockproviders.chaos;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import io.github.jerojasol.mockproviders.config.MockProperties;

/** Runtime chaos configuration, replaced atomically by the admin endpoint. */
public class ChaosState {

    private final AtomicReference<ChaosConfig> current;

    public ChaosState(MockProperties.Chaos initial) {
        this.current = new AtomicReference<>(new ChaosConfig(initial.enabled(), initial.latencyMin(),
                initial.latencyMax(), initial.errorRate()));
    }

    public ChaosConfig get() {
        return current.get();
    }

    /** Applies {@code change}; an invalid result throws and leaves the state untouched. */
    public ChaosConfig update(UnaryOperator<ChaosConfig> change) {
        return current.updateAndGet(change);
    }
}
