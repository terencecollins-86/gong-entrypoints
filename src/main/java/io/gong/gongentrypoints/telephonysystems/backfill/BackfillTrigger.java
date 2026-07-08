package io.gong.gongentrypoints.telephonysystems.backfill;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Trigger for the Telephony Systems <b>backfill marked TSs</b> smoke test — the simplest entry
 * point in the "02 - Data Flows" doc (zero-arg, no payload). Firing it proves the whole
 * request → Supervisor → troubleshooter loop is working.
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

    private final RestClient telephonyRestClient;

    public BackfillTrigger(RestClient telephonyRestClient) {
        this.telephonyRestClient = telephonyRestClient;
    }

    @PostMapping("/triggers/backfill-marked-tss")
    public String triggerBackfillMarkedTss() {
        return telephonyRestClient.post()
                .uri(BACKFILL_PATH)
                .retrieve()
                .body(String.class);
    }
}
