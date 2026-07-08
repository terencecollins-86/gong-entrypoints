package io.gong.gongentrypoints.telephonysystems.backfill;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Trigger for the Telephony Systems <b>backfill marked TSs</b> smoke test — the simplest entry
 * point in the "02 - Data Flows" doc (zero-arg, no payload). Firing it proves the whole
 * request → Supervisor → troubleshooter loop is working.
 *
 * <p>The {@code loop} query param controls how many times it fires:
 * <ul>
 *   <li>absent → once</li>
 *   <li>{@code loop=N} (a number) → N times</li>
 *   <li>{@code loop=true} → loop until {@code POST /telephonysystems/backfill/stop}</li>
 * </ul>
 *
 * <p>Downstream call:
 * {@code POST /troubleshooting/telephony-system-pci-compliant/generic/backfill/backfillMarkedTSs}
 * on {@code IngesterTelephonySystemsSupervisor}
 * ({@code IngesterTelephonySystemsTroubleshooter.backfillMarkedUsers()}).
 */
@RestController
public class BackfillTrigger {

    private static final String BACKFILL_PATH =
            "/troubleshooting/telephony-system-pci-compliant/generic/backfill/backfillMarkedTSs";

    /** Delay between iterations when firing N times or looping. */
    private static final long ITERATION_DELAY_MS = 5_000;

    private final RestClient telephonyRestClient;

    /** True while a {@code loop=true} run is active; flipped off by {@link #stop()}. */
    private final AtomicBoolean looping = new AtomicBoolean(false);

    public BackfillTrigger(RestClient telephonyRestClient) {
        this.telephonyRestClient = telephonyRestClient;
    }

    @PostMapping("/telephonysystems/backfill")
    public String triggerBackfillMarkedTss(@RequestParam(required = false) String loop) {
        if (loop == null || loop.isBlank()) {
            return fireOnce();
        }
        if ("true".equalsIgnoreCase(loop)) {
            return loopUntilStopped();
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
        return fireNTimes(times);
    }

    /** Stops an in-progress {@code loop=true} run. */
    @PostMapping("/telephonysystems/backfill/stop")
    public String stop() {
        looping.set(false);
        return "Backfill loop stop requested";
    }

    private String fireOnce() {
        return telephonyRestClient.post()
                .uri(BACKFILL_PATH)
                .retrieve()
                .body(String.class);
    }

    private String fireNTimes(int times) {
        for (int i = 0; i < times; i++) {
            fireOnce();
            if (i < times - 1 && !sleepBetweenIterations()) {
                return "Backfill interrupted after " + (i + 1) + "/" + times + " iterations";
            }
        }
        return "Backfill fired " + times + " times";
    }

    private String loopUntilStopped() {
        if (!looping.compareAndSet(false, true)) {
            return "Backfill loop already running";
        }
        int count = 0;
        try {
            while (looping.get()) {
                fireOnce();
                count++;
                if (!sleepBetweenIterations()) {
                    break;
                }
            }
        } finally {
            looping.set(false);
        }
        return "Backfill loop stopped after " + count + " iterations";
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
