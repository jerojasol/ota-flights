package io.github.jerojasol.flights.application.fakes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Stores submitted tasks so the test decides when a background refresh runs. */
public final class CapturingExecutor implements Executor {

    private final List<Runnable> pending = new ArrayList<>();

    @Override
    public void execute(Runnable task) {
        pending.add(task);
    }

    public int pending() {
        return pending.size();
    }

    public void runAll() {
        List<Runnable> tasks = List.copyOf(pending);
        pending.clear();
        tasks.forEach(Runnable::run);
    }
}
