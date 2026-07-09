package io.gong.gongentrypoints.telephonysystems.backfill;

import io.gong.gongentrypoints.telephonysystems.TelephonyTarget;
import io.gong.gongentrypoints.telephonysystems.TelephonyTarget.Mode;
import io.gong.gongentrypoints.telephonysystems.TriggerLoop;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
 * <p>Pass {@code X-Telephony-Target: hybrid} to hit the hybrid env instead of localhost.
 * Absent or {@code local} targets {@code telephony.base-url} (default localhost:8097).
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

    private final TelephonyTarget telephonyTarget;
    private final TriggerLoop triggerLoop = new TriggerLoop();

    public BackfillTrigger(TelephonyTarget telephonyTarget) {
        this.telephonyTarget = telephonyTarget;
    }

    @PostMapping("/telephonysystems/backfill")
    public String triggerBackfillMarkedTss(
            @RequestParam(required = false) String loop,
            @RequestHeader(value = "X-Telephony-Target", required = false, defaultValue = "local") Mode target) {
        return triggerLoop.run(loop, () -> fireOnce(target));
    }

    /** Stops an in-progress {@code loop=true} run. */
    @PostMapping("/telephonysystems/backfill/stop")
    public String stop() {
        return triggerLoop.stop();
    }

    private String fireOnce(Mode target) {
        return telephonyTarget.client(target).post()
                .uri(BACKFILL_PATH)
                .retrieve()
                .body(String.class);
    }
}
