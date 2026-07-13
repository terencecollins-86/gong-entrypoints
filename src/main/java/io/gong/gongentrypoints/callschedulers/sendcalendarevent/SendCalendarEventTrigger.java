package io.gong.gongentrypoints.callschedulers.sendcalendarevent;

import io.gong.gongentrypoints.callschedulers.CallSchedulersTarget;
import io.gong.gongentrypoints.callschedulers.CallSchedulersTarget.Mode;
import io.gong.gongentrypoints.telephonysystems.TriggerLoop;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trigger for <b>send calendar event through core scheduling pipeline</b> — the primary
 * CallScheduler processing path. Submits a {@code CallSchedulingRequest} JSON (type
 * {@code CALENDAR_EVENT}) to the troubleshooter, which enqueues it on the
 * {@code CALL_SCHEDULING_REQUESTS} Kafka topic. The consumer picks it up and drives the full
 * pipeline: distributed lock → {@code callSchedulingRequestsService.callIncomingCalendarInviteHandler()}
 * → validation chain → scheduling decision.
 *
 * <p>Set a breakpoint at {@code CallSchedulingRequestsConsumer.accept()} to observe the full flow.
 *
 * <p>Pass {@code X-CallSchedulers-Target: hybrid} to hit the hybrid env instead of localhost.
 *
 * <p>Downstream call:
 * {@code POST /troubleshooting/call-scheduling-requests-consumer/sendEventJson}
 * on {@code CallScheduler} ({@code TroubleshootingCallSchedulingRequestsConsumer.sendEventJson()}).
 */
@RestController
public class SendCalendarEventTrigger {

    private static final String SEND_EVENT_PATH =
            "/troubleshooting/call-scheduling-requests-consumer/sendEventJson";

    private final CallSchedulersTarget callSchedulersTarget;
    private final TriggerLoop triggerLoop = new TriggerLoop();

    public SendCalendarEventTrigger(CallSchedulersTarget callSchedulersTarget) {
        this.callSchedulersTarget = callSchedulersTarget;
    }

    @PostMapping(value = "/callschedulers/send-calendar-event", consumes = MediaType.APPLICATION_JSON_VALUE)
    public String sendCalendarEvent(
            @RequestParam(required = false) String loop,
            @RequestHeader(value = "X-CallSchedulers-Target", required = false, defaultValue = "local") Mode target,
            @RequestBody String callSchedulingRequest) {
        return triggerLoop.run(loop, () -> fireOnce(target, callSchedulingRequest));
    }

    /** Stops an in-progress {@code loop=true} run. */
    @PostMapping("/callschedulers/send-calendar-event/stop")
    public String stop() {
        return triggerLoop.stop();
    }

    private String fireOnce(Mode target, String callSchedulingRequest) {
        return callSchedulersTarget.client(target).post()
                .uri(SEND_EVENT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(callSchedulingRequest)
                .retrieve()
                .body(String.class);
    }
}
