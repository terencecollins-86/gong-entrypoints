package io.gong.gongentrypoints.callschedulers.recurringscheduledtaskforevent;

import io.gong.gongentrypoints.callschedulers.CallSchedulersTarget;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trigger for the CallScheduler <b>recurring-events scheduled task scoped to a single iCalUID</b>.
 *
 * <p>Runs the same logic as {@code run-recurring-scheduled-task} but restricts execution to one
 * recurring series identified by {@code company-id} + {@code ical-uid}. Use this when the
 * blanket task is too broad — e.g. to debug why a specific series isn't generating upcoming
 * occurrences.
 *
 * <p>Downstream call:
 * {@code POST /troubleshooting/recurring-meetings/run-scheduled-task-for-specific-event}
 * on {@code CallScheduler} ({@code TroubleshootingRecurringEmailScheduling.runRecurringEventsCallScheduledTaskForSpecificEvent()}).
 */
@RestController
public class RunRecurringScheduledTaskForEventTrigger {

    private static final String PATH =
            "/troubleshooting/recurring-meetings/run-scheduled-task-for-specific-event";

    private final CallSchedulersTarget callSchedulersTarget;

    public RunRecurringScheduledTaskForEventTrigger(CallSchedulersTarget callSchedulersTarget) {
        this.callSchedulersTarget = callSchedulersTarget;
    }

    @PostMapping("/callschedulers/run-recurring-scheduled-task-for-event")
    public String runForEvent(
            @RequestParam("company-id") long companyId,
            @RequestParam("ical-uid") String icalUid,
            @RequestParam(value = "num-of-days", defaultValue = "14") int numOfDays,
            @RequestParam(value = "include-cancelled", defaultValue = "false") boolean includeCancelled) {
        return callSchedulersTarget.client().post()
                .uri(uriBuilder -> uriBuilder.path(PATH)
                        .queryParam("company-id", companyId)
                        .queryParam("ical-uid", icalUid)
                        .queryParam("num-of-days", numOfDays)
                        .queryParam("include_cancelled_event", includeCancelled)
                        .build())
                .retrieve()
                .body(String.class);
    }
}
