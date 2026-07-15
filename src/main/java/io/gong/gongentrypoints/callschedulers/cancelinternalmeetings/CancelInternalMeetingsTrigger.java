package io.gong.gongentrypoints.callschedulers.cancelinternalmeetings;

import io.gong.gongentrypoints.callschedulers.CallSchedulersTarget;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trigger for <b>cancel all internal meeting recordings for a company</b>.
 *
 * <p>Cancels all scheduled call recordings classified as internal meetings (i.e. calls where all
 * participants are from the same company). Used when a company disables internal meeting
 * recording and needs existing scheduled internal calls purged. Exercises
 * {@code CancelCallService.cancelScheduledInternalMeetingsCallsRecordings()}.
 *
 * <p>Downstream call:
 * {@code POST /scheduledCallsActions/cancelScheduledInternalMeetingsCallsRecordings?companyId={companyId}}
 * on {@code CallScheduler} ({@code ScheduledCallsActionsController.cancelScheduledInternalMeetingsCallsRecordings()}).
 */
@RestController
public class CancelInternalMeetingsTrigger {

    private static final String CANCEL_INTERNAL_PATH =
            "/scheduledCallsActions/cancelScheduledInternalMeetingsCallsRecordings";

    private final CallSchedulersTarget callSchedulersTarget;

    public CancelInternalMeetingsTrigger(CallSchedulersTarget callSchedulersTarget) {
        this.callSchedulersTarget = callSchedulersTarget;
    }

    @PostMapping("/callschedulers/cancel-internal-meetings")
    public String cancelInternalMeetings(
            @RequestParam("company-id") long companyId) {
        callSchedulersTarget.client().post()
                .uri(uriBuilder -> uriBuilder.path(CANCEL_INTERNAL_PATH)
                        .queryParam("companyId", companyId)
                        .build())
                .retrieve()
                .toBodilessEntity();
        return "Done — cancelled internal meeting recordings for companyId=" + companyId;
    }
}
