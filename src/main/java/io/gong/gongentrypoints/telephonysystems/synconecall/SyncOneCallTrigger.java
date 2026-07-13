package io.gong.gongentrypoints.telephonysystems.synconecall;

import io.gong.gongentrypoints.telephonysystems.TriggerLoop;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Trigger for <b>sync one call</b> — entrypoint #3 in "Entrypoints Within the Telephony System"
 * (the pull/SYNC counterpart to process-call-event). Fetches a single call from the provider by
 * id and runs it through the full sync pipeline ({@code dialerServicesManager.syncOneCall(...)}).
 *
 * <p>All inputs are query params (no body). {@code integration-flavor} is <b>not</b> passed — the
 * downstream derives it from {@code company-id} + {@code integration-id}. The {@code loop} query
 * param controls repeat behaviour (absent → once, {@code loop=N} → N times, {@code loop=true} →
 * loop until {@code .../stop}).
 *
 * <p>Downstream call:
 * {@code POST /troubleshooting/telephony-system-pci-compliant/generic/calls/syncOneCall}
 * on {@code IngesterTelephonySystemsSupervisor}
 * ({@code IngesterTelephonySystemsTroubleshooter.syncOneCall()}).
 */
@RestController
public class SyncOneCallTrigger {

    private static final String SYNC_ONE_CALL_PATH =
            "/troubleshooting/telephony-system-pci-compliant/generic/calls/syncOneCall";

    private final RestClient telephonyRestClient;
    private final TriggerLoop triggerLoop = new TriggerLoop();

    public SyncOneCallTrigger(RestClient telephonyRestClient) {
        this.telephonyRestClient = telephonyRestClient;
    }

    @PostMapping("/telephonysystems/sync-one-call")
    public String triggerSyncOneCall(
            @RequestParam("company-id") long companyId,
            @RequestParam("integration-id") long integrationId,
            @RequestParam("providerCallId") String providerCallId,
            @RequestParam(name = "callDate", required = false) String callDate,
            @RequestParam(name = "callUrl", required = false) String callUrl,
            @RequestParam(required = false) String loop) {
        return triggerLoop.run(loop,
                () -> fireOnce(companyId, integrationId, providerCallId, callDate, callUrl));
    }

    /** Stops an in-progress {@code loop=true} run. */
    @PostMapping("/telephonysystems/sync-one-call/stop")
    public String stop() {
        return triggerLoop.stop();
    }

    private String fireOnce(long companyId, long integrationId, String providerCallId,
                            String callDate, String callUrl) {
        return telephonyRestClient.post()
                .uri(uriBuilder -> {
                    // Free-form string params are passed as URI template variables so any braces
                    // in their values are treated as literals and encoded, not parsed as templates.
                    Map<String, Object> vars = new HashMap<>();
                    vars.put("providerCallId", providerCallId);
                    uriBuilder.path(SYNC_ONE_CALL_PATH)
                            .queryParam("company-id", companyId)
                            .queryParam("integration-id", integrationId)
                            .queryParam("providerCallId", "{providerCallId}");
                    if (callDate != null) {
                        uriBuilder.queryParam("callDate", "{callDate}");
                        vars.put("callDate", callDate);
                    }
                    if (callUrl != null) {
                        uriBuilder.queryParam("callUrl", "{callUrl}");
                        vars.put("callUrl", callUrl);
                    }
                    return uriBuilder.build(vars);
                })
                .retrieve()
                .body(String.class);
    }
}
