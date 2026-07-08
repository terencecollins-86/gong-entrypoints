package io.gong.gongentrypoints.telephonysystems.processcallevent;

import io.gong.gongentrypoints.telephonysystems.TriggerLoop;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Trigger for <b>process one telephony call event</b> — Flow B (push) in the "02 - Data Flows"
 * doc. Drives the exact same core path as the main {@code TelephonyCallEventConsumer} Kafka
 * consumer ({@code dialerService.processCallEvent(...)}), but over HTTP.
 *
 * <p>The request body is a {@code TelephonyCallEvent} JSON, forwarded verbatim to the
 * troubleshooter. {@code integration-flavor} must match the event's provider (e.g.
 * {@code GONG_CONNECT_API}). The {@code loop} query param controls repeat behaviour
 * (absent → once, {@code loop=N} → N times, {@code loop=true} → loop until {@code .../stop}).
 *
 * <p>Downstream call:
 * {@code POST /troubleshooting/telephony-call-events/generic/telephony-call-event/process-one-event}
 * on {@code IngesterTelephonySystemsSupervisor}
 * ({@code TelephonyCallEventsTroubleshooter.processTelephonyCallEvent()}).
 */
@RestController
public class ProcessCallEventTrigger {

    private static final String PROCESS_EVENT_PATH =
            "/troubleshooting/telephony-call-events/generic/telephony-call-event/process-one-event";

    private final RestClient telephonyRestClient;
    private final TriggerLoop triggerLoop = new TriggerLoop();

    public ProcessCallEventTrigger(RestClient telephonyRestClient) {
        this.telephonyRestClient = telephonyRestClient;
    }

    @PostMapping(value = "/telephonysystems/process-call-event", consumes = MediaType.APPLICATION_JSON_VALUE)
    public String triggerProcessCallEvent(
            @RequestParam("integration-flavor") String integrationFlavor,
            @RequestParam(required = false) String loop,
            @RequestBody String telephonyCallEvent) {
        return triggerLoop.run(loop, () -> fireOnce(integrationFlavor, telephonyCallEvent));
    }

    /** Stops an in-progress {@code loop=true} run. */
    @PostMapping("/telephonysystems/process-call-event/stop")
    public String stop() {
        return triggerLoop.stop();
    }

    private String fireOnce(String integrationFlavor, String telephonyCallEvent) {
        return telephonyRestClient.post()
                .uri(uriBuilder -> uriBuilder.path(PROCESS_EVENT_PATH)
                        .queryParam("integration-flavor", integrationFlavor)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(telephonyCallEvent)
                .retrieve()
                .body(String.class);
    }
}
