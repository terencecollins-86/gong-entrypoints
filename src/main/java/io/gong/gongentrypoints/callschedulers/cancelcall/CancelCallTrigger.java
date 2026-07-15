package io.gong.gongentrypoints.callschedulers.cancelcall;

import io.gong.gongentrypoints.callschedulers.CallSchedulersTarget;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trigger for <b>cancel a scheduled call by owner</b>.
 *
 * <p>Cancels a specific scheduled call recording identified by callId. The owner-cancel path
 * marks the call as cancelled and removes it from the scheduled recordings queue. Use this
 * to test the cancellation flow without waiting for a calendar delete event.
 *
 * <p>Downstream call:
 * {@code POST /scheduledCallsActions/cancelScheduledCallByOwner?callId={callId}&companyId={companyId}}
 * on {@code CallScheduler} ({@code ScheduledCallsActionsController.cancelScheduledCallByOwner()}).
 */
@RestController
public class CancelCallTrigger {

    private static final String CANCEL_CALL_PATH =
            "/scheduledCallsActions/cancelScheduledCallByOwner";

    private final CallSchedulersTarget callSchedulersTarget;

    public CancelCallTrigger(CallSchedulersTarget callSchedulersTarget) {
        this.callSchedulersTarget = callSchedulersTarget;
    }

    @PostMapping("/callschedulers/cancel-call")
    public String cancelCall(
            @RequestParam("call-id") long callId,
            @RequestParam("company-id") long companyId) {
        callSchedulersTarget.client().post()
                .uri(uriBuilder -> uriBuilder.path(CANCEL_CALL_PATH)
                        .queryParam("callId", callId)
                        .queryParam("companyId", companyId)
                        .build())
                .retrieve()
                .toBodilessEntity();
        return "Done — cancelled callId=" + callId + " for companyId=" + companyId;
    }
}
