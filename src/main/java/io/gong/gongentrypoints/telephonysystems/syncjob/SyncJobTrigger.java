package io.gong.gongentrypoints.telephonysystems.syncjob;

import io.gong.gongentrypoints.telephonysystems.TriggerLoop;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Trigger for the <b>High/Low-priority SyncJob (SQS)</b> executor — entrypoint #5 in "Entrypoints
 * Within the Telephony System" (the production periodic/backfill sync path). The executor's
 * {@code execute(...)} has no direct HTTP entry, so the SyncInfra troubleshooter offers two
 * on-demand ways to reach it, both exposed here:
 *
 * <ul>
 *   <li><b>run-chain-now</b> — flips the scheduled event's run-time to now so the chain enqueues a
 *       {@code SyncJob} on its own (most realistic). {@code is-backfill=true} routes to the
 *       low-priority (backfill) queue.</li>
 *   <li><b>send-message</b> — puts a {@code SyncJob} JSON on the queue directly, bypassing the
 *       scheduler and hitting {@code execute()} immediately. {@code high-priority} picks the queue.</li>
 * </ul>
 *
 * <p>Each variant has its own {@code loop}/{@code stop} (absent → once, {@code loop=N} → N times,
 * {@code loop=true} → loop until the matching {@code .../stop}).
 *
 * <p>Downstream calls (both on {@code IngesterTelephonySystemsSupervisor},
 * {@code IngesterTelephonySystemsSyncInfraTroubleshooter}):
 * {@code POST /troubleshooting/time-based-events-sync-infra/syncJobInfra/SyncJobChain/runChainNow}
 * and {@code POST /troubleshooting/time-based-events-sync-infra/sqs/sendMessage}.
 */
@RestController
public class SyncJobTrigger {

    private static final String RUN_CHAIN_NOW_PATH =
            "/troubleshooting/time-based-events-sync-infra/syncJobInfra/SyncJobChain/runChainNow";
    private static final String SEND_MESSAGE_PATH =
            "/troubleshooting/time-based-events-sync-infra/sqs/sendMessage";

    private final RestClient telephonyRestClient;
    private final TriggerLoop runChainLoop = new TriggerLoop();
    private final TriggerLoop sendMessageLoop = new TriggerLoop();

    public SyncJobTrigger(RestClient telephonyRestClient) {
        this.telephonyRestClient = telephonyRestClient;
    }

    /** Variant A — run the existing scheduled chain now (enqueues a SyncJob on its own). */
    @PostMapping("/telephonysystems/sync-job/run-chain-now")
    public String triggerRunChainNow(
            @RequestParam("company-id") long companyId,
            @RequestParam("integration-id") long integrationId,
            @RequestParam(name = "is-backfill", required = false, defaultValue = "false") boolean isBackfill,
            @RequestParam(required = false) String loop) {
        return runChainLoop.run(loop, () -> fireRunChainNow(companyId, integrationId, isBackfill));
    }

    @PostMapping("/telephonysystems/sync-job/run-chain-now/stop")
    public String stopRunChainNow() {
        return runChainLoop.stop();
    }

    /** Variant B — put a SyncJob JSON on the SQS queue directly (hits execute() immediately). */
    @PostMapping(value = "/telephonysystems/sync-job/send-message", consumes = MediaType.APPLICATION_JSON_VALUE)
    public String triggerSendMessage(
            @RequestParam(name = "high-priority", required = false, defaultValue = "true") boolean highPriority,
            @RequestParam(required = false) String loop,
            @RequestBody String syncJob) {
        return sendMessageLoop.run(loop, () -> fireSendMessage(highPriority, syncJob));
    }

    @PostMapping("/telephonysystems/sync-job/send-message/stop")
    public String stopSendMessage() {
        return sendMessageLoop.stop();
    }

    private String fireRunChainNow(long companyId, long integrationId, boolean isBackfill) {
        return telephonyRestClient.post()
                .uri(uriBuilder -> uriBuilder.path(RUN_CHAIN_NOW_PATH)
                        .queryParam("company-id", companyId)
                        .queryParam("integration-id", integrationId)
                        .queryParam("is-backfill", isBackfill)
                        .build())
                .retrieve()
                .body(String.class);
    }

    private String fireSendMessage(boolean highPriority, String syncJob) {
        // Pass the JSON as a URI template variable ({message}) so its braces are treated as a
        // literal value and encoded — not parsed as nested URI-template placeholders.
        return telephonyRestClient.post()
                .uri(uriBuilder -> uriBuilder.path(SEND_MESSAGE_PATH)
                        .queryParam("message", "{message}")
                        .queryParam("high-priority", highPriority)
                        .build(syncJob))
                .retrieve()
                .body(String.class);
    }
}
