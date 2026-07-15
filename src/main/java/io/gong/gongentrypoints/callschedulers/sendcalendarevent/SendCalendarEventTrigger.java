package io.gong.gongentrypoints.callschedulers.sendcalendarevent;

import io.gong.gongentrypoints.callschedulers.CallSchedulersTarget;
import io.gong.gongentrypoints.callschedulers.CallSchedulersTarget.Mode;
import io.gong.gongentrypoints.callschedulers.sendcalendarevent.CalendarEventFaker.Scenario;
import io.gong.gongentrypoints.callschedulers.sendcalendarevent.model.CallSchedulingRequest;
import io.gong.gongentrypoints.telephonysystems.TriggerLoop;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

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
    private final CalendarEventFaker calendarEventFaker;
    private final ObjectMapper objectMapper;
    private final TriggerLoop triggerLoop = new TriggerLoop();

    public SendCalendarEventTrigger(
            CallSchedulersTarget callSchedulersTarget,
            CalendarEventFaker calendarEventFaker,
            ObjectMapper objectMapper) {
        this.callSchedulersTarget = callSchedulersTarget;
        this.calendarEventFaker = calendarEventFaker;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/callschedulers/send-calendar-event")
    public String sendCalendarEvent(
            @RequestParam(required = false) String loop,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false, defaultValue = "NEW_CALL") String scenario,
            @RequestParam(required = false) String iCalUID,
            @RequestHeader(value = "X-CallSchedulers-Target", required = false, defaultValue = "local") Mode target,
            @RequestBody(required = false) String callSchedulingRequest) {
        Scenario resolvedScenario = parseScenario(scenario);
        // Resolve the payload inside the loop lambda so every iteration gets fresh event IDs.
        return triggerLoop.run(
                loop,
                () -> fireOnce(target, resolvePayload(callSchedulingRequest, resolvedScenario, companyId, userId, iCalUID)));
    }

    /**
     * Builds the JSON payload for a single send. Blank body → generated for the requested scenario;
     * body present → parsed and only the dynamic fields (event IDs + timestamps) overridden (the
     * scenario/iCalUID params are ignored on the override path).
     */
    private String resolvePayload(String body, Scenario scenario, Long companyId, Long userId, String iCalUID) {
        CallSchedulingRequest request;
        if (body == null || body.isBlank()) {
            request = calendarEventFaker.generate(scenario, companyId, userId, iCalUID);
        } else {
            request = calendarEventFaker.applyDynamicOverrides(
                    objectMapper.readValue(body, CallSchedulingRequest.class));
        }
        return objectMapper.writeValueAsString(request);
    }

    /** Case-insensitive parse of the {@code scenario} param with a clear 400 on an unknown value. */
    private Scenario parseScenario(String scenario) {
        try {
            return Scenario.valueOf(scenario.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            String valid = Arrays.stream(Scenario.values()).map(Enum::name).collect(Collectors.joining(", "));
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid 'scenario' value '" + scenario + "'. Valid values: " + valid);
        }
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
