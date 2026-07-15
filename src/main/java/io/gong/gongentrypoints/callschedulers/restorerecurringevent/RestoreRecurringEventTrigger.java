package io.gong.gongentrypoints.callschedulers.restorerecurringevent;

import io.gong.gongentrypoints.callschedulers.CallSchedulersTarget;
import io.gong.gongentrypoints.callschedulers.CallSchedulersTarget.Mode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trigger for <b>restore a cancelled recurring event series</b>.
 *
 * <p>Clears the cancelled flag on the recurring rule in {@code calendar_recurring_event}, making
 * the series eligible for scheduling again. Use after {@code cancel-recurring-event} to test the
 * cancel → restore round-trip for recurring series. Parallel to {@code restore-call} for
 * one-time events.
 *
 * <p>Pass {@code X-CallSchedulers-Target: hybrid} to hit the hybrid env instead of localhost.
 *
 * <p>Downstream call:
 * {@code POST /troubleshooting/recurring-meetings/restore-cancelled-recurring-main-event}
 * on {@code CallScheduler} ({@code TroubleshootingRecurringEmailScheduling.restoreCancelledMainEvent()}).
 */
@RestController
public class RestoreRecurringEventTrigger {

    private static final String PATH =
            "/troubleshooting/recurring-meetings/restore-cancelled-recurring-main-event";

    private final CallSchedulersTarget callSchedulersTarget;

    public RestoreRecurringEventTrigger(CallSchedulersTarget callSchedulersTarget) {
        this.callSchedulersTarget = callSchedulersTarget;
    }

    @PostMapping("/callschedulers/restore-recurring-event")
    public String restoreRecurringEvent(
            @RequestParam("company-id") long companyId,
            @RequestParam("ical-uid") String icalUid,
            @RequestHeader(value = "X-CallSchedulers-Target", required = false, defaultValue = "local") Mode target) {
        callSchedulersTarget.client(target).post()
                .uri(uriBuilder -> uriBuilder.path(PATH)
                        .queryParam("company-id", companyId)
                        .queryParam("ical-uid", icalUid)
                        .build())
                .retrieve()
                .toBodilessEntity();
        return "Done — restored recurring series ical-uid=" + icalUid + " for companyId=" + companyId;
    }
}
