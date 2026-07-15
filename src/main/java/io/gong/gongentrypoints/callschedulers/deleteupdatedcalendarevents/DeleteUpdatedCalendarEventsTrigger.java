package io.gong.gongentrypoints.callschedulers.deleteupdatedcalendarevents;

import io.gong.gongentrypoints.callschedulers.CallSchedulersTarget;
import io.gong.gongentrypoints.callschedulers.CallSchedulersTarget.Mode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trigger for the CallScheduler <b>delete old updated_calendar_event rows scheduled task</b>.
 *
 * <p>Prunes rows from {@code updated_calendar_event} older than {@code days-back}. Every
 * {@code send-calendar-event} call writes a row there; this task keeps the table from growing
 * unbounded during local loop testing. Equivalent to the nightly cleanup cron.
 *
 * <p>Pass {@code X-CallSchedulers-Target: hybrid} to hit the hybrid env instead of localhost.
 *
 * <p>Downstream call:
 * {@code POST /troubleshooting/updated-calendar-events/run-scheduled-task?daysBackToDelete={n}}
 * on {@code CallScheduler} ({@code TroubleshootingDeleteUpdatedCalendarEvents.runDeleteUpdatedCalendarEventsScheduledTask()}).
 */
@RestController
public class DeleteUpdatedCalendarEventsTrigger {

    private static final String PATH =
            "/troubleshooting/updated-calendar-events/run-scheduled-task";

    private final CallSchedulersTarget callSchedulersTarget;

    public DeleteUpdatedCalendarEventsTrigger(CallSchedulersTarget callSchedulersTarget) {
        this.callSchedulersTarget = callSchedulersTarget;
    }

    @PostMapping("/callschedulers/delete-updated-calendar-events")
    public String deleteUpdatedCalendarEvents(
            @RequestParam(value = "days-back", defaultValue = "1") long daysBack,
            @RequestHeader(value = "X-CallSchedulers-Target", required = false, defaultValue = "local") Mode target) {
        return callSchedulersTarget.client(target).post()
                .uri(uriBuilder -> uriBuilder.path(PATH)
                        .queryParam("daysBackToDelete", daysBack)
                        .build())
                .retrieve()
                .body(String.class);
    }
}
