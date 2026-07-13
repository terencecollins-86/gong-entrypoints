package io.gong.gongentrypoints.callschedulers.restorecall;

import io.gong.gongentrypoints.callschedulers.CallSchedulersTarget;
import io.gong.gongentrypoints.callschedulers.CallSchedulersTarget.Mode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trigger for <b>restore a cancelled call by owner</b>.
 *
 * <p>Restores a previously cancelled scheduled call recording identified by callId. The restore
 * path re-enables the call in the scheduled recordings queue. Use this in combination with
 * {@code cancel-call} to test the cancel → restore round-trip.
 *
 * <p>Pass {@code X-CallSchedulers-Target: hybrid} to hit the hybrid env instead of localhost.
 *
 * <p>Downstream call:
 * {@code POST /scheduledCallsActions/restoreCancelledCallByOwner?callId={callId}&companyId={companyId}}
 * on {@code CallScheduler} ({@code ScheduledCallsActionsController.restoreCancelledCallByOwner()}).
 */
@RestController
public class RestoreCallTrigger {

    private static final String RESTORE_CALL_PATH =
            "/scheduledCallsActions/restoreCancelledCallByOwner";

    private final CallSchedulersTarget callSchedulersTarget;

    public RestoreCallTrigger(CallSchedulersTarget callSchedulersTarget) {
        this.callSchedulersTarget = callSchedulersTarget;
    }

    @PostMapping("/callschedulers/restore-call")
    public String restoreCall(
            @RequestParam("call-id") long callId,
            @RequestParam("company-id") long companyId,
            @RequestHeader(value = "X-CallSchedulers-Target", required = false, defaultValue = "local") Mode target) {
        callSchedulersTarget.client(target).post()
                .uri(uriBuilder -> uriBuilder.path(RESTORE_CALL_PATH)
                        .queryParam("callId", callId)
                        .queryParam("companyId", companyId)
                        .build())
                .retrieve()
                .toBodilessEntity();
        return "Done — restored callId=" + callId + " for companyId=" + companyId;
    }
}
