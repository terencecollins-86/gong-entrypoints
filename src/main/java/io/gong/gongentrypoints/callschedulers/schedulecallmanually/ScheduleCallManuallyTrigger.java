package io.gong.gongentrypoints.callschedulers.schedulecallmanually;

import io.gong.gongentrypoints.callschedulers.CallSchedulersTarget;
import io.gong.gongentrypoints.telephonysystems.TriggerLoop;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trigger for <b>schedule a new call manually</b> — the direct scheduling path that bypasses
 * calendar and email ingestion. Stands in for the Gong UI action a user (or internal service)
 * takes to tell Gong "record this meeting"; writes a {@code MANUAL}-mechanism row into
 * {@code scheduled_calls}. This is the second independent source of new scheduled calls (the
 * first being send-calendar-event), so it's the fastest way to stream fresh MANUAL rows into a
 * local DB without a calendar or Kafka event.
 *
 * <p>Each send uses a fresh {@code callId} and current-relative start/end times so a
 * {@code loop=N} run produces N genuinely-distinct scheduled calls.
 *
 * <p>Downstream call:
 * {@code POST /scheduledCallsActions/scheduleNewCallManually?companyId={companyId}} on
 * {@code CallScheduler} ({@code ScheduledCallsActionsController.scheduleNewCallManually()}),
 * body {@code ManualSchedulingCallDetails}.
 */
@RestController
public class ScheduleCallManuallyTrigger {

    private static final String SCHEDULE_MANUALLY_PATH = "/scheduledCallsActions/scheduleNewCallManually";

    // Local-dev seed (seed-callscheduler-local.sql): company 9001 / workspace 1001 / user 501 (alice),
    // provider 'zoom' enabled in company_recorder_properties.
    private static final long DEFAULT_COMPANY_ID = 9001L;
    private static final long DEFAULT_WORKSPACE_ID = 1001L;
    private static final long DEFAULT_APP_USER_ID = 501L;
    private static final String DEFAULT_PROVIDER = "ZOOM";
    private static final String DEFAULT_CALL_URL = "https://zoom.us/j/123456789";

    private final CallSchedulersTarget callSchedulersTarget;
    private final TriggerLoop triggerLoop = new TriggerLoop();

    public ScheduleCallManuallyTrigger(CallSchedulersTarget callSchedulersTarget) {
        this.callSchedulersTarget = callSchedulersTarget;
    }

    @PostMapping("/callschedulers/schedule-call-manually")
    public String scheduleCallManually(
            @RequestParam(value = "loop", required = false) String loop,
            @RequestParam(value = "company-id", required = false, defaultValue = "9001") long companyId,
            @RequestParam(value = "workspace-id", required = false, defaultValue = "1001") long workspaceId,
            @RequestParam(value = "user-id", required = false, defaultValue = "501") long appUserId,
            @RequestParam(value = "provider", required = false, defaultValue = DEFAULT_PROVIDER) String provider) {
        // Build the payload inside the loop lambda so every iteration gets a fresh callId + times.
        return triggerLoop.run(loop, () -> fireOnce(companyId, buildDetails(companyId, workspaceId, appUserId, provider)));
    }

    /** Stops an in-progress {@code loop=true} run. */
    @PostMapping("/callschedulers/schedule-call-manually/stop")
    public String stop() {
        return triggerLoop.stop();
    }

    /** Fresh callId + future start/end per send, so loop=N creates N distinct scheduled calls. */
    private ManualCallDetails buildDetails(long companyId, long workspaceId, long appUserId, String provider) {
        long callId = ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L);
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        return new ManualCallDetails(
                callId, companyId, workspaceId, appUserId, provider, DEFAULT_CALL_URL,
                "Manual Schedule Smoke Test", start.toString(), end.toString(), null,
                List.of(new Invitee("Bob Acme", "bob@acme-corp.com", "ACCEPTED", "PARTICIPANT")),
                false, false);
    }

    private String fireOnce(long companyId, ManualCallDetails details) {
        callSchedulersTarget.client().post()
                .uri(uriBuilder -> uriBuilder.path(SCHEDULE_MANUALLY_PATH).queryParam("companyId", companyId).build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(details)
                .retrieve()
                .toBodilessEntity();
        return "Done — scheduled manual callId=" + details.callId() + " for companyId=" + companyId;
    }

    /**
     * Mirrors CallScheduler's {@code ManualSchedulingCallDetails} field names. Times are ISO-8601
     * strings (server parses them to {@code Instant}); {@code callProviderCode} is the provider enum
     * name (e.g. {@code ZOOM}).
     */
    private record ManualCallDetails(
            long callId, long companyId, long workspaceId, long appUserId, String callProviderCode,
            String callUrl, String callTitle, String callStartDateTime, String callEndDateTime,
            String recordingParams, List<Invitee> calendarInvitees,
            boolean isInternalMeeting, boolean isInterview) {
    }

    /** Mirrors CallScheduler's {@code CalendarInviteeCommon}. */
    private record Invitee(String name, String emailAddress, String responseStatus, String role) {
    }
}
