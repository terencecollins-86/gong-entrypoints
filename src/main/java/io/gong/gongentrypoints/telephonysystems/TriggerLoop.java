package io.gong.gongentrypoints.telephonysystems;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Reusable once / N-times / loop-until-stopped runner shared by entrypoint triggers.
 *
 * <p>Create one instance per trigger — it holds that trigger's stop flag. Repeat behaviour is
 * driven by the {@code loop} value: absent/blank → once (returns the action's result),
 * {@code loop=N} → N times, {@code loop=true} → loop until {@link #stop()}. Iterations are spaced
 * {@link #ITERATION_DELAY_MS} apart.
 */
public class TriggerLoop {

    /** Delay between iterations when firing N times or looping. */
    private static final long ITERATION_DELAY_MS = 1_000;

    /** True while a {@code loop=true} run is active; flipped off by {@link #stop()}. */
    private final AtomicBoolean looping = new AtomicBoolean(false);

    public String run(String loop, Supplier<String> action) {
        if (loop == null || loop.isBlank()) {
            return action.get();
        }
        if ("true".equalsIgnoreCase(loop)) {
            return loopUntilStopped(action);
        }
        int times;
        try {
            times = Integer.parseInt(loop.trim());
        } catch (NumberFormatException e) {
            return "Invalid 'loop' value '" + loop + "'. Use a number (loop=10) or loop=true.";
        }
        if (times < 1) {
            return "Invalid 'loop' value '" + loop + "'. Must be >= 1.";
        }
        return fireNTimes(times, action);
    }

    /** Stops an in-progress {@code loop=true} run. */
    public String stop() {
        looping.set(false);
        return "Loop stop requested";
    }

    private String fireNTimes(int times, Supplier<String> action) {
        for (int i = 0; i < times; i++) {
            action.get();
            if (i < times - 1 && !sleepBetweenIterations()) {
                return "Interrupted after " + (i + 1) + "/" + times + " iterations";
            }
        }
        return "Fired " + times + " times";
    }

    private String loopUntilStopped(Supplier<String> action) {
        if (!looping.compareAndSet(false, true)) {
            return "Loop already running";
        }
        int count = 0;
        try {
            while (looping.get()) {
                action.get();
                count++;
                if (!sleepBetweenIterations()) {
                    break;
                }
            }
        } finally {
            looping.set(false);
        }
        return "Loop stopped after " + count + " iterations";
    }

    /** Sleeps between iterations; returns false if the thread was interrupted. */
    private boolean sleepBetweenIterations() {
        try {
            Thread.sleep(ITERATION_DELAY_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
