package io.gong.gongentrypoints.callschedulers.cancelrecurringevent;

import io.gong.gongentrypoints.callschedulers.CallSchedulersTarget;
import io.gong.gongentrypoints.callschedulers.CallSchedulersTarget.Mode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trigger for <b>manually cancel a recurring event series</b>.
 *
 * <p>Marks the recurring rule as cancelled in {@code calendar_recurring_event} and cancels all
 * associated upcoming scheduled calls. Use in combination with {@code restore-recurring-event}
 * to test the recurring-series cancel → restore round-trip. Parallel to {@code cancel-call} for
 * one-time events.
 *
 * <p>Pass {@code X-CallSchedulers-Target: hybrid} to hit the hybrid env instead of localhost.
 *
 * <p>Downstream call:
 * {@code POST /troubleshooting/recurring-meetings/manually-cancel-recurring-event}
 * on {@code CallScheduler} ({@code TroubleshootingRecurringEmailScheduling.manuallyCancelRecurringEvent()}).
 */
@RestController
public class CancelRecurringEventTrigger {

    private static final String PATH =
            "/troubleshooting/recurring-meetings/manually-cancel-recurring-event";

    private final CallSchedulersTarget callSchedulersTarget;

    public CancelRecurringEventTrigger(CallSchedulersTarget callSchedulersTarget) {
        this.callSchedulersTarget = callSchedulersTarget;
    }

    @PostMapping("/callschedulers/cancel-recurring-event")
    public String cancelRecurringEvent(
            @RequestParam("company-id") long companyId,
            @RequestParam("ical-uid") String icalUid,
            @RequestHeader(value = "X-CallSchedulers-Target", required = false, defaultValue = "local") Mode target) {
        return callSchedulersTarget.client(target).post()
                .uri(uriBuilder -> uriBuilder.path(PATH)
                        .queryParam("company-id", companyId)
                        .queryParam("ical-uid", icalUid)
                        .build())
                .retrieve()
                .body(String.class);
    }
}
